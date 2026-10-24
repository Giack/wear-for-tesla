package dev.giack.teslawear.protocol

import com.google.protobuf.ByteString
import com.tesla.generated.universalmessage.UniversalMessage
import com.tesla.generated.universalmessage.UniversalMessage.RoutableMessage
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Reply shapes as a Model Y sent them over BLE (2026-10-23, captured with the official SDK). */
class ReplyMatchingTest {
    private val request = Messages.envelope(Domain.VCSEC, Messages.randomBytes(16)).build()
    private val address = request.fromDestination.routingAddress

    private fun reply(toAddress: ByteString? = null, toDomain: Int? = null, requestUuid: ByteString? = null): RoutableMessage {
        val to = UniversalMessage.Destination.newBuilder()
        toAddress?.let(to::setRoutingAddress)
        toDomain?.let(to::setDomainValue)
        return RoutableMessage.newBuilder().setToDestination(to)
            .setFromDestination(UniversalMessage.Destination.newBuilder().setDomainValue(Domain.VCSEC.wire))
            .also { b -> requestUuid?.let(b::setRequestUuid) }
            .build()
    }

    @Test fun handshakeReplyEchoesUuidAndAddress() =
        assertTrue(reply(toAddress = address, requestUuid = request.uuid).isReplyTo(request))

    @Test fun vcsecReplyCarriesOnlyTheAddress() =
        assertTrue(reply(toAddress = address).isReplyTo(request))

    @Test fun replyWithOnlyTheUuidMatches() =
        assertTrue(reply(requestUuid = request.uuid).isReplyTo(request))

    @Test fun broadcastIsNoReply() =
        assertFalse(reply(toDomain = UniversalMessage.Domain.DOMAIN_BROADCAST_VALUE).isReplyTo(request))

    @Test fun anotherRequestsAddressIsNoReply() =
        assertFalse(reply(toAddress = ByteString.copyFrom(Messages.randomBytes(16))).isReplyTo(request))

    @Test fun anotherRequestsUuidIsNoReply() =
        assertFalse(reply(toAddress = address, requestUuid = ByteString.copyFrom(Messages.randomBytes(16))).isReplyTo(request))
}
