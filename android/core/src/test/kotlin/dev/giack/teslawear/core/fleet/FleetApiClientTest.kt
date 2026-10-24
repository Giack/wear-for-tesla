package dev.giack.teslawear.core.fleet

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FleetApiClientTest {
    private val server = MockWebServer()
    private val meter = meterAt()
    private lateinit var client: FleetApiClient
    private val vin = "XP7YGCEK0SB000000"

    private val tokenStore = MemoryTokenStore(Tokens("access-1", "refresh-1", Long.MAX_VALUE))

    private fun tokenProvider() =
        TokenProvider(tokenStore, OAuthClient(OkHttpClient(), "id", "secret", server.url("/token")), nowMs = { 0 })

    @BeforeTest fun start() {
        server.start()
        val tokens = tokenProvider()
        client = FleetApiClient(listOf(server.url("/")), tokens, OkHttpClient(), meter, MemoryRegionStore(server.url("/")), nowMs = { 7 })
    }

    @AfterTest fun stop() = server.shutdown()

    @Test fun firstCallFindsTheUsersRegionAndRemembersIt() = runTest {
        val regional = server.url("/eu/")
        server.enqueue(MockResponse().setBody(
            """{"response":{"region":"eu","fleet_api_base_url":"${regional.toString().trimEnd('/')}"}}""",
        ))
        server.enqueue(MockResponse().setBody("""{"response":{"state":"online"}}"""))
        server.enqueue(MockResponse().setBody("""{"response":{"state":"asleep"}}"""))
        val store = MemoryRegionStore()
        val discovering = FleetApiClient(listOf(server.url("/")), tokenProvider(), OkHttpClient(), meter, store)

        assertEquals(FleetResult.Ok("online"), discovering.vehicleState(vin))
        assertEquals(FleetResult.Ok("asleep"), discovering.vehicleState(vin))

        assertEquals("/api/1/users/region", server.takeRequest().path)
        assertEquals("/eu/api/1/vehicles/$vin", server.takeRequest().path)
        assertEquals("/eu/api/1/vehicles/$vin", server.takeRequest().path)
        assertEquals("/eu", store.load()?.encodedPath)
    }

    @Test fun regionLookupSkipsServersWhereTheAppIsNotRegistered() = runTest {
        val notRegistered = MockWebServer().apply { start() }
        try {
            notRegistered.enqueue(MockResponse().setResponseCode(412).setBody("""{"error":"Account must be registered in the current region"}"""))
            server.enqueue(MockResponse().setBody(
                """{"response":{"region":"eu","fleet_api_base_url":"${server.url("/eu").toString().trimEnd('/')}"}}""",
            ))
            server.enqueue(MockResponse().setBody("""{"response":{"state":"online"}}"""))
            val store = MemoryRegionStore()
            val discovering = FleetApiClient(
                listOf(notRegistered.url("/"), server.url("/")), tokenProvider(), OkHttpClient(), meter, store,
            )

            assertEquals(FleetResult.Ok("online"), discovering.vehicleState(vin))
            assertEquals("/api/1/users/region", notRegistered.takeRequest().path)
            assertEquals("/eu", store.load()?.encodedPath)
        } finally {
            notRegistered.shutdown()
        }
    }

    @Test fun vehicleDataSendsBearerAndEndpoints() = runTest {
        server.enqueue(MockResponse().setBody(fixture("vehicle_data_online.json")))
        val result = client.vehicleData(vin)
        assertIs<FleetResult.Ok<*>>(result)
        val req = server.takeRequest()
        assertEquals("Bearer access-1", req.getHeader("Authorization"))
        assertEquals("/api/1/vehicles/$vin/vehicle_data?endpoints=charge_state%3Bclimate_state%3Bvehicle_state%3Bvehicle_config", req.path)
        assertEquals(1, meter.counts().data)
    }

    @Test fun vehicleData408IsAsleep() = runTest {
        server.enqueue(MockResponse().setResponseCode(408).setBody("""{"error":"vehicle unavailable"}"""))
        assertEquals(FleetResult.Asleep, client.vehicleData(vin))
        assertEquals(1, meter.counts().data)
    }

    @Test fun vehicleStateReadsState() = runTest {
        server.enqueue(MockResponse().setBody("""{"response":{"vin":"$vin","state":"asleep"}}"""))
        assertEquals(FleetResult.Ok("asleep"), client.vehicleState(vin))
        assertEquals("/api/1/vehicles/$vin", server.takeRequest().path)
    }

    @Test fun wakeUpCountsAsWake() = runTest {
        server.enqueue(MockResponse().setBody("""{"response":{"state":"online"}}"""))
        assertEquals(FleetResult.Ok(Unit), client.wakeUp(vin))
        assertEquals("POST", server.takeRequest().method)
        assertEquals(1, meter.counts().wakes)
    }

    @Test fun signedCommandSendsBase64Body() = runTest {
        val reply = byteArrayOf(9, 8, 7)
        server.enqueue(MockResponse().setBody("""{"response":"${Base64.getEncoder().encodeToString(reply)}"}"""))
        val result = client.signedCommand(vin, byteArrayOf(1, 2, 3))
        assertEquals(reply.toList(), (result as FleetResult.Ok).value.toList())
        val req = server.takeRequest()
        assertEquals("/api/1/vehicles/$vin/signed_command", req.path)
        assertTrue(req.body.readUtf8().contains(""""routable_message":"AQID""""))
        assertEquals(1, meter.counts().commands)
    }

    @Test fun serverErrorIsReported() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        assertEquals(500, (client.vehicleData(vin) as FleetResult.Error).code)
    }

    @Test fun unauthorizedForcesOneRefreshAndRetry() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setBody("""{"access_token":"access-2","refresh_token":"refresh-2","expires_in":3600}"""))
        server.enqueue(MockResponse().setBody("""{"response":{"state":"online"}}"""))
        assertEquals(FleetResult.Ok("online"), client.vehicleState(vin))
        server.takeRequest()
        assertEquals("/token", server.takeRequest().path)
        assertEquals("Bearer access-2", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun secondUnauthorizedRequiresLoginAndClearsTokens() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setBody("""{"access_token":"access-2","refresh_token":"refresh-2","expires_in":3600}"""))
        server.enqueue(MockResponse().setResponseCode(401))
        kotlin.test.assertFailsWith<AuthRequiredException> { client.vehicleState(vin) }
        assertEquals(null, tokenStore.tokens)
    }
}
