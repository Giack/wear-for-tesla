package dev.giack.teslawear.core.fleet

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenProviderTest {
    private val server = MockWebServer()
    private var now = 1_000_000L

    @BeforeTest fun start() = server.start()
    @AfterTest fun stop() = server.shutdown()

    private fun oauth() = OAuthClient(OkHttpClient(), "client-id", "client-secret", server.url("/oauth2/v3/token"), nowMs = { now })

    @Test fun returnsValidTokenWithoutRefresh() = runTest {
        val store = MemoryTokenStore(Tokens("a", "r", now + 3_600_000))
        assertEquals("a", TokenProvider(store, oauth(), nowMs = { now }).accessToken())
        assertEquals(0, server.requestCount)
    }

    @Test fun tokenProviderRefreshesNearExpiry() = runTest {
        server.enqueue(MockResponse().setBody("""{"access_token":"a2","refresh_token":"r2","expires_in":28800}"""))
        val store = MemoryTokenStore(Tokens("a", "r", now + 30_000))
        val provider = TokenProvider(store, oauth(), nowMs = { now })
        assertEquals("a2", provider.accessToken())
        assertEquals(Tokens("a2", "r2", now + 28_800_000), store.tokens)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("grant_type=refresh_token") && body.contains("refresh_token=r") && body.contains("client_id=client-id"))
        assertEquals("a2", provider.accessToken())
        assertEquals(1, server.requestCount)
    }

    @Test fun keepsOldRefreshTokenWhenNotRotated() = runTest {
        server.enqueue(MockResponse().setBody("""{"access_token":"a2","expires_in":100}"""))
        val store = MemoryTokenStore(Tokens("a", "r", now))
        TokenProvider(store, oauth(), nowMs = { now }).accessToken()
        assertEquals("r", store.tokens!!.refresh)
    }

    @Test fun tokenProviderThrowsAuthRequiredWhenRefreshFails() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"invalid_grant"}"""))
        val store = MemoryTokenStore(Tokens("a", "r", now))
        assertFailsWith<AuthRequiredException> { TokenProvider(store, oauth(), nowMs = { now }).accessToken() }
        assertNull(store.tokens)
    }

    @Test fun noTokensMeansAuthRequired() = runTest {
        assertFailsWith<AuthRequiredException> { TokenProvider(MemoryTokenStore(), oauth(), nowMs = { now }).accessToken() }
    }

    @Test fun exchangeCodeSendsPkceAndAudience() = runTest {
        server.enqueue(MockResponse().setBody("""{"access_token":"a","refresh_token":"r","expires_in":10}"""))
        val t = oauth().exchangeCode("the-code", "verifier", "https://wear.googleapis.com/3p_auth/dev.giack.teslawear")
        assertEquals(Tokens("a", "r", now + 10_000), t)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("grant_type=authorization_code"))
        assertTrue(body.contains("code_verifier=verifier"))
        assertTrue(body.contains("client_secret=client-secret"))
        assertTrue(body.contains("audience=https%3A%2F%2Ffleet-api.prd.eu.vn.cloud.tesla.com"))
    }
}
