package dev.giack.teslawear.protocol

import com.tesla.generated.carserver.server.CarServer
import com.tesla.generated.universalmessage.UniversalMessage.MessageFault_E
import com.tesla.generated.universalmessage.UniversalMessage.RoutableMessage
import com.tesla.generated.vcsec.Vcsec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs commands over one transport: lazy per-domain handshake (or cached session), signing,
 * a single resync on session faults, and reply interpretation. Transport availability errors
 * (TransportUnavailableException and transport-specific ones) propagate to the caller.
 */
class CommandExecutor(
    private val vin: String,
    private val key: EcdhKey,
    private val transport: Transport,
    private val cache: SessionCache,
    private val timeoutMs: Long = 15_000,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val pins: VehicleKeyPins? = null,
    /** True for transports whose handshake is authenticated end to end (Fleet API): they record pins; others enforce them. */
    private val trustedPins: Boolean = false,
) : CommandRunner {
    private val sessions = mutableMapOf<Domain, Session>()
    private val mutex = Mutex()

    override suspend fun execute(cmd: VehicleCommand): CommandResult = mutex.withLock {
        try {
            val (request, reply) = sendSigned(cmd.domain, cmd.payload(), resynced = false)
            interpret(cmd.domain, request, reply)
        } catch (e: TransportTimeoutException) {
            CommandResult.Unknown
        } catch (e: VehicleFault) {
            CommandResult.Failed(e.message ?: "fault")
        } catch (e: SessionException) {
            CommandResult.Failed(e.message ?: "session error")
        }
    }

    /** Returns the reply payload of a read request; VCSEC status may be sent unauthenticated. */
    suspend fun query(domain: Domain, payload: ByteArray, authenticated: Boolean = true): ByteArray = mutex.withLock {
        if (!authenticated) {
            val message = Messages.envelope(domain, Messages.randomBytes(16))
                .setProtobufMessageAsBytes(com.google.protobuf.ByteString.copyFrom(payload)).build()
            val reply = transport.send(message, timeoutMs)
            checkFault(reply)
            return@withLock reply.protobufMessageAsBytes.toByteArray()
        }
        val (request, reply) = sendSigned(domain, payload, resynced = false)
        session(domain).openResponse(reply, request.requestHash)
    }

    private suspend fun session(domain: Domain): Session {
        val expected = if (trustedPins) null else pins?.get(domain)
        sessions[domain]?.let { existing ->
            val state = existing.state
            // A pin recorded after this session started (e.g. by the Fleet API path) must still be honoured.
            if (state != null && (expected == null || state.vehiclePublic.contentEquals(expected))) return existing
            sessions.remove(domain)
        }
        val session = Session(vin, domain, key, nowMs, expected)
        val cached = cache.load(domain)?.takeIf { expected == null || it.vehiclePublic.contentEquals(expected) }
        if (cached != null) {
            session.restore(cached)
        } else {
            val request = session.sessionInfoRequest()
            val reply = try {
                transport.send(request.message, timeoutMs)
            } catch (e: TransportTimeoutException) {
                // Nothing was authorized yet, so the command was certainly not executed.
                throw TransportUnavailableException("no handshake reply from $domain", e)
            }
            checkFault(reply)
            if (!reply.hasSessionInfo()) throw VehicleFault(null, "handshake reply without session info")
            session.acceptSessionInfo(reply, request.requestUuid)
            cache.save(session.state!!)
        }
        if (trustedPins) pins?.pin(domain, session.state!!.vehiclePublic)
        sessions[domain] = session
        return session
    }

    private suspend fun sendSigned(domain: Domain, payload: ByteArray, resynced: Boolean): Pair<SignedRequest, RoutableMessage> {
        val session = session(domain)
        val request = session.sign(payload, transport.authMethod)
        cache.save(session.state!!)
        val reply = transport.send(request.message, timeoutMs)
        val fault = reply.signedMessageStatus.signedMessageFault
        if (fault != MessageFault_E.MESSAGEFAULT_ERROR_NONE) {
            if (fault in RESYNC_FAULTS && reply.hasSessionInfo() && !resynced) {
                session.acceptSessionInfo(reply, request.requestUuid)
                cache.save(session.state!!)
                return sendSigned(domain, payload, resynced = true)
            }
            throw VehicleFault(fault)
        }
        return request to reply
    }

    private suspend fun interpret(domain: Domain, request: SignedRequest, firstReply: RoutableMessage): CommandResult {
        val session = session(domain)
        var reply = firstReply
        while (true) {
            val payload = session.openResponse(reply, request.requestHash)
            when (domain) {
                Domain.INFOTAINMENT -> {
                    val status = CarServer.Response.parseFrom(payload).actionStatus
                    return if (status.result == CarServer.OperationStatus_E.OPERATIONSTATUS_ERROR) {
                        CommandResult.Failed(status.resultReason.plainText.ifEmpty { "unspecified error" })
                    } else {
                        CommandResult.Ok
                    }
                }
                Domain.VCSEC -> {
                    val message = Vcsec.FromVCSECMessage.parseFrom(payload)
                    if (message.hasNominalError()) return CommandResult.Failed(message.nominalError.genericError.name)
                    if (!message.hasCommandStatus()) return CommandResult.Ok
                    when (message.commandStatus.operationStatus) {
                        Vcsec.OperationStatus_E.OPERATIONSTATUS_WAIT ->
                            reply = transport.next(request.message, timeoutMs) ?: return CommandResult.Ok
                        Vcsec.OperationStatus_E.OPERATIONSTATUS_ERROR -> return CommandResult.Failed(
                            message.commandStatus.signedMessageStatus.signedMessageInformation.name,
                        )
                        else -> return CommandResult.Ok
                    }
                }
            }
        }
    }

    private fun checkFault(reply: RoutableMessage) {
        val fault = reply.signedMessageStatus.signedMessageFault
        if (fault != MessageFault_E.MESSAGEFAULT_ERROR_NONE) throw VehicleFault(fault)
    }

    private companion object {
        val RESYNC_FAULTS = setOf(
            MessageFault_E.MESSAGEFAULT_ERROR_INVALID_SIGNATURE,
            MessageFault_E.MESSAGEFAULT_ERROR_INVALID_TOKEN_OR_COUNTER,
            MessageFault_E.MESSAGEFAULT_ERROR_INCORRECT_EPOCH,
            MessageFault_E.MESSAGEFAULT_ERROR_TIME_EXPIRED,
        )
    }
}

