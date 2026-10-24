package dev.giack.teslawear.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import com.tesla.generated.universalmessage.UniversalMessage.RoutableMessage
import dev.giack.teslawear.diag.Diag
import dev.giack.teslawear.protocol.AuthMethod
import dev.giack.teslawear.protocol.BleFraming
import dev.giack.teslawear.protocol.Transport
import dev.giack.teslawear.protocol.TransportTimeoutException
import dev.giack.teslawear.protocol.TransportUnavailableException
import dev.giack.teslawear.protocol.isReplyTo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/** GATT link to the car: write-with-response chunks out, indications/notifications in. */
@SuppressLint("MissingPermission")
class BleTransport(private val context: Context, private val deviceProvider: suspend () -> BluetoothDevice?) : Transport {
    override val authMethod = AuthMethod.AES_GCM

    private val mutex = Mutex()
    private var gatt: BluetoothGatt? = null
    private var txChar: BluetoothGattCharacteristic? = null
    private var blockLen = 20
    private var ready: CompletableDeferred<Unit>? = null
    private var writeDone: CompletableDeferred<Int>? = null
    private val reassembler = BleFraming.Reassembler()
    private val incoming = Channel<RoutableMessage>(Channel.UNLIMITED)
    @Volatile var lastUseMs = 0L
        private set

    val isConnected: Boolean get() = gatt != null && ready?.isCompleted == true

    override suspend fun send(message: RoutableMessage, timeoutMs: Long): RoutableMessage = mutex.withLock {
        lastUseMs = System.currentTimeMillis()
        ensureConnected()
        while (incoming.tryReceive().isSuccess) Unit // drop stale replies
        val framed = BleFraming.frame(message.toByteArray())
        val chunks = BleFraming.chunks(framed, blockLen)
        Diag.i("BLE send ${message.toDestination.domain}: ${framed.size} B in ${chunks.size} chunks")
        chunks.forEachIndexed { i, chunk ->
            try {
                write(chunk)
            } catch (e: TransportUnavailableException) {
                // Once the last chunk was handed to GATT the car may have the whole command:
                // report "unknown" rather than letting the router resend it over the cloud.
                if (i == chunks.lastIndex) throw TransportTimeoutException("lost link after sending") else throw e
            }
        }
        awaitReply(message, timeoutMs) ?: run {
            Diag.w("BLE no reply within $timeoutMs ms")
            throw TransportTimeoutException()
        }
    }

    override suspend fun sendRaw(bytes: ByteArray) = mutex.withLock {
        lastUseMs = System.currentTimeMillis()
        ensureConnected()
        val chunks = BleFraming.chunks(BleFraming.frame(bytes), blockLen)
        Diag.i("BLE send raw: ${bytes.size} B in ${chunks.size} chunks")
        chunks.forEach { write(it) }
    }

    /** Over BLE the final VCSEC status always arrives on this link: silence means the outcome is unknown. */
    override suspend fun next(request: RoutableMessage, timeoutMs: Long): RoutableMessage? =
        mutex.withLock { awaitReply(request, timeoutMs) ?: throw TransportTimeoutException() }

    /** Disconnects only if nothing used the link for [idleMs]; serialized with sends. */
    suspend fun closeIfIdle(idleMs: Long) = mutex.withLock {
        if (System.currentTimeMillis() - lastUseMs >= idleMs) close()
    }

    private suspend fun awaitReply(request: RoutableMessage, timeoutMs: Long): RoutableMessage? = withTimeoutOrNull(timeoutMs) {
        var msg = incoming.receive()
        while (!msg.isReplyTo(request)) {
            Diag.i("BLE ignored message for ${describe(msg.toDestination)}")
            msg = incoming.receive()
        }
        msg
    }

    private fun describe(d: com.tesla.generated.universalmessage.UniversalMessage.Destination) =
        if (d.hasRoutingAddress()) "another request" else "domain ${d.domainValue}"

    private suspend fun write(chunk: ByteArray) {
        val g = gatt ?: throw TransportUnavailableException("not connected")
        val done = CompletableDeferred<Int>().also { writeDone = it }
        val status = g.writeCharacteristic(txChar!!, chunk, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        if (status != BluetoothStatusCodes.SUCCESS) throw TransportUnavailableException("write rejected: $status")
        val result = withTimeoutOrNull(WRITE_TIMEOUT_MS) { done.await() } ?: throw TransportUnavailableException("write timeout")
        if (result != BluetoothGatt.GATT_SUCCESS) throw TransportUnavailableException("write failed: $result")
    }

    private suspend fun ensureConnected() {
        if (isConnected) return
        val device = deviceProvider() ?: throw TransportUnavailableException("car not found")
        Diag.i("BLE connecting")
        val deferred = CompletableDeferred<Unit>().also { ready = it }
        reassembler.reset()
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        try {
            withTimeout(CONNECT_TIMEOUT_MS) { deferred.await() }
            Diag.i("BLE ready (block $blockLen B)")
        } catch (e: Exception) {
            Diag.w("BLE connect failed: $e")
            close()
            throw TransportUnavailableException("connect failed", e)
        }
    }

    fun close() {
        if (gatt != null) Diag.i("BLE close")
        gatt?.let { runCatching { it.disconnect(); it.close() } }
        gatt = null
        txChar = null
        ready = null
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Diag.i("BLE connection state $newState (status $status)")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    ready?.completeExceptionally(TransportUnavailableException("disconnected"))
                    writeDone?.complete(-1)
                    if (gatt === g) { gatt = null; txChar = null; ready = null }
                    g.close()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(UUID.fromString(BleFraming.SERVICE_UUID))
            txChar = service?.getCharacteristic(UUID.fromString(BleFraming.TO_VEHICLE_UUID))
            Diag.i("BLE services (status $status): tesla=${service != null}, tx=${txChar != null}")
            if (service == null || txChar == null) {
                ready?.completeExceptionally(TransportUnavailableException("Tesla service missing"))
                return
            }
            if (!g.requestMtu(MTU)) subscribe(g)
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            Diag.i("BLE mtu $mtu (status $status)")
            if (status == BluetoothGatt.GATT_SUCCESS) blockLen = minOf(mtu, BleFraming.MAX_MESSAGE) - 3
            subscribe(g)
        }

        private fun subscribe(g: BluetoothGatt) {
            val rx = g.getService(UUID.fromString(BleFraming.SERVICE_UUID))
                .getCharacteristic(UUID.fromString(BleFraming.FROM_VEHICLE_UUID))
            g.setCharacteristicNotification(rx, true)
            val value = if (rx.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            }
            g.writeDescriptor(rx.getDescriptor(CCCD), value)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Diag.i("BLE subscribe status $status")
            if (status == BluetoothGatt.GATT_SUCCESS) ready?.complete(Unit)
            else ready?.completeExceptionally(TransportUnavailableException("subscribe failed: $status"))
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) Diag.w("BLE write status $status")
            writeDone?.complete(status)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            val message = try {
                reassembler.push(value)
            } catch (_: IllegalStateException) {
                null
            } ?: return
            val parsed = runCatching { RoutableMessage.parseFrom(message) }.getOrNull()
            Diag.i("BLE reply ${message.size} B, parsed=${parsed != null}")
            parsed?.let { incoming.trySend(it) }
        }
    }

    private companion object {
        const val MTU = 512
        const val CONNECT_TIMEOUT_MS = 8_000L
        const val WRITE_TIMEOUT_MS = 3_000L
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
