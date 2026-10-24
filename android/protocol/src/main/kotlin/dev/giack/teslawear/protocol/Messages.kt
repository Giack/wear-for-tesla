package dev.giack.teslawear.protocol

import com.google.protobuf.ByteString
import com.tesla.generated.universalmessage.UniversalMessage
import com.tesla.generated.universalmessage.UniversalMessage.RoutableMessage
import java.security.SecureRandom

internal object Messages {
    private val random = SecureRandom()

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { random.nextBytes(it) }

    /** Envelope addressed to [domain] with a fresh routing address and uuid. */
    fun envelope(domain: Domain, uuid: ByteArray): RoutableMessage.Builder = RoutableMessage.newBuilder()
        .setToDestination(UniversalMessage.Destination.newBuilder().setDomainValue(domain.wire))
        .setFromDestination(UniversalMessage.Destination.newBuilder().setRoutingAddress(ByteString.copyFrom(randomBytes(16))))
        .setUuid(ByteString.copyFrom(uuid))
}

/**
 * Whether this message answers [request]. Handshake and infotainment replies echo the request's
 * uuid; VCSEC status and command replies carry no request_uuid, only the request's (random,
 * per-message) routing address. Broadcasts carry neither and never match.
 */
fun RoutableMessage.isReplyTo(request: RoutableMessage): Boolean {
    val address = toDestination.routingAddress
    if (requestUuid.isEmpty && address.isEmpty) return false
    return (requestUuid.isEmpty || requestUuid == request.uuid) &&
        (address.isEmpty || address == request.fromDestination.routingAddress)
}

class SignedRequest(val message: RoutableMessage, val requestUuid: ByteArray, val requestHash: ByteArray)
