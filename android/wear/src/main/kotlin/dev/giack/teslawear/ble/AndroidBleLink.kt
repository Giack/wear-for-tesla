package dev.giack.teslawear.ble

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.tesla.generated.carserver.server.CarServer
import com.tesla.generated.vcsec.Vcsec
import dev.giack.teslawear.core.BleLink
import dev.giack.teslawear.core.Snapshot
import dev.giack.teslawear.diag.Diag
import dev.giack.teslawear.protocol.BleFraming
import dev.giack.teslawear.protocol.CommandExecutor
import dev.giack.teslawear.protocol.CommandResult
import dev.giack.teslawear.protocol.CommandRunner
import dev.giack.teslawear.protocol.Domain
import dev.giack.teslawear.protocol.EcdhKey
import dev.giack.teslawear.protocol.Enrollment
import dev.giack.teslawear.protocol.KeyEnrollment
import dev.giack.teslawear.protocol.SessionCache
import dev.giack.teslawear.protocol.TransportUnavailableException
import dev.giack.teslawear.protocol.VehicleKeyPins
import dev.giack.teslawear.protocol.VehicleSleepProbe
import dev.giack.teslawear.protocol.WakingRunner
import dev.giack.teslawear.protocol.VehicleCommand
import dev.giack.teslawear.protocol.VehicleReads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Bluetooth path: scan by local name, GATT transport, idle disconnect after 10 s. */
class AndroidBleLink(
    private val context: Context,
    vin: String,
    private val key: EcdhKey,
    cache: SessionCache,
    pins: VehicleKeyPins,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : BleLink {
    private val localName = BleFraming.vehicleLocalName(vin)
    private val scanner = BleScanner(context)
    private var lastDevice: BluetoothDevice? = null
    private var lastSeenMs = 0L
    private val transport = BleTransport(context) { lastDevice ?: scanner.find(localName, SCAN_MS)?.also(::seen) }
    // BLE handshakes are checked against vehicle keys pinned through Fleet API (when known).
    private val executor = CommandExecutor(vin, key, transport, cache, timeoutMs = 10_000, pins = pins, trustedPins = false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var idleJob: Job? = null

    /** Why the last BLE status read failed; shown by the setup diagnostics. */
    @Volatile var lastError: String? = null
        private set

    private fun seen(device: BluetoothDevice) {
        lastDevice = device
        lastSeenMs = nowMs()
        knownCar(context).edit().putString(KEY_ADDRESS, device.address).apply()
    }

    private fun permitted(): Boolean = scanner.permissionsOk()

    /** Setup diagnostics: what a 6 s scan sees. The car it finds is reused, so no second scan follows. */
    suspend fun diagnose(): ScanReport {
        val (device, report) = scanner.diagnose(localName, 6_000)
        device?.let(::seen)
        return report
    }

    /** Asks the car to add this link's BLE key; the user then taps the Keycard. */
    suspend fun requestKey() = KeyEnrollment.requestAddKey(transport, key.publicBytes)

    /** Whether the car now accepts this link's BLE key. */
    suspend fun verifyKey(): Enrollment =
        KeyEnrollment.verify(executor) { Diag.w("BLE key check: ${describe(it)}") }.also { Diag.i("BLE key: $it") }

    override suspend fun isReachable(timeoutMs: Long): Boolean {
        if (!permitted()) return false
        if (transport.isConnected || nowMs() - lastSeenMs < SEEN_GRACE_MS) {
            Diag.i("BLE reachable without scan (connected=${transport.isConnected})")
            return true
        }
        return scanner.find(localName, timeoutMs)?.also(::seen) != null
    }

    private val probe = object : VehicleSleepProbe {
        override suspend fun isAsleep(): Boolean? = vcsecStatus()?.let(BleStatusMapper::isAsleep)
        override suspend fun execute(cmd: VehicleCommand): CommandResult = executor.execute(cmd)
    }

    // Infotainment sleeps while VCSEC listens: wake it over BLE before infotainment commands.
    private val waking = WakingRunner(probe)

    override val commands: CommandRunner = object : CommandRunner {
        override suspend fun execute(cmd: VehicleCommand): CommandResult {
            idleJob?.cancel()
            return try {
                waking.execute(cmd).also { scheduleDisconnect() }
            } catch (e: TransportUnavailableException) {
                forgetDevice()
                throw e
            }
        }
    }

    /** Tile and background read: no scan (Android rejects it outside the foreground); a direct connection to the remembered address. */
    @android.annotation.SuppressLint("MissingPermission")
    override suspend fun readKnownCarStatus(): Snapshot? {
        if (context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) return null
        val address = knownCar(context).getString(KEY_ADDRESS, null) ?: return null.also { Diag.i("BLE direct: no car seen yet (open the app near the car once)") }
        val adapter = context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter?.takeIf { it.isEnabled } ?: return null
        if (!transport.isConnected) lastDevice = adapter.getRemoteDevice(address)
        return readStatus()
    }

    override suspend fun readStatus(): Snapshot? {
        idleJob?.cancel()
        val status = vcsecStatus() ?: return null
        var snapshot = BleStatusMapper.fromVcsec(status, nowMs())
        if (!BleStatusMapper.isAsleep(status)) {
            for (request in VehicleReads.carServerDataRequests()) {
                runCatching {
                    val reply = executor.query(Domain.INFOTAINMENT, request)
                    BleStatusMapper.fromCarServer(CarServer.Response.parseFrom(reply), nowMs())
                }.onFailure { Diag.w("BLE infotainment read failed", it) }.getOrNull()?.let { snapshot = snapshot.mergedWith(it) }
            }
        }
        scheduleDisconnect()
        return snapshot
    }

    /**
     * Authenticated VCSEC status read. No unsigned fallback: fault replies are themselves
     * unauthenticated, so they must not be able to downgrade the read.
     */
    private suspend fun vcsecStatus(): Vcsec.VehicleStatus? {
        lastError = null
        val bytes = try {
            executor.query(Domain.VCSEC, VehicleReads.vcsecStatusRequest(), authenticated = true)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Diag.w("BLE VCSEC status failed", e)
            lastError = describe(e)
            forgetDevice()
            return null
        }
        val message = runCatching { Vcsec.FromVCSECMessage.parseFrom(bytes) }.getOrNull()
        if (message?.hasVehicleStatus() != true) {
            lastError = if (message == null) "unreadable VCSEC reply" else "VCSEC reply without status"
            Diag.w("BLE $lastError")
            return null
        }
        return message.vehicleStatus
    }

    private fun forgetDevice() {
        lastDevice = null
        lastSeenMs = 0
        transport.close()
    }

    private fun scheduleDisconnect() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(IDLE_DISCONNECT_MS)
            transport.closeIfIdle(IDLE_DISCONNECT_MS)
        }
    }

    companion object {
        private const val KEY_ADDRESS = "address"
        private fun knownCar(context: Context) = context.getSharedPreferences("ble_known_car", Context.MODE_PRIVATE)

        /** Forgets the car's Bluetooth address (car removed). */
        fun forgetKnownCar(context: Context) = knownCar(context).edit().clear().apply()

        private fun describe(e: Throwable): String =
            generateSequence(e) { it.cause }.take(3).joinToString(" ← ") { "${it.javaClass.simpleName}: ${it.message}" }

        private const val SCAN_MS = 3_000L
        private const val SEEN_GRACE_MS = 30_000L
        private const val IDLE_DISCONNECT_MS = 10_000L
    }
}
