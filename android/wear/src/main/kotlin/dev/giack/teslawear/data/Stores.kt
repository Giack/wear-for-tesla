package dev.giack.teslawear.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.giack.teslawear.core.Snapshot
import dev.giack.teslawear.core.SnapshotRepository
import dev.giack.teslawear.core.fleet.RegionStore
import dev.giack.teslawear.core.fleet.TokenStore
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import dev.giack.teslawear.core.setup.BleKeyStatus
import dev.giack.teslawear.core.setup.Car
import dev.giack.teslawear.core.setup.CarModel
import dev.giack.teslawear.core.setup.CarProfile
import dev.giack.teslawear.core.setup.ProfileSource
import dev.giack.teslawear.core.setup.SetupState
import dev.giack.teslawear.core.setup.SetupStore
import dev.giack.teslawear.core.fleet.Tokens
import dev.giack.teslawear.core.usage.UsageCounts
import dev.giack.teslawear.core.usage.UsageMeter
import dev.giack.teslawear.core.usage.UsageStore
import dev.giack.teslawear.protocol.Domain
import dev.giack.teslawear.protocol.SessionCache
import dev.giack.teslawear.protocol.SessionState
import dev.giack.teslawear.protocol.VehicleKeyPins
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.util.Base64

private val Context.snapshotStore by preferencesDataStore("snapshot")
private val SNAPSHOT = stringPreferencesKey("snapshot")

class DataStoreSnapshotRepository(
    private val context: Context,
    private val onSaved: (Snapshot) -> Unit = {},
) : SnapshotRepository {
    val updates: Flow<Snapshot?> = context.snapshotStore.data.map { prefs -> prefs[SNAPSHOT]?.let(SnapshotCodec::decode) }

    override suspend fun load(): Snapshot? = updates.first()

    override suspend fun save(s: Snapshot) {
        context.snapshotStore.edit { it[SNAPSHOT] = SnapshotCodec.encode(s) }
        onSaved(s)
    }

    suspend fun clear() { context.snapshotStore.edit { it.remove(SNAPSHOT) } }
}

/** OAuth tokens, encrypted at rest. */
class EncryptedTokenStore(context: Context) : TokenStore {
    private val file = EncryptedJsonFile(context.filesDir.resolve("tokens.bin"), KeystoreCipher("tokens"))

    override fun load(): Tokens? = file.read()?.let {
        Tokens(it.getValue("a").jsonPrimitive.content, it.getValue("r").jsonPrimitive.content, it.getValue("e").jsonPrimitive.long)
    }

    override fun save(t: Tokens) = file.write(
        buildJsonObject { put("a", JsonPrimitive(t.access)); put("r", JsonPrimitive(t.refresh)); put("e", JsonPrimitive(t.expiresAtMs)) },
    )

    override fun clear() = file.delete()
}

class PrefsUsageStore(context: Context) : UsageStore {
    private val prefs = context.getSharedPreferences("usage", Context.MODE_PRIVATE)

    override fun load(): UsageCounts? = prefs.getString("month", null)?.let {
        UsageCounts(it, prefs.getInt("data", 0), prefs.getInt("commands", 0), prefs.getInt("wakes", 0))
    }

    override fun save(c: UsageCounts) {
        prefs.edit().putString("month", c.month).putInt("data", c.data).putInt("commands", c.commands)
            .putInt("wakes", c.wakes).apply()
    }
}

/** The monthly Fleet API budget the user picked in Setup. */
class PrefsBudgetStore(context: Context) {
    private val prefs = context.getSharedPreferences("usage", Context.MODE_PRIVATE)

    fun load(): Double = prefs.getFloat("budget_usd", UsageMeter.DEFAULT_BUDGET_USD.toFloat()).toDouble()
    fun save(usd: Double) = prefs.edit().putFloat("budget_usd", usd.toFloat()).apply()
}

/** Setup state; holds no secrets (keys and tokens live in their encrypted stores). */
class PrefsSetupStore(context: Context) : SetupStore {
    private val prefs = context.getSharedPreferences("setup", Context.MODE_PRIVATE)

    override fun load(): SetupState? {
        if (!prefs.getBoolean("saved", false)) return null
        val car = prefs.getString("car_name", null)?.let { Car(it, prefs.getString("car_vin", null)) }
        val profile = prefs.getString("model", null)?.let { model ->
            CarProfile(
                CarModel.valueOf(model), prefs.getString("paint", "").orEmpty(), prefs.getString("wheels", "").orEmpty(),
                ProfileSource.valueOf(prefs.getString("profile_source", ProfileSource.USER.name)!!),
            )
        }
        return SetupState(car, profile, BleKeyStatus.valueOf(prefs.getString("ble_key", BleKeyStatus.NONE.name)!!), prefs.getBoolean("fleet", false))
    }

    override fun save(state: SetupState) {
        prefs.edit().clear().putBoolean("saved", true)
            .putString("car_name", state.car?.localName).putString("car_vin", state.car?.vin)
            .putString("model", state.profile?.model?.name).putString("paint", state.profile?.paint)
            .putString("wheels", state.profile?.wheels).putString("profile_source", state.profile?.source?.name)
            .putString("ble_key", state.bleKey.name).putBoolean("fleet", state.fleetEnabled)
            .apply()
    }
}

/** The user's regional Fleet API server, found after login. */
class PrefsRegionStore(context: Context) : RegionStore {
    private val prefs = context.getSharedPreferences("fleet_region", Context.MODE_PRIVATE)
    override fun load(): HttpUrl? = prefs.getString("base_url", null)?.toHttpUrlOrNull()
    override fun save(url: HttpUrl) { prefs.edit().putString("base_url", url.toString()).apply() }
    fun clear() { prefs.edit().clear().apply() }
}

/** Per-transport session cache (BLE and cloud may use different keys). */
class PrefsSessionCache(context: Context, transport: String) : SessionCache {
    private val prefs = context.getSharedPreferences("sessions_$transport", Context.MODE_PRIVATE)

    override fun load(domain: Domain): SessionState? = prefs.getString(domain.name, null)?.let {
        runCatching { SessionState.decode(Base64.getDecoder().decode(it)) }.getOrNull()
    }

    override fun save(state: SessionState) {
        prefs.edit().putString(state.domain.name, Base64.getEncoder().encodeToString(state.encode())).apply()
    }

    fun clear() = prefs.edit().clear().apply()
}

/** Vehicle domain keys learned through Fleet API; BLE handshakes must present the same keys. */
class PrefsVehicleKeyPins(context: Context) : VehicleKeyPins {
    private val prefs = context.getSharedPreferences("vehicle_keys", Context.MODE_PRIVATE)

    override fun get(domain: Domain): ByteArray? = prefs.getString(domain.name, null)?.let { Base64.getDecoder().decode(it) }

    override fun pin(domain: Domain, key: ByteArray) {
        prefs.edit().putString(domain.name, Base64.getEncoder().encodeToString(key)).apply()
    }

    fun clear() = prefs.edit().clear().apply()
}
