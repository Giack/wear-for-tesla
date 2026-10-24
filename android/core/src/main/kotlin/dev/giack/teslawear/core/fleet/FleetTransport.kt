package dev.giack.teslawear.core.fleet

import com.tesla.generated.universalmessage.UniversalMessage.RoutableMessage
import dev.giack.teslawear.protocol.AuthMethod
import dev.giack.teslawear.protocol.Transport
import dev.giack.teslawear.protocol.TransportTimeoutException
import dev.giack.teslawear.protocol.TransportUnavailableException
import java.io.IOException
import java.io.InterruptedIOException

class VehicleAsleepException : Exception("vehicle asleep")

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
            is FleetResult.Error -> throw TransportUnavailableException("Fleet API ${result.code}: ${result.message}")
        }
    }
}
