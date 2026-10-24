package dev.giack.teslawear.protocol

import com.tesla.generated.universalmessage.UniversalMessage
import com.tesla.generated.universalmessage.UniversalMessage.RoutableMessage
import com.tesla.generated.vcsec.Vcsec
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyEnrollmentTest {
    private class RecordingTransport : Transport {
        override val authMethod = AuthMethod.AES_GCM
        val raw = mutableListOf<ByteArray>()
        override suspend fun send(message: RoutableMessage, timeoutMs: Long): RoutableMessage = error("not used")
        override suspend fun sendRaw(bytes: ByteArray) { raw += bytes }
    }

    /** The envelope with its signed payload decoded, so equality covers the whole request. */
    private fun decode(bytes: ByteArray): Pair<Vcsec.SignatureType, Vcsec.UnsignedMessage> {
        val signed = Vcsec.ToVCSECMessage.parseFrom(bytes).signedMessage
        return signed.signatureType to Vcsec.UnsignedMessage.parseFrom(signed.protobufMessageAsBytes)
    }

    /** A car answering every message through [reply]; throwing simulates the link. */
    private class FakeCar(val reply: (RoutableMessage) -> RoutableMessage) : Transport {
        override val authMethod = AuthMethod.AES_GCM
        override suspend fun send(message: RoutableMessage, timeoutMs: Long) = reply(message)
    }

    private fun fault(f: UniversalMessage.MessageFault_E) = RoutableMessage.newBuilder()
        .setSignedMessageStatus(UniversalMessage.MessageStatus.newBuilder().setSignedMessageFault(f)).build()

    private fun executorFor(car: Transport) =
        CommandExecutor(DocVectors.VIN, JvmEcdhKey.fromPem(DocVectors.CLIENT_PEM), car, NoCache, timeoutMs = 1_000)

    private object NoCache : SessionCache {
        override fun load(domain: Domain): SessionState? = null
        override fun save(state: SessionState) = Unit
    }

    @Test fun keyTheCarDoesNotKnowIsNotPairedYet() = runTest {
        val car = FakeCar { fault(UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_UNKNOWN_KEY_ID) }
        assertEquals(Enrollment.NOT_PAIRED, KeyEnrollment.verify(executorFor(car)))
    }

    @Test fun failureReasonIsReported() = runTest {
        val car = FakeCar { fault(UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_UNKNOWN_KEY_ID) }
        val reasons = mutableListOf<String>()
        KeyEnrollment.verify(executorFor(car)) { reasons.add(it.message.orEmpty()) }
        assertEquals(listOf("MESSAGEFAULT_ERROR_UNKNOWN_KEY_ID"), reasons)
    }

    @Test fun keyWithTheWrongRoleLacksPrivileges() = runTest {
        val vehicle = FakeVehicle()
        val client = JvmEcdhKey.fromPem(DocVectors.CLIENT_PEM)
        val car = FakeCar { msg ->
            if (msg.hasSessionInfoRequest()) {
                vehicle.sessionInfoResponse(client.publicBytes, msg.uuid.toByteArray(), Domain.VCSEC)
            } else {
                fault(UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_INSUFFICIENT_PRIVILEGES)
            }
        }
        assertEquals(Enrollment.INSUFFICIENT_PRIVILEGES, KeyEnrollment.verify(executorFor(car)))
    }

    @Test fun carOutOfBluetoothRange() = runTest {
        val car = FakeCar { throw TransportUnavailableException("car not found") }
        assertEquals(Enrollment.OUT_OF_RANGE, KeyEnrollment.verify(executorFor(car)))
    }

    @Test fun carThatDoesNotAnswerTheHandshake() = runTest {
        val car = FakeCar { throw TransportTimeoutException() }
        assertEquals(Enrollment.NO_REPLY, KeyEnrollment.verify(executorFor(car)))
    }

    @Test fun keyTheCarAcceptsIsPaired() = runTest {
        val vehicle = FakeVehicle()
        val client = JvmEcdhKey.fromPem(DocVectors.CLIENT_PEM)
        val status = Vcsec.FromVCSECMessage.newBuilder().setVehicleStatus(Vcsec.VehicleStatus.getDefaultInstance()).build()
        val car = FakeCar { msg ->
            if (msg.hasSessionInfoRequest()) {
                vehicle.sessionInfoResponse(client.publicBytes, msg.uuid.toByteArray(), Domain.VCSEC)
            } else {
                vehicle.encryptedResponse(client.publicBytes, Domain.VCSEC, requestHashOf(msg), msg.uuid.toByteArray(), status.toByteArray(), 1)
            }
        }
        assertEquals(Enrollment.PAIRED, KeyEnrollment.verify(executorFor(car)))
    }

    private val publicKey = ByteArray(65) { it.toByte() }.also { it[0] = 0x04 }

    @Test fun addKeyRequestMatchesTeslaSdk() = runTest {
        val transport = RecordingTransport()
        KeyEnrollment.requestAddKey(transport, publicKey)
        // Encoded by Tesla's vehicle-command v0.4.1 (addKeyPayload, ROLE_DRIVER, KEY_FORM_FACTOR_ANDROID_DEVICE,
        // SIGNATURE_TYPE_PRESENT_KEY) for the same public key. Go writes the fields in another order, so the
        // messages are compared decoded.
        val tesla = unhex(
            "0a54125082014d320208072a470a430a41040102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f" +
                "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f4020031802",
        )
        assertEquals(decode(tesla), decode(transport.raw.single()))
    }
}
