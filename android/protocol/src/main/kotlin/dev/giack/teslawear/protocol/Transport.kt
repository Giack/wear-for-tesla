package dev.giack.teslawear.protocol

import com.tesla.generated.universalmessage.UniversalMessage.RoutableMessage

/** Carries RoutableMessages to the vehicle (BLE or Fleet API). */
interface Transport {
    val authMethod: AuthMethod

    /** Sends [message] and returns the first reply to it (see [isReplyTo]). */
    suspend fun send(message: RoutableMessage, timeoutMs: Long): RoutableMessage

    /** Further replies to the same request (VCSEC sends WAIT before the final status); null if none can arrive. */
    suspend fun next(request: RoutableMessage, timeoutMs: Long): RoutableMessage? = null

    /** Sends a bare VCSEC message with no reply expected (add-key request); only Bluetooth can. */
    suspend fun sendRaw(bytes: ByteArray): Unit = throw TransportUnavailableException("raw VCSEC messages need Bluetooth")
}

class TransportTimeoutException(message: String = "no reply from vehicle") : Exception(message)

class TransportUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface SessionCache {
    fun load(domain: Domain): SessionState?
    fun save(state: SessionState)
}

sealed interface CommandResult {
    data object Ok : CommandResult
    data class Failed(val reason: String) : CommandResult
    /** Sent, but no reply arrived: the vehicle may or may not have executed it. */
    data object Unknown : CommandResult
}

/** Something that executes vehicle commands (implemented by CommandExecutor; faked in tests). */
interface CommandRunner {
    suspend fun execute(cmd: VehicleCommand): CommandResult
}

/** Vehicle domain public keys learned over an authenticated channel (Fleet API over TLS). */
interface VehicleKeyPins {
    fun get(domain: Domain): ByteArray?
    fun pin(domain: Domain, key: ByteArray)
}

/** The vehicle answered with a protocol-level fault (signed_message_fault), e.g. it declined the request. */
class VehicleFault(
    val fault: com.tesla.generated.universalmessage.UniversalMessage.MessageFault_E?,
    message: String = fault?.name ?: "vehicle fault",
) : Exception(message)
