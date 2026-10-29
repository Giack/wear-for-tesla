package dev.giack.teslawear.protocol

import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** BLE transport framing (protocol.md "BLE"): 2-byte big-endian length prefix, MTU-sized chunks. */
object BleFraming {
    const val SERVICE_UUID = "00000211-b2d1-43f0-9b88-960cebf8b91e"
    const val TO_VEHICLE_UUID = "00000212-b2d1-43f0-9b88-960cebf8b91e"
    const val FROM_VEHICLE_UUID = "00000213-b2d1-43f0-9b88-960cebf8b91e"
    const val MAX_MESSAGE = 1024
    const val RX_GAP_MS = 1_000L

    /** "S" + hex(SHA1(VIN)[:8]) + "C". */
    fun vehicleLocalName(vin: String): String =
        "S" + MessageDigest.getInstance("SHA-1").digest(vin.toByteArray()).copyOf(8).joinToString("") { "%02x".format(it) } + "C"

    fun frame(msg: ByteArray): ByteArray {
        require(msg.size <= MAX_MESSAGE) { "message too large for BLE" }
        return byteArrayOf((msg.size ushr 8).toByte(), msg.size.toByte()) + msg
    }

    fun chunks(framed: ByteArray, blockLen: Int): List<ByteArray> =
        (framed.indices step blockLen).map { framed.copyOfRange(it, minOf(it + blockLen, framed.size)) }

    /** Joins notifications into messages; a gap over [RX_GAP_MS] drops a partial one (as Tesla's Go connector). */
    class Reassembler(private val maxLen: Int = MAX_MESSAGE, private val nowMs: () -> Long = System::currentTimeMillis) {
        private val buffer = ByteArrayOutputStream()
        private var lastChunkMs = 0L

        /** Feeds one notification; returns a complete message once all of it has arrived. */
        fun push(chunk: ByteArray): ByteArray? {
            val now = nowMs()
            if (now - lastChunkMs > RX_GAP_MS) buffer.reset()
            lastChunkMs = now
            buffer.write(chunk)
            val bytes = buffer.toByteArray()
            if (bytes.size < 2) return null
            val length = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
            if (length > maxLen) {
                reset()
                throw IllegalStateException("BLE message length $length exceeds $maxLen")
            }
            if (bytes.size < 2 + length) return null
            reset()
            return bytes.copyOfRange(2, 2 + length)
        }

        fun reset() = buffer.reset()
    }
}
