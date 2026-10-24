package dev.giack.teslawear.protocol

import com.google.protobuf.ByteString
import com.tesla.generated.carserver.server.CarServer
import com.tesla.generated.universalmessage.UniversalMessage
import com.tesla.generated.universalmessage.UniversalMessage.RoutableMessage
import com.tesla.generated.vcsec.Vcsec
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CommandExecutorTest {
    private val client = JvmEcdhKey.fromPem(DocVectors.CLIENT_PEM)
    private val vehicle = FakeVehicle()

    private class MemoryCache : SessionCache {
        val states = mutableMapOf<Domain, SessionState>()
        override fun load(domain: Domain) = states[domain]
        override fun save(state: SessionState) { states[state.domain] = state }
    }

    private class FakeTransport(
        override val authMethod: AuthMethod = AuthMethod.HMAC,
        val onCommand: (RoutableMessage) -> RoutableMessage,
    ) : Transport {
        val vehicle = FakeVehicle()
        val client = JvmEcdhKey.fromPem(DocVectors.CLIENT_PEM)
        val sent = mutableListOf<RoutableMessage>()
        val queued = ArrayDeque<(RoutableMessage) -> RoutableMessage>()
        private var lastCommand: RoutableMessage? = null
        private var responseCounter = 0L

        /** Encrypted reply to [cmd], as a vehicle on firmware 2024.38+ sends it. */
        fun reply(cmd: RoutableMessage, payload: ByteArray): RoutableMessage = vehicle.encryptedResponse(
            client.publicBytes, Domain.fromWire(cmd.toDestination.domainValue), requestHashOf(cmd), cmd.uuid.toByteArray(),
            payload, ++responseCounter)
        override suspend fun send(message: RoutableMessage, timeoutMs: Long): RoutableMessage {
            sent += message
            if (message.hasSessionInfoRequest()) {
                return vehicle.sessionInfoResponse(client.publicBytes, message.uuid.toByteArray(), Domain.fromWire(message.toDestination.domainValue))
            }
            lastCommand = message
            return onCommand(message)
        }
        override suspend fun next(request: RoutableMessage, timeoutMs: Long): RoutableMessage? =
            queued.removeFirstOrNull()?.invoke(lastCommand!!)
        val commands get() = sent.filter { it.hasProtobufMessageAsBytes() }
    }

    private fun carServerReply(ok: Boolean, reason: String = ""): ByteArray {
        val status = CarServer.ActionStatus.newBuilder()
            .setResult(if (ok) CarServer.OperationStatus_E.OPERATIONSTATUS_OK else CarServer.OperationStatus_E.OPERATIONSTATUS_ERROR)
        if (reason.isNotEmpty()) status.setResultReason(CarServer.ResultReason.newBuilder().setPlainText(reason))
        return CarServer.Response.newBuilder().setActionStatus(status).build().toByteArray()
    }

    private fun plain(payload: ByteArray) = RoutableMessage.newBuilder().setProtobufMessageAsBytes(ByteString.copyFrom(payload)).build()

    private fun executor(transport: Transport, cache: SessionCache = MemoryCache()) =
        CommandExecutor(DocVectors.VIN, client, transport, cache)

    private fun okTransport(): FakeTransport {
        lateinit var t: FakeTransport
        t = FakeTransport { t.reply(it, carServerReply(ok = true)) }
        return t
    }

    @Test fun executorHandshakesThenSends() = runTest {
        val cache = MemoryCache()
        val t = okTransport()
        assertEquals(CommandResult.Ok, executor(t, cache).execute(VehicleCommand.ClimateOn))
        assertTrue(t.sent[0].hasSessionInfoRequest())
        assertTrue(t.sent[1].signatureData.hasHMACPersonalizedData())
        assertEquals(7, cache.states[Domain.INFOTAINMENT]!!.counter)
    }

    @Test fun executorUsesCachedSessionWithoutHandshake() = runTest {
        val cache = MemoryCache()
        executor(okTransport(), cache).execute(VehicleCommand.ClimateOn)
        val t = okTransport()
        executor(t, cache).execute(VehicleCommand.ClimateOff)
        assertFalse(t.sent.any { it.hasSessionInfoRequest() })
        assertEquals(8, t.sent.single().signatureData.hmacPersonalizedData.counter)
    }

    @Test fun executorResyncsOnceOnSessionFault() = runTest {
        lateinit var t: FakeTransport
        t = FakeTransport { cmd ->
            t.vehicle.epoch = Messages.randomBytes(16)
            t.vehicle.sessionInfoResponse(client.publicBytes, cmd.uuid.toByteArray(), Domain.INFOTAINMENT).toBuilder()
                .setSignedMessageStatus(UniversalMessage.MessageStatus.newBuilder()
                    .setOperationStatus(UniversalMessage.OperationStatus_E.OPERATIONSTATUS_ERROR)
                    .setSignedMessageFault(UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_INCORRECT_EPOCH))
                .build()
        }
        val result = executor(t).execute(VehicleCommand.ClimateOn)
        assertIs<CommandResult.Failed>(result)
        assertEquals(2, t.sent.count { it.signatureData.hasHMACPersonalizedData() })
    }

    @Test fun executorDoesNotResendAfterTimeout() = runTest {
        val t = FakeTransport { throw TransportTimeoutException() }
        assertEquals(CommandResult.Unknown, executor(t).execute(VehicleCommand.OpenTrunk))
        assertEquals(1, t.sent.count { it.signatureData.hasHMACPersonalizedData() })
    }

    @Test fun executorMapsActionStatusFailure() = runTest {
        lateinit var t: FakeTransport
        t = FakeTransport { t.reply(it, carServerReply(ok = false, reason = "not_charging")) }
        assertEquals(CommandResult.Failed("not_charging"), executor(t).execute(VehicleCommand.ChargePortClose))
    }

    @Test fun executorMapsUnknownKeyFault() = runTest {
        val t = FakeTransport { RoutableMessage.newBuilder().setSignedMessageStatus(UniversalMessage.MessageStatus.newBuilder()
            .setSignedMessageFault(UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_UNKNOWN_KEY_ID)).build() }
        val result = executor(t).execute(VehicleCommand.Honk)
        assertIs<CommandResult.Failed>(result)
        assertTrue(result.reason.contains("UNKNOWN_KEY_ID"))
    }

    @Test fun vcsecWaitsForFinalReply() = runTest {
        val wait = Vcsec.FromVCSECMessage.newBuilder().setCommandStatus(
            Vcsec.CommandStatus.newBuilder().setOperationStatus(Vcsec.OperationStatus_E.OPERATIONSTATUS_WAIT)).build().toByteArray()
        lateinit var t: FakeTransport
        t = FakeTransport(AuthMethod.AES_GCM) { t.reply(it, wait) }
        t.queued += { cmd -> t.reply(cmd, Vcsec.FromVCSECMessage.getDefaultInstance().toByteArray()) }
        assertEquals(CommandResult.Ok, executor(t).execute(VehicleCommand.Lock))
        assertTrue(t.queued.isEmpty())
    }

    @Test fun vcsecErrorStatusFails() = runTest {
        lateinit var t: FakeTransport
        t = FakeTransport(AuthMethod.AES_GCM) { t.reply(it, Vcsec.FromVCSECMessage.newBuilder().setCommandStatus(
            Vcsec.CommandStatus.newBuilder().setOperationStatus(Vcsec.OperationStatus_E.OPERATIONSTATUS_ERROR)).build().toByteArray()) }
        assertIs<CommandResult.Failed>(executor(t).execute(VehicleCommand.Unlock))
    }

    @Test fun unsignedVcsecStatusQuerySkipsHandshake() = runTest {
        val status = Vcsec.FromVCSECMessage.newBuilder().setVehicleStatus(
            Vcsec.VehicleStatus.newBuilder().setVehicleLockState(Vcsec.VehicleLockState_E.VEHICLELOCKSTATE_LOCKED)).build()
        val t = FakeTransport(AuthMethod.AES_GCM) { plain(status.toByteArray()) }
        val reply = executor(t).query(Domain.VCSEC, VehicleReads.vcsecStatusRequest(), authenticated = false)
        assertEquals(Vcsec.VehicleLockState_E.VEHICLELOCKSTATE_LOCKED, Vcsec.FromVCSECMessage.parseFrom(reply).vehicleStatus.vehicleLockState)
        assertFalse(t.sent.single().hasSignatureData())
        assertNotNull(t.sent.single().toDestination)
    }

    private class MemoryPins : VehicleKeyPins {
        val keys = mutableMapOf<Domain, ByteArray>()
        override fun get(domain: Domain) = keys[domain]
        override fun pin(domain: Domain, key: ByteArray) { keys[domain] = key }
    }

    @Test fun trustedExecutorPinsVehicleKey() = runTest {
        val pins = MemoryPins()
        CommandExecutor(DocVectors.VIN, client, okTransport(), MemoryCache(), pins = pins, trustedPins = true).execute(VehicleCommand.ClimateOn)
        assertEquals(DocVectors.vehiclePublic.toList(), pins.keys[Domain.INFOTAINMENT]!!.toList())
    }

    @Test fun untrustedExecutorRejectsKeyThatDiffersFromPin() = runTest {
        val pins = MemoryPins().apply { pin(Domain.INFOTAINMENT, JvmEcdhKey.generate().publicBytes) }
        val t = okTransport()
        val result = CommandExecutor(DocVectors.VIN, client, t, MemoryCache(), pins = pins, trustedPins = false).execute(VehicleCommand.ClimateOn)
        assertIs<CommandResult.Failed>(result)
        assertEquals(0, t.commands.size)
    }

    @Test fun untrustedExecutorDoesNotWritePins() = runTest {
        val pins = MemoryPins()
        CommandExecutor(DocVectors.VIN, client, okTransport(), MemoryCache(), pins = pins, trustedPins = false).execute(VehicleCommand.ClimateOn)
        assertTrue(pins.keys.isEmpty())
    }

    @Test fun untrustedExecutorDropsInMemorySessionWhenPinChanges() = runTest {
        val pins = MemoryPins()
        val t = okTransport()
        val exec = CommandExecutor(DocVectors.VIN, client, t, MemoryCache(), pins = pins, trustedPins = false)
        exec.execute(VehicleCommand.ClimateOn)
        pins.pin(Domain.INFOTAINMENT, JvmEcdhKey.generate().publicBytes)
        assertIs<CommandResult.Failed>(exec.execute(VehicleCommand.ClimateOff))
        assertEquals(1, t.commands.size)
    }

    @Test fun vehicleFaultIsTypedForCallers() = runTest {
        val t = FakeTransport { RoutableMessage.newBuilder().setSignedMessageStatus(UniversalMessage.MessageStatus.newBuilder()
            .setSignedMessageFault(UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_UNKNOWN_KEY_ID)).build() }
        val e = kotlin.runCatching { executor(t).query(Domain.VCSEC, VehicleReads.vcsecStatusRequest()) }.exceptionOrNull()
        assertIs<VehicleFault>(e)
        assertEquals(UniversalMessage.MessageFault_E.MESSAGEFAULT_ERROR_UNKNOWN_KEY_ID, e.fault)
    }

    @Test fun handshakeTimeoutMeansNotSent() = runTest {
        val t = object : Transport {
            override val authMethod = AuthMethod.AES_GCM
            override suspend fun send(message: RoutableMessage, timeoutMs: Long): RoutableMessage = throw TransportTimeoutException()
        }
        assertFailsWith<TransportUnavailableException> { executor(t).execute(VehicleCommand.ClimateOn) }
    }

    @Test fun vcsecWaitThenTimeoutIsUnknown() = runTest {
        val wait = Vcsec.FromVCSECMessage.newBuilder().setCommandStatus(
            Vcsec.CommandStatus.newBuilder().setOperationStatus(Vcsec.OperationStatus_E.OPERATIONSTATUS_WAIT)).build().toByteArray()
        lateinit var t: FakeTransport
        t = FakeTransport(AuthMethod.AES_GCM) { t.reply(it, wait) }
        t.queued += { throw TransportTimeoutException() }
        assertEquals(CommandResult.Unknown, executor(t).execute(VehicleCommand.Lock))
    }
}
