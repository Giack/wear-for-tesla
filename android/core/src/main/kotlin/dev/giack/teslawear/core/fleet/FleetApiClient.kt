package dev.giack.teslawear.core.fleet

import dev.giack.teslawear.core.Snapshot
import dev.giack.teslawear.core.usage.UsageKind
import dev.giack.teslawear.core.usage.UsageMeter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Base64

sealed interface FleetResult<out T> {
    data class Ok<T>(val value: T) : FleetResult<T>
    data object Asleep : FleetResult<Nothing>
    data class Error(val code: Int, val message: String) : FleetResult<Nothing>
}

/** The Fleet API calls the router and refresher need (faked in tests). */
interface VehicleApi {
    suspend fun vehicleState(vin: String): FleetResult<String>
    suspend fun vehicleData(vin: String): FleetResult<Snapshot>
    suspend fun wakeUp(vin: String): FleetResult<Unit>
}

/** Where the user's regional Fleet API lives, once found. */
interface RegionStore {
    fun load(): HttpUrl?
    fun save(url: HttpUrl)
}

/**
 * Minimal Fleet API client. Every call is recorded in the usage meter. The first call asks the
 * [regionalServers] in turn for the user's region and remembers the server it names. A server where the
 * Developer app is not registered answers 412, so the next one is tried.
 */
class FleetApiClient(
    private val regionalServers: List<HttpUrl>,
    private val tokens: TokenProvider,
    http: OkHttpClient,
    private val meter: UsageMeter,
    private val region: RegionStore,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : VehicleApi {
    // OkHttp silently repeats a request answered with 408 (vehicle asleep); every repeat is billed.
    private val http = http.newBuilder().retryOnConnectionFailure(false).build()

    override suspend fun vehicleState(vin: String): FleetResult<String> =
        call(UsageKind.DATA, get("api/1/vehicles/$vin")) { body ->
            Json.parseToJsonElement(body).jsonObject.getValue("response").jsonObject.getValue("state").jsonPrimitive.content
        }

    override suspend fun vehicleData(vin: String): FleetResult<Snapshot> {
        val url = baseUrl().newBuilder().addPathSegments("api/1/vehicles/$vin/vehicle_data")
            .addQueryParameter("endpoints", "charge_state;climate_state;vehicle_state;vehicle_config").build()
        return call(UsageKind.DATA, Request.Builder().url(url).get()) { VehicleDataMapper.map(it, nowMs()) }
    }

    override suspend fun wakeUp(vin: String): FleetResult<Unit> =
        call(UsageKind.WAKE, post("api/1/vehicles/$vin/wake_up", "")) { }

    suspend fun signedCommand(vin: String, routable: ByteArray): FleetResult<ByteArray> {
        val body = """{"routable_message":"${Base64.getEncoder().encodeToString(routable)}"}"""
        return call(UsageKind.COMMAND, post("api/1/vehicles/$vin/signed_command", body)) { text ->
            Base64.getDecoder().decode(Json.parseToJsonElement(text).jsonObject.getValue("response").jsonPrimitive.content)
        }
    }

    private suspend fun baseUrl(): HttpUrl = region.load() ?: discoverRegion().also(region::save)

    private suspend fun discoverRegion(): HttpUrl {
        val answers = mutableListOf<FleetResult<HttpUrl>>()
        for (server in regionalServers) {
            val request = Request.Builder().url(server.newBuilder().addPathSegments("api/1/users/region").build()).get()
            val found = call(UsageKind.DATA, request) { body ->
                Json.parseToJsonElement(body).jsonObject.getValue("response").jsonObject
                    .getValue("fleet_api_base_url").jsonPrimitive.content.toHttpUrl()
            }
            if (found is FleetResult.Ok) return found.value
            answers += found
            if (found !is FleetResult.Error || found.code != NOT_REGISTERED_HERE) break
        }
        throw IOException("Fleet API region not found: $answers")
    }

    private suspend fun get(path: String) = Request.Builder().url(baseUrl().newBuilder().addPathSegments(path).build()).get()

    private suspend fun post(path: String, json: String) = Request.Builder()
        .url(baseUrl().newBuilder().addPathSegments(path).build())
        .post(json.toRequestBody(JSON))

    private suspend fun <T> call(kind: UsageKind, request: Request.Builder, parse: (String) -> T): FleetResult<T> {
        var token = tokens.accessToken()
        for (attempt in 0..1) {
            meter.record(kind)
            val result = withContext(Dispatchers.IO) {
                http.newCall(request.header("Authorization", "Bearer $token").build()).execute().use { response ->
                    val text = response.body.string()
                    when {
                        response.code == 401 -> null
                        response.code == 408 -> FleetResult.Asleep
                        response.isSuccessful -> FleetResult.Ok(parse(text))
                        else -> FleetResult.Error(response.code, text.take(200))
                    }
                }
            }
            if (result != null) return result
            // 401: one silent refresh and a single retry (each try is billed), then ask for login.
            if (attempt == 0) token = tokens.forceRefresh()
        }
        tokens.clear()
        throw AuthRequiredException("Fleet API 401")
    }

    private companion object {
        val JSON = "application/json".toMediaType()
        const val NOT_REGISTERED_HERE = 412
    }
}
