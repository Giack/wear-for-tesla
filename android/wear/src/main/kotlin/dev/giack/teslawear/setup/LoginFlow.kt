package dev.giack.teslawear.setup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.wear.phone.interactions.authentication.CodeChallenge
import androidx.wear.phone.interactions.authentication.CodeVerifier
import androidx.wear.phone.interactions.authentication.OAuthRequest
import androidx.wear.phone.interactions.authentication.OAuthResponse
import androidx.wear.phone.interactions.authentication.RemoteAuthClient
import androidx.wear.remote.interactions.RemoteActivityHelper
import dev.giack.teslawear.AppGraph
import dev.giack.teslawear.R
import dev.giack.teslawear.TeslaConfig
import dev.giack.teslawear.ui.UiText
import dev.giack.teslawear.ui.text
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/** Tesla login opened on the phone's browser via RemoteAuthClient (PKCE S256). */
class LoginFlow(private val context: Context, private val fleet: AppGraph.FleetPieces) {
    sealed interface Result {
        data object Success : Result
        data class Error(val message: UiText) : Result
    }

    suspend fun login(): Result {
        val verifier = CodeVerifier()
        val state = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val authUri = Uri.parse(TeslaConfig.AUTH_URL).buildUpon()
            .appendQueryParameter("scope", TeslaConfig.SCOPES)
            .appendQueryParameter("state", state)
            .appendQueryParameter("locale", Locale.getDefault().toLanguageTag())
            .build()
        val request = OAuthRequest.Builder(context)
            .setAuthProviderUrl(authUri)
            .setClientId(fleet.secrets.clientId)
            .setCodeChallenge(CodeChallenge(verifier))
            .build()
        val client = RemoteAuthClient.create(context)
        try {
            val response = suspendCancellableCoroutine<Pair<OAuthResponse?, Int?>> { cont ->
                client.sendAuthorizationRequest(request, Executors.newSingleThreadExecutor(), object : RemoteAuthClient.Callback() {
                    override fun onAuthorizationResponse(request: OAuthRequest, response: OAuthResponse) {
                        cont.resume(response to null)
                    }
                    override fun onAuthorizationError(request: OAuthRequest, errorCode: Int) {
                        cont.resume(null to errorCode)
                    }
                })
            }
            val url = response.first?.responseUrl ?: return Result.Error(text(R.string.login_cancelled, response.second.toString()))
            if (url.getQueryParameter("state") != state) return Result.Error(text(R.string.login_invalid_reply))
            val code = url.getQueryParameter("code") ?: return Result.Error(text(R.string.login_no_code))
            val tokens = fleet.oauth.exchangeCode(code, verifier.value, request.redirectUrl)
            fleet.tokens.save(tokens)
            return Result.Success
        } catch (e: Exception) {
            return Result.Error(text(R.string.login_failed))
        } finally {
            client.close()
        }
    }

    /** Opens tesla.com/_ak/<domain> on the phone so the Tesla app adds the Cloud key to the car; false without a domain. */
    fun openPairingOnPhone(): Boolean {
        val domain = fleet.secrets.domain ?: return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(TeslaConfig.pairingUrl(domain))).addCategory(Intent.CATEGORY_BROWSABLE)
        RemoteActivityHelper(context, Executors.newSingleThreadExecutor()).startRemoteActivity(intent)
        return true
    }
}
