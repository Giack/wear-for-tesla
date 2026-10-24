package dev.giack.teslawear.protocol

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SessionTest {
    private val clientKey = JvmEcdhKey.fromPem(DocVectors.CLIENT_PEM)
    private val docUuid = unhex("1588d5a30eabc6f8fc9a951b11f6fd11")
    private val docInfo = unhex("0806124104c7a1f47138486aa4729971494878d33b1a24e39571f748a6e16c5955b3d877d3a6aaa0e955166474af5d32c410f439a2234137ad1bb085fd4e8813c958f11d971a104c463f9cc0d3d26906e982ed224adde6255a0a0000")
    private val docTag = unhex("996c1fe38331be138f8039c194b14db2198846ed7d8251e6749284d7b32ea002")
    private var now = 1_000L
    private lateinit var s: Session

    private fun response(info: ByteArray, tag: ByteArray, uuid: ByteArray = docUuid) =
        FakeVehicle().sessionInfoResponse(clientKey.publicBytes, uuid, Domain.INFOTAINMENT).toBuilder()
            .setSessionInfo(com.google.protobuf.ByteString.copyFrom(info))
            .setSignatureData(com.tesla.generated.signatures.Signatures.SignatureData.newBuilder().setSessionInfoTag(
                com.tesla.generated.signatures.Signatures.HMAC_Signature_Data.newBuilder().setTag(com.google.protobuf.ByteString.copyFrom(tag))))
            .build()

    @BeforeTest fun docSession() {
        s = Session(DocVectors.VIN, Domain.INFOTAINMENT, clientKey, nowMs = { now })
        s.acceptSessionInfo(response(docInfo, docTag), docUuid)
    }

    @Test fun acceptsDocSessionInfo() {
        assertEquals("4c463f9cc0d3d26906e982ed224adde6", s.state!!.epoch.hex())
        assertEquals(6, s.state!!.counter)
        assertEquals(2650, s.state!!.clockTime)
    }

    @Test fun rejectsTamperedSessionInfoTag() {
        val fresh = Session(DocVectors.VIN, Domain.INFOTAINMENT, clientKey, nowMs = { now })
        val bad = docTag.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertFailsWith<SessionException> { fresh.acceptSessionInfo(response(docInfo, bad), docUuid) }
        assertNull(fresh.state)
    }

    @Test fun sessionInfoRequestCarriesPublicKey() {
        val req = s.sessionInfoRequest()
        assertEquals(clientKey.publicBytes.hex(), req.message.sessionInfoRequest.publicKey.toByteArray().hex())
        assertEquals(Domain.INFOTAINMENT.wire, req.message.toDestination.domainValue)
        assertEquals(16, req.requestUuid.size)
        assertEquals(req.requestUuid.hex(), req.message.uuid.toByteArray().hex())
    }

    @Test fun aesGcmSignMatchesDoc() {
        val req = s.sign(unhex("120452020801"), AuthMethod.AES_GCM, expiresInSec = 5, nonce = unhex("dbf79447fa156674dae1caed"))
        val gcm = req.message.signatureData.aesgcmPersonalizedData
        assertEquals(7, gcm.counter)
        assertEquals(2655, gcm.expiresAt)
        assertEquals("38038e8c0f2e", req.message.protobufMessageAsBytes.toByteArray().hex())
        assertEquals("c228e0ff64991481db3a7bbc133696c5", gcm.tag.toByteArray().hex())
        assertEquals(clientKey.publicBytes.hex(), req.message.signatureData.signerIdentity.publicKey.toByteArray().hex())
        assertEquals(2, req.message.flags)
        assertEquals("05c228e0ff64991481db3a7bbc133696c5", req.requestHash.hex())
    }

    @Test fun hmacSignMatchesDerivedVector() {
        val req = s.sign(unhex("120452020801"), AuthMethod.HMAC, expiresInSec = 5)
        assertEquals("120452020801", req.message.protobufMessageAsBytes.toByteArray().hex())
        val hmac = req.message.signatureData.hmacPersonalizedData
        assertEquals(7, hmac.counter)
        assertEquals("f3c24554a00e42160e6acef6dd4f1c8ef9c30ed6391ec818d75663bee00a47f0", hmac.tag.toByteArray().hex())
    }

    @Test fun expiryFollowsLocalClock() {
        now += 10_000
        assertEquals(2650 + 10 + 15, s.sign(unhex("00"), AuthMethod.HMAC).message.signatureData.hmacPersonalizedData.expiresAt)
    }

    @Test fun vcsecRequestHashIsTruncatedTo17Bytes() {
        val v = Session(DocVectors.VIN, Domain.VCSEC, clientKey, nowMs = { now })
        val vehicle = FakeVehicle()
        v.acceptSessionInfo(vehicle.sessionInfoResponse(clientKey.publicBytes, docUuid, Domain.VCSEC), docUuid)
        val req = v.sign(unhex("00"), AuthMethod.HMAC)
        assertEquals(17, req.requestHash.size)
        assertEquals(8, req.requestHash[0].toInt())
    }

    private fun sessionWith(vehicle: FakeVehicle) = Session(DocVectors.VIN, Domain.INFOTAINMENT, clientKey, nowMs = { now }).also {
        it.acceptSessionInfo(vehicle.sessionInfoResponse(clientKey.publicBytes, docUuid, Domain.INFOTAINMENT), docUuid)
    }

    @Test fun counterNeverRollsBackWithinEpoch() {
        val vehicle = FakeVehicle().apply { counter = 10; clock = 100 }
        val session = sessionWith(vehicle)
        session.sign(unhex("00"), AuthMethod.HMAC); session.sign(unhex("00"), AuthMethod.HMAC)
        vehicle.counter = 5; vehicle.clock = 101
        session.acceptSessionInfo(vehicle.sessionInfoResponse(clientKey.publicBytes, docUuid, Domain.INFOTAINMENT), docUuid)
        assertEquals(13, session.sign(unhex("00"), AuthMethod.HMAC).message.signatureData.hmacPersonalizedData.counter)
    }

    @Test fun counterResetsOnNewEpoch() {
        val vehicle = FakeVehicle().apply { counter = 10 }
        val session = sessionWith(vehicle)
        vehicle.epoch = unhex("00112233445566778899aabbccddeeff"); vehicle.counter = 3; vehicle.clock = 5
        session.acceptSessionInfo(vehicle.sessionInfoResponse(clientKey.publicBytes, docUuid, Domain.INFOTAINMENT), docUuid)
        assertEquals(4, session.sign(unhex("00"), AuthMethod.HMAC).message.signatureData.hmacPersonalizedData.counter)
    }

    @Test fun ignoresOlderClockInSameEpoch() {
        val vehicle = FakeVehicle().apply { clock = 500 }
        val session = sessionWith(vehicle)
        vehicle.clock = 400
        session.acceptSessionInfo(vehicle.sessionInfoResponse(clientKey.publicBytes, docUuid, Domain.INFOTAINMENT), docUuid)
        assertEquals(500, session.state!!.clockTime)
    }

    @Test fun rejectsChangedVehicleKey() {
        val other = FakeVehicle(key = JvmEcdhKey.generate())
        assertFailsWith<SessionException> {
            s.acceptSessionInfo(other.sessionInfoResponse(clientKey.publicBytes, docUuid, Domain.INFOTAINMENT), docUuid)
        }
    }

    @Test fun stateRoundTripsThroughEncoding() {
        val restored = Session(DocVectors.VIN, Domain.INFOTAINMENT, clientKey, nowMs = { now })
        restored.restore(SessionState.decode(s.state!!.encode()))
        val a = s.sign(unhex("120452020801"), AuthMethod.HMAC, expiresInSec = 5)
        val b = restored.sign(unhex("120452020801"), AuthMethod.HMAC, expiresInSec = 5)
        assertEquals(a.message.signatureData.hmacPersonalizedData.tag, b.message.signatureData.hmacPersonalizedData.tag)
    }

    @Test fun openResponseRoundTrip() {
        val req = s.sign(unhex("120452020801"), AuthMethod.AES_GCM)
        val reply = FakeVehicle().encryptedResponse(clientKey.publicBytes, Domain.INFOTAINMENT, req.requestHash, req.requestUuid,
            plaintext = unhex("3a00"), responseCounter = 1)
        assertEquals("3a00", s.openResponse(reply, req.requestHash).hex())
        assertFailsWith<SessionException> { s.openResponse(reply, req.requestHash) }
    }

    @Test fun openResponseRejectsUnencryptedReply() {
        val req = s.sign(unhex("00"), AuthMethod.HMAC)
        val reply = com.tesla.generated.universalmessage.UniversalMessage.RoutableMessage.newBuilder()
            .setProtobufMessageAsBytes(com.google.protobuf.ByteString.copyFrom(unhex("3a00"))).build()
        assertFailsWith<SessionException> { s.openResponse(reply, req.requestHash) }
    }

    @Test fun openResponseUsesSessionDomainNotReplyClaim() {
        val req = s.sign(unhex("120452020801"), AuthMethod.AES_GCM)
        val reply = FakeVehicle().encryptedResponse(clientKey.publicBytes, Domain.VCSEC, req.requestHash, req.requestUuid,
            plaintext = unhex("3a00"), responseCounter = 1)
        assertFailsWith<SessionException> { s.openResponse(reply, req.requestHash) }
    }

    @Test fun rejectsUnexpectedVehicleKeyOnFirstHandshake() {
        val pinned = JvmEcdhKey.generate().publicBytes
        val fresh = Session(DocVectors.VIN, Domain.INFOTAINMENT, clientKey, nowMs = { now }, expectedVehiclePublic = pinned)
        assertFailsWith<SessionException> { fresh.acceptSessionInfo(response(docInfo, docTag), docUuid) }
        assertNull(fresh.state)
    }

    @Test fun acceptsPinnedVehicleKey() {
        val fresh = Session(DocVectors.VIN, Domain.INFOTAINMENT, clientKey, nowMs = { now }, expectedVehiclePublic = DocVectors.vehiclePublic)
        fresh.acceptSessionInfo(response(docInfo, docTag), docUuid)
        assertEquals(6, fresh.state!!.counter)
    }
}
