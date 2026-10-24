package dev.giack.teslawear.protocol

import com.google.protobuf.ByteString
import com.tesla.generated.keys.Keys
import com.tesla.generated.universalmessage.UniversalMessage
import com.tesla.generated.vcsec.Vcsec

/** Whether the car accepts a BLE key, as seen by an authenticated status read. */
enum class Enrollment { PAIRED, NOT_PAIRED, INSUFFICIENT_PRIVILEGES, OUT_OF_RANGE, NO_REPLY }

/** Adding a BLE key to the car: the request the car shows on screen once the Keycard is tapped. */
object KeyEnrollment {
    /**
     * Asks the car to add [publicKey] (uncompressed SEC1) with the driver role. The car does not
     * answer: the user taps the Keycard on the centre console and confirms on the car's screen.
     * Same message as Tesla's `tesla-control add-key-request`.
     */
    suspend fun requestAddKey(transport: Transport, publicKey: ByteArray) {
        val payload = Vcsec.UnsignedMessage.newBuilder().setWhitelistOperation(
            Vcsec.WhitelistOperation.newBuilder()
                .setAddKeyToWhitelistAndAddPermissions(
                    Vcsec.PermissionChange.newBuilder()
                        .setKey(Vcsec.PublicKey.newBuilder().setPublicKeyRaw(ByteString.copyFrom(publicKey)))
                        .setKeyRole(Keys.Role.ROLE_DRIVER),
                )
                .setMetadataForKey(Vcsec.KeyMetadata.newBuilder().setKeyFormFactor(Vcsec.KeyFormFactor.KEY_FORM_FACTOR_ANDROID_DEVICE)),
        ).build()
        val envelope = Vcsec.ToVCSECMessage.newBuilder().setSignedMessage(
            Vcsec.SignedMessage.newBuilder()
                .setProtobufMessageAsBytes(payload.toByteString())
                .setSignatureType(Vcsec.SignatureType.SIGNATURE_TYPE_PRESENT_KEY),
        ).build()
        transport.sendRaw(envelope.toByteArray())
    }

    /**
     * Reads the car's status with the key behind [executor]: only a key the car accepts can do it.
     * [onFailure] receives the underlying error of any non-paired outcome (for diagnostics).
     */
    suspend fun verify(executor: CommandExecutor, onFailure: (Exception) -> Unit = {}): Enrollment = try {
        executor.query(Domain.VCSEC, VehicleReads.vcsecStatusRequest(), authenticated = true)
        Enrollment.PAIRED
    } catch (e: VehicleFault) {
        onFailure(e)
        if (e.fault == UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_INSUFFICIENT_PRIVILEGES) Enrollment.INSUFFICIENT_PRIVILEGES
        else Enrollment.NOT_PAIRED
    } catch (e: TransportTimeoutException) {
        onFailure(e)
        Enrollment.NO_REPLY
    } catch (e: TransportUnavailableException) {
        onFailure(e)
        // The executor reports a handshake that got no reply as "unavailable" caused by a timeout.
        if (e.cause is TransportTimeoutException) Enrollment.NO_REPLY else Enrollment.OUT_OF_RANGE
    }
}
