package dev.giack.teslawear.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dev.giack.teslawear.protocol.EcdhKey
import dev.giack.teslawear.protocol.JvmEcdhKey
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import javax.crypto.KeyAgreement

/** BLE key created on the watch: the private key never leaves the Android Keystore. */
class KeystoreEcdhKey(private val alias: String = "ble_key") : EcdhKey {
    private val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    init {
        if (!keyStore.containsAlias(alias)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, PROVIDER).apply {
                initialize(
                    KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_AGREE_KEY)
                        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        .build(),
                )
            }.generateKeyPair()
        }
    }

    override val publicBytes: ByteArray = JvmEcdhKey.encodePublic(keyStore.getCertificate(alias).publicKey as ECPublicKey)

    override fun sharedSecretX(peerPublic: ByteArray): ByteArray = KeyAgreement.getInstance("ECDH", PROVIDER).run {
        init(keyStore.getKey(alias, null) as PrivateKey)
        doPhase(JvmEcdhKey.decodePublic(peerPublic), true)
        generateSecret()
    }

    companion object {
        private const val PROVIDER = "AndroidKeyStore"

        /** Deletes the watch's BLE key; the next KeystoreEcdhKey creates a new one. */
        fun delete(alias: String = "ble_key") {
            KeyStore.getInstance(PROVIDER).apply { load(null) }.takeIf { it.containsAlias(alias) }?.deleteEntry(alias)
        }
    }
}
