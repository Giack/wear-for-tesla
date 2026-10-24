package dev.giack.teslawear.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

enum class Domain(val wire: Int) {
    VCSEC(2),
    INFOTAINMENT(3),
    ;

    companion object {
        fun fromWire(wire: Int): Domain = entries.first { it.wire == wire }
    }
}

enum class AuthMethod { HMAC, AES_GCM }

class SessionException(message: String) : Exception(message)

/** Per-domain session state; persisted so a restart does not need a new handshake. */
class SessionState(
    val domain: Domain,
    val epoch: ByteArray,
    val counter: Long,
    val clockTime: Long,
    val localTimeAtSyncMs: Long,
    val vehiclePublic: ByteArray,
) {
    fun copy(counter: Long = this.counter): SessionState =
        SessionState(domain, epoch, counter, clockTime, localTimeAtSyncMs, vehiclePublic)

    fun encode(): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeByte(VERSION)
            out.writeByte(domain.wire)
            out.writeByte(epoch.size); out.write(epoch)
            out.writeLong(counter)
            out.writeLong(clockTime)
            out.writeLong(localTimeAtSyncMs)
            out.writeByte(vehiclePublic.size); out.write(vehiclePublic)
        }
        return bytes.toByteArray()
    }

    companion object {
        private const val VERSION = 1

        fun decode(b: ByteArray): SessionState = DataInputStream(ByteArrayInputStream(b)).use { input ->
            require(input.readUnsignedByte() == VERSION) { "unknown session state version" }
            val domain = Domain.fromWire(input.readUnsignedByte())
            val epoch = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }
            val counter = input.readLong()
            val clock = input.readLong()
            val local = input.readLong()
            val pub = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }
            SessionState(domain, epoch, counter, clock, local, pub)
        }
    }
}
