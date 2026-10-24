package dev.giack.teslawear.protocol

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Symmetric primitives of the Vehicle Command Protocol (protocol.md "Key Agreement" onward). */
object SessionCrypto {
    private const val TAG_BYTES = 16

    /** K = SHA1(Sx)[:16]. */
    fun deriveKey(sharedX: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-1").digest(sharedX).copyOf(16)

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)

    /** HMAC-SHA256(K, label), e.g. "session info" or "authenticated command". */
    fun subkey(k: ByteArray, label: String): ByteArray = hmacSha256(k, label.toByteArray())

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    fun aesGcmSeal(k: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): Pair<ByteArray, ByteArray> {
        val sealed = gcm(Cipher.ENCRYPT_MODE, k, nonce, aad).doFinal(plaintext)
        return sealed.copyOfRange(0, sealed.size - TAG_BYTES) to sealed.copyOfRange(sealed.size - TAG_BYTES, sealed.size)
    }

    fun aesGcmOpen(k: ByteArray, nonce: ByteArray, ciphertext: ByteArray, tag: ByteArray, aad: ByteArray): ByteArray =
        gcm(Cipher.DECRYPT_MODE, k, nonce, aad).doFinal(ciphertext + tag)

    private fun gcm(mode: Int, k: ByteArray, nonce: ByteArray, aad: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(k, "AES"), GCMParameterSpec(TAG_BYTES * 8, nonce))
            updateAAD(aad)
        }
}
