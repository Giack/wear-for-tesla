package dev.giack.teslawear.protocol

import com.google.protobuf.ByteString
import com.tesla.generated.signatures.Signatures
import com.tesla.generated.universalmessage.UniversalMessage
import com.tesla.generated.universalmessage.UniversalMessage.RoutableMessage

/**
 * Client side of one vehicle domain session (protocol.md "Handshake", "Authorizing commands",
 * "Response handling"). Not thread-safe: callers serialize access per domain.
 */
class Session(
    val vin: String,
    val domain: Domain,
    private val key: EcdhKey,
    private val nowMs: () -> Long = System::currentTimeMillis,
    /** When known (pinned from an authenticated channel), the only vehicle key a handshake may present. */
    private val expectedVehiclePublic: ByteArray? = null,
) {
    var state: SessionState? = null
        private set
    private var sharedKey: ByteArray? = null
    private val seenResponseCounters = mutableMapOf<String, MutableSet<Long>>()

    fun sessionInfoRequest(): SignedRequest {
        val uuid = Messages.randomBytes(16)
        val message = Messages.envelope(domain, uuid)
            .setSessionInfoRequest(
                UniversalMessage.SessionInfoRequest.newBuilder().setPublicKey(ByteString.copyFrom(key.publicBytes)),
            )
            .build()
        return SignedRequest(message, uuid, ByteArray(0))
    }

    fun restore(state: SessionState) {
        require(state.domain == domain) { "session state for another domain" }
        this.state = state
        sharedKey = SessionCrypto.deriveKey(key.sharedSecretX(state.vehiclePublic))
    }

    /** Verifies and applies session info found in a handshake reply or an error reply. */
    fun acceptSessionInfo(response: RoutableMessage, requestUuid: ByteArray) {
        val encoded = response.sessionInfo.toByteArray()
        val info = Signatures.SessionInfo.parseFrom(encoded)
        val vehiclePublic = info.publicKey.toByteArray()
        val current = state
        if (current != null && !current.vehiclePublic.contentEquals(vehiclePublic)) {
            throw SessionException("vehicle public key changed")
        }
        if (expectedVehiclePublic != null && !expectedVehiclePublic.contentEquals(vehiclePublic)) {
            throw SessionException("vehicle public key does not match the pinned key")
        }
        val k = if (current != null) sharedKey!! else SessionCrypto.deriveKey(key.sharedSecretX(vehiclePublic))
        val meta = MetadataBuilder()
            .add(Signatures.Tag.TAG_SIGNATURE_TYPE_VALUE, byteArrayOf(Signatures.SignatureType.SIGNATURE_TYPE_HMAC_VALUE.toByte()))
            .add(Signatures.Tag.TAG_PERSONALIZATION_VALUE, vin.toByteArray())
            .add(Signatures.Tag.TAG_CHALLENGE_VALUE, requestUuid)
            .serialize()
        val expected = SessionCrypto.hmacSha256(SessionCrypto.subkey(k, "session info"), meta + encoded)
        val tag = response.signatureData.sessionInfoTag.tag.toByteArray()
        if (!java.security.MessageDigest.isEqual(expected, tag)) throw SessionException("session info tag invalid")

        val epoch = info.epoch.toByteArray()
        val clock = info.clockTime.toLong() and 0xFFFFFFFFL
        val counter = info.counter.toLong() and 0xFFFFFFFFL
        state = when {
            current == null || !current.epoch.contentEquals(epoch) ->
                SessionState(domain, epoch, counter, clock, nowMs(), vehiclePublic)
            clock < current.clockTime -> current
            else -> SessionState(domain, epoch, maxOf(current.counter, counter), clock, nowMs(), vehiclePublic)
        }
        sharedKey = k
    }

    fun sign(payload: ByteArray, method: AuthMethod, expiresInSec: Int = 15, nonce: ByteArray? = null): SignedRequest {
        val s = state ?: throw SessionException("no session")
        val k = sharedKey!!
        check(s.counter < 0xFFFFFFFFL) { "counter rollover" }
        val counter = s.counter + 1
        state = s.copy(counter = counter)
        val expiresAt = s.clockTime + (nowMs() - s.localTimeAtSyncMs) / 1000 + expiresInSec
        val flags = 1 shl UniversalMessage.Flags.FLAG_ENCRYPT_RESPONSE_VALUE
        val sigType = when (method) {
            AuthMethod.HMAC -> Signatures.SignatureType.SIGNATURE_TYPE_HMAC_PERSONALIZED_VALUE
            AuthMethod.AES_GCM -> Signatures.SignatureType.SIGNATURE_TYPE_AES_GCM_PERSONALIZED_VALUE
        }
        val meta = MetadataBuilder()
            .add(Signatures.Tag.TAG_SIGNATURE_TYPE_VALUE, byteArrayOf(sigType.toByte()))
            .add(Signatures.Tag.TAG_DOMAIN_VALUE, byteArrayOf(domain.wire.toByte()))
            .add(Signatures.Tag.TAG_PERSONALIZATION_VALUE, vin.toByteArray())
            .add(Signatures.Tag.TAG_EPOCH_VALUE, s.epoch)
            .addUInt32(Signatures.Tag.TAG_EXPIRES_AT_VALUE, expiresAt)
            .addUInt32(Signatures.Tag.TAG_COUNTER_VALUE, counter)
            .addUInt32(Signatures.Tag.TAG_FLAGS_VALUE, flags.toLong())
            .serialize()

        val uuid = Messages.randomBytes(16)
        val builder = Messages.envelope(domain, uuid).setFlags(flags)
        val signer = Signatures.KeyIdentity.newBuilder().setPublicKey(ByteString.copyFrom(key.publicBytes))
        val tag: ByteArray
        when (method) {
            AuthMethod.HMAC -> {
                tag = SessionCrypto.hmacSha256(SessionCrypto.subkey(k, "authenticated command"), meta + payload)
                builder.setProtobufMessageAsBytes(ByteString.copyFrom(payload))
                builder.setSignatureData(
                    Signatures.SignatureData.newBuilder().setSignerIdentity(signer).setHMACPersonalizedData(
                        Signatures.HMAC_Personalized_Signature_Data.newBuilder()
                            .setEpoch(ByteString.copyFrom(s.epoch)).setCounter(counter.toInt())
                            .setExpiresAt(expiresAt.toInt()).setTag(ByteString.copyFrom(tag)),
                    ),
                )
            }
            AuthMethod.AES_GCM -> {
                val iv = nonce ?: Messages.randomBytes(12)
                val (ciphertext, gcmTag) = SessionCrypto.aesGcmSeal(k, iv, payload, SessionCrypto.sha256(meta))
                tag = gcmTag
                builder.setProtobufMessageAsBytes(ByteString.copyFrom(ciphertext))
                builder.setSignatureData(
                    Signatures.SignatureData.newBuilder().setSignerIdentity(signer).setAESGCMPersonalizedData(
                        Signatures.AES_GCM_Personalized_Signature_Data.newBuilder()
                            .setEpoch(ByteString.copyFrom(s.epoch)).setNonce(ByteString.copyFrom(iv))
                            .setCounter(counter.toInt()).setExpiresAt(expiresAt.toInt()).setTag(ByteString.copyFrom(tag)),
                    ),
                )
            }
        }
        val hashTag = if (method == AuthMethod.HMAC && domain == Domain.VCSEC) tag.copyOf(16) else tag
        return SignedRequest(builder.build(), uuid, byteArrayOf(sigType.toByte()) + hashTag)
    }

    /**
     * Decrypts and authenticates the reply to a signed request. Requests always set
     * FLAG_ENCRYPT_RESPONSE and the target vehicle runs firmware newer than 2024.38, so an
     * unencrypted reply is rejected rather than trusted.
     */
    fun openResponse(response: RoutableMessage, requestHash: ByteArray): ByteArray {
        if (!response.signatureData.hasAESGCMResponseData()) throw SessionException("unauthenticated response")
        val k = sharedKey ?: throw SessionException("no session")
        val gcm = response.signatureData.aesgcmResponseData
        val counter = gcm.counter.toLong() and 0xFFFFFFFFL
        val seen = seenResponseCounters.getOrPut(requestHash.joinToString("") { "%02x".format(it) }) { mutableSetOf() }
        if (counter in seen) throw SessionException("replayed response counter")
        val meta = MetadataBuilder()
            .add(Signatures.Tag.TAG_SIGNATURE_TYPE_VALUE, byteArrayOf(Signatures.SignatureType.SIGNATURE_TYPE_AES_GCM_RESPONSE_VALUE.toByte()))
            .add(Signatures.Tag.TAG_DOMAIN_VALUE, byteArrayOf(domain.wire.toByte()))
            .add(Signatures.Tag.TAG_PERSONALIZATION_VALUE, vin.toByteArray())
            .addUInt32(Signatures.Tag.TAG_COUNTER_VALUE, counter)
            .addUInt32(Signatures.Tag.TAG_FLAGS_VALUE, response.flags.toLong() and 0xFFFFFFFFL)
            .add(Signatures.Tag.TAG_REQUEST_HASH_VALUE, requestHash)
            .addUInt32(Signatures.Tag.TAG_FAULT_VALUE, response.signedMessageStatus.signedMessageFaultValue.toLong())
            .serialize()
        val plaintext = try {
            SessionCrypto.aesGcmOpen(k, gcm.nonce.toByteArray(), response.protobufMessageAsBytes.toByteArray(),
                gcm.tag.toByteArray(), SessionCrypto.sha256(meta))
        } catch (e: javax.crypto.AEADBadTagException) {
            throw SessionException("response authentication failed")
        }
        seen += counter
        return plaintext
    }
}
