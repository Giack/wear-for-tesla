package dev.giack.teslawear.core.fleet

import com.tesla.generated.universalmessage.UniversalMessage.RoutableMessage
import dev.giack.teslawear.protocol.AuthMethod
import dev.giack.teslawear.protocol.Transport
import dev.giack.teslawear.protocol.TransportTimeoutException
import dev.giack.teslawear.protocol.TransportUnavailableException
import java.io.IOException
import java.io.InterruptedIOException

class VehicleAsleepException : Exception("vehicle asleep")

/** Tesla refused the request itself (HTTP 4xx): it never reached the car. */
class FleetRejectedException(val code: Int) : Exception("Fleet API $code")

/** Sends signed commands through Fleet API `signed_command` (HMAC-authenticated, as Fleet API requires). */
class FleetTransport(private val client: FleetApiClient, private val vin: String) : Transport {
    override val authMethod = AuthMethod.HMAC

    override suspend fun send(message: RoutableMessage, timeoutMs: Long): RoutableMessage {
        val result = try {
            client.signedCommand(vin, message.toByteArray())
        } catch (e: InterruptedIOException) {
            throw TransportTimeoutException("Fleet API timeout")
        } catch (e: IOException) {
            throw TransportUnavailableException("Fleet API unreachable", e)
        }
        return when (result) {
            is FleetResult.Ok -> RoutableMessage.parseFrom(result.value)
            FleetResult.Asleep -> throw VehicleAsleepException()
            // A 5xx may follow Tesla forwarding the command, so the car may have executed it.
            is FleetResult.Error ->
                if (result.code >= 500) throw TransportTimeoutException("Fleet API ${result.code}: ${result.message}")
                else throw FleetRejectedException(result.code)
        }
    }
}
