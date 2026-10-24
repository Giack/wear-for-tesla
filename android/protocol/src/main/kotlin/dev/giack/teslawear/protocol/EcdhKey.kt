package dev.giack.teslawear.protocol

import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import javax.crypto.KeyAgreement

/** A P-256 key able to perform ECDH with a vehicle domain. */
interface EcdhKey {
    /** Uncompressed SEC1 encoding (0x04 || X || Y), 65 bytes. */
    val publicBytes: ByteArray

    /** X coordinate of the ECDH shared point, 32 bytes big-endian. */
    fun sharedSecretX(peerPublic: ByteArray): ByteArray
}

/** Software P-256 key backed by the JCA provider. */
class JvmEcdhKey(val privateScalar: ByteArray, override val publicBytes: ByteArray) : EcdhKey {
    private val privateKey: ECPrivateKey = KeyFactory.getInstance("EC")
        .generatePrivate(ECPrivateKeySpec(BigInteger(1, privateScalar), P256)) as ECPrivateKey

    override fun sharedSecretX(peerPublic: ByteArray): ByteArray {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(decodePublic(peerPublic), true)
        return agreement.generateSecret()
    }

    companion object {
        val P256: ECParameterSpec = AlgorithmParameters.getInstance("EC")
            .apply { init(ECGenParameterSpec("secp256r1")) }
            .getParameterSpec(ECParameterSpec::class.java)

        fun generate(): JvmEcdhKey {
            val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
            val scalar = fixed32((pair.private as ECPrivateKey).s)
            return JvmEcdhKey(scalar, encodePublic(pair.public as ECPublicKey))
        }

        /** Parses a SEC1 `EC PRIVATE KEY` PEM (as written by `openssl ecparam -genkey`). */
        fun fromPem(pem: String): JvmEcdhKey {
            val der = Base64.getMimeDecoder().decode(
                pem.lineSequence().filterNot { it.startsWith("-----") }.joinToString(""),
            )
            val reader = Der(der)
            reader.expect(0x30); reader.length()
            reader.expect(0x02); reader.skip(reader.length())
            reader.expect(0x04); val scalar = reader.take(reader.length())
            var public: ByteArray? = null
            while (reader.hasMore()) {
                val tag = reader.byte()
                val len = reader.length()
                if (tag == 0xA1) {
                    reader.expect(0x03); val bitLen = reader.length()
                    reader.byte() // unused-bits count
                    public = reader.take(bitLen - 1)
                } else {
                    reader.skip(len)
                }
            }
            requireNotNull(public) { "PEM has no embedded public key" }
            return JvmEcdhKey(fixed32(BigInteger(1, scalar)), public)
        }

        fun encodePublic(key: ECPublicKey): ByteArray =
            byteArrayOf(0x04) + fixed32(key.w.affineX) + fixed32(key.w.affineY)

        fun decodePublic(bytes: ByteArray): ECPublicKey {
            require(bytes.size == 65 && bytes[0] == 0x04.toByte()) { "expected uncompressed P-256 point" }
            val point = ECPoint(BigInteger(1, bytes.copyOfRange(1, 33)), BigInteger(1, bytes.copyOfRange(33, 65)))
            return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, P256)) as ECPublicKey
        }

        private fun fixed32(n: BigInteger): ByteArray {
            val raw = n.toByteArray()
            return when {
                raw.size == 32 -> raw
                raw.size > 32 -> raw.copyOfRange(raw.size - 32, raw.size)
                else -> ByteArray(32 - raw.size) + raw
            }
        }
    }

    private class Der(private val b: ByteArray) {
        private var i = 0
        fun hasMore() = i < b.size
        fun byte(): Int = b[i++].toInt() and 0xFF
        fun expect(tag: Int) = require(byte() == tag) { "unexpected DER tag" }
        fun skip(n: Int) { i += n }
        fun take(n: Int): ByteArray = b.copyOfRange(i, i + n).also { i += n }
        fun length(): Int {
            val first = byte()
            if (first < 0x80) return first
            var len = 0
            repeat(first and 0x7F) { len = (len shl 8) or byte() }
            return len
        }
    }
}
