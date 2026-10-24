package dev.giack.teslawear.core.fleet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

data class Tokens(val access: String, val refresh: String, val expiresAtMs: Long)

interface TokenStore {
    fun load(): Tokens?
    fun save(t: Tokens)
    fun clear()
}

class AuthRequiredException(message: String = "Tesla login required") : Exception(message)

/** Third-party OAuth against Tesla's Fleet auth server. */
class OAuthClient(
    private val http: OkHttpClient,
    private val clientId: String,
    private val clientSecret: String,
    private val tokenUrl: HttpUrl,
    private val audience: String = FLEET_AUDIENCE_EU,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    suspend fun exchangeCode(code: String, codeVerifier: String, redirectUri: String): Tokens = post(
        FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("code", code)
            .add("code_verifier", codeVerifier)
            .add("redirect_uri", redirectUri)
            .add("audience", audience)
            .build(),
        previousRefresh = null,
    )

    suspend fun refresh(refreshToken: String): Tokens = post(
        FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", clientId)
            .add("refresh_token", refreshToken)
            .build(),
        previousRefresh = refreshToken,
    )

    private suspend fun post(body: FormBody, previousRefresh: String?): Tokens = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(tokenUrl).post(body).build()).execute().use { response ->
            val text = response.body.string()
            if (response.code in 400..499) throw AuthRequiredException("token endpoint ${response.code}")
            if (!response.isSuccessful) throw IOException("token endpoint ${response.code}")
            val json = Json.parseToJsonElement(text).jsonObject
            Tokens(
                access = json.getValue("access_token").jsonPrimitive.content,
                refresh = json["refresh_token"]?.jsonPrimitive?.content ?: previousRefresh
                    ?: throw AuthRequiredException("no refresh token"),
                expiresAtMs = nowMs() + json.getValue("expires_in").jsonPrimitive.long * 1000,
            )
        }
    }

    companion object {
        const val FLEET_AUDIENCE_EU = "https://fleet-api.prd.eu.vn.cloud.tesla.com"
    }
}
