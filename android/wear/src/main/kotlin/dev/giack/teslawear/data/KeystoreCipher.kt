package dev.giack.teslawear.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM with a non-exportable AndroidKeyStore key; output is IV (12 bytes) || ciphertext. */
class KeystoreCipher(private val alias: String) {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return cipher.iv + cipher.doFinal(plaintext)
    }

    fun decrypt(blob: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, 12))
        return cipher.doFinal(blob, 12, blob.size - 12)
    }
}

/** A small JSON object persisted encrypted in the app's private files dir. */
class EncryptedJsonFile(private val file: java.io.File, private val cipher: KeystoreCipher) {
    @Synchronized
    fun read(): kotlinx.serialization.json.JsonObject? = runCatching {
        if (!file.exists()) return null
        kotlinx.serialization.json.Json.parseToJsonElement(cipher.decrypt(file.readBytes()).decodeToString()) as
            kotlinx.serialization.json.JsonObject
    }.getOrNull()

    @Synchronized
    fun write(json: kotlinx.serialization.json.JsonObject) {
        val tmp = java.io.File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(cipher.encrypt(json.toString().encodeToByteArray()))
        tmp.renameTo(file)
    }

    @Synchronized
    fun delete() { file.delete() }
}
