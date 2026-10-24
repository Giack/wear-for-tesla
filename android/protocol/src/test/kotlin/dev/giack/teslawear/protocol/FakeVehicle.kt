package dev.giack.teslawear.protocol

import com.google.protobuf.ByteString
import com.tesla.generated.signatures.Signatures
import com.tesla.generated.universalmessage.UniversalMessage
import com.tesla.generated.universalmessage.UniversalMessage.RoutableMessage

/** Plays the vehicle side of the protocol for tests, using the published test vehicle key. */
class FakeVehicle(
    val vin: String = DocVectors.VIN,
    val key: JvmEcdhKey = JvmEcdhKey.fromPem(DocVectors.VEHICLE_PEM),
) {
    var epoch: ByteArray = DocVectors.epoch
    var counter: Long = 6
    var clock: Long = 2650

    fun k(clientPublic: ByteArray): ByteArray = SessionCrypto.deriveKey(key.sharedSecretX(clientPublic))

    fun sessionInfoBytes(): ByteArray = Signatures.SessionInfo.newBuilder()
        .setCounter(counter.toInt())
        .setPublicKey(ByteString.copyFrom(key.publicBytes))
        .setEpoch(ByteString.copyFrom(epoch))
        .setClockTime(clock.toInt())
        .build().toByteArray()

    fun sessionInfoTag(clientPublic: ByteArray, challenge: ByteArray, info: ByteArray): ByteArray {
        val meta = MetadataBuilder().add(0, byteArrayOf(6)).add(2, vin.toByteArray()).add(6, challenge).serialize()
        return SessionCrypto.hmacSha256(SessionCrypto.subkey(k(clientPublic), "session info"), meta + info)
    }

    fun sessionInfoResponse(clientPublic: ByteArray, requestUuid: ByteArray, domain: Domain): RoutableMessage {
        val info = sessionInfoBytes()
        return RoutableMessage.newBuilder()
            .setFromDestination(UniversalMessage.Destination.newBuilder().setDomainValue(domain.wire))
            .setSessionInfo(ByteString.copyFrom(info))
            .setRequestUuid(ByteString.copyFrom(requestUuid))
            .setSignatureData(Signatures.SignatureData.newBuilder().setSessionInfoTag(
                Signatures.HMAC_Signature_Data.newBuilder().setTag(ByteString.copyFrom(sessionInfoTag(clientPublic, requestUuid, info)))))
            .build()
    }

    /** Builds an AES-GCM encrypted reply exactly as protocol.md "Response decryption" describes. */
    fun encryptedResponse(
        clientPublic: ByteArray, domain: Domain, requestHash: ByteArray, requestUuid: ByteArray,
        plaintext: ByteArray, responseCounter: Long, flags: Int = 0,
    ): RoutableMessage {
        val meta = MetadataBuilder().add(0, byteArrayOf(9)).add(1, byteArrayOf(domain.wire.toByte()))
            .add(2, vin.toByteArray()).addUInt32(5, responseCounter).addUInt32(7, flags.toLong())
            .add(8, requestHash).addUInt32(9, 0).serialize()
        val nonce = ByteArray(12) { it.toByte() }
        val (ct, tag) = SessionCrypto.aesGcmSeal(k(clientPublic), nonce, plaintext, SessionCrypto.sha256(meta))
        return RoutableMessage.newBuilder()
            .setFromDestination(UniversalMessage.Destination.newBuilder().setDomainValue(domain.wire))
            .setProtobufMessageAsBytes(ByteString.copyFrom(ct))
            .setRequestUuid(ByteString.copyFrom(requestUuid))
            .setFlags(flags)
            .setSignatureData(Signatures.SignatureData.newBuilder().setAESGCMResponseData(
                Signatures.AES_GCM_Response_Signature_Data.newBuilder()
                    .setNonce(ByteString.copyFrom(nonce)).setCounter(responseCounter.toInt()).setTag(ByteString.copyFrom(tag))))
            .build()
    }
}

/** Mirror of Go's authentication.RequestID: signature type byte + tag (VCSEC HMAC tags truncated to 16 bytes). */
fun requestHashOf(message: RoutableMessage): ByteArray {
    val sig = message.signatureData
    return when {
        sig.hasAESGCMPersonalizedData() -> byteArrayOf(5) + sig.aesgcmPersonalizedData.tag.toByteArray()
        sig.hasHMACPersonalizedData() -> {
            val tag = sig.hmacPersonalizedData.tag.toByteArray()
            byteArrayOf(8) + if (message.toDestination.domainValue == Domain.VCSEC.wire) tag.copyOf(16) else tag
        }
        else -> ByteArray(0)
    }
}
