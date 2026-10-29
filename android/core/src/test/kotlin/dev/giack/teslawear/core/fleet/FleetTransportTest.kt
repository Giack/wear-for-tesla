package dev.giack.teslawear.core.fleet

import com.tesla.generated.universalmessage.UniversalMessage.RoutableMessage
import dev.giack.teslawear.protocol.TransportTimeoutException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FleetTransportTest {
    private val server = MockWebServer()
    private lateinit var transport: FleetTransport

    @BeforeTest fun start() {
        server.start()
        val tokens = TokenProvider(
            MemoryTokenStore(Tokens("access-1", "refresh-1", Long.MAX_VALUE)),
            OAuthClient(OkHttpClient(), "id", "secret", server.url("/token")), nowMs = { 0 },
        )
        val client = FleetApiClient(listOf(server.url("/")), tokens, OkHttpClient(), meterAt(), MemoryRegionStore(server.url("/")))
        transport = FleetTransport(client, "XP7YGCEK0SB000000")
    }

    @AfterTest fun stop() = server.shutdown()

    // A 4xx is Tesla's own answer: the command never reached the car.
    @Test fun clientErrorMeansRejectedBeforeTheCar() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"rate limited"}"""))
        val e = assertFailsWith<FleetRejectedException> { transport.send(RoutableMessage.getDefaultInstance(), 1_000) }
        assertEquals(429, e.code)
    }

    // A 5xx may come after Tesla forwarded the command: the outcome is unknown.
    @Test fun serverErrorMeansOutcomeUnknown() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":"upstream"}"""))
        assertFailsWith<TransportTimeoutException> { transport.send(RoutableMessage.getDefaultInstance(), 1_000) }
    }
}
