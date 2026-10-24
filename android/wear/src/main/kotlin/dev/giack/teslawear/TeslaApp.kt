package dev.giack.teslawear

import android.app.Application
import android.content.Context
import dev.giack.teslawear.ble.AndroidBleLink
import dev.giack.teslawear.core.BleLink
import dev.giack.teslawear.core.CloudLink
import dev.giack.teslawear.core.CommandRouter
import dev.giack.teslawear.core.Snapshot
import dev.giack.teslawear.core.StatusRefresher
import dev.giack.teslawear.core.fleet.FleetApiClient
import dev.giack.teslawear.core.fleet.FleetCarConfig
import dev.giack.teslawear.core.fleet.FleetTransport
import dev.giack.teslawear.core.fleet.OAuthClient
import dev.giack.teslawear.core.fleet.TokenProvider
import dev.giack.teslawear.core.setup.ProfileSource
import dev.giack.teslawear.core.setup.Setup
import dev.giack.teslawear.core.usage.UsageMeter
import dev.giack.teslawear.data.DataStoreSnapshotRepository
import dev.giack.teslawear.data.EncryptedTokenStore
import dev.giack.teslawear.data.KeystoreEcdhKey
import dev.giack.teslawear.data.PrefsRegionStore
import dev.giack.teslawear.data.PrefsSessionCache
import dev.giack.teslawear.data.PrefsSetupStore
import dev.giack.teslawear.data.PrefsBudgetStore
import dev.giack.teslawear.data.PrefsUsageStore
import dev.giack.teslawear.data.PrefsVehicleKeyPins
import dev.giack.teslawear.data.SecretsStore
import dev.giack.teslawear.diag.Diag
import dev.giack.teslawear.protocol.CommandExecutor
import dev.giack.teslawear.render.carImages
import dev.giack.teslawear.complication.ComplicationUpdates
import dev.giack.teslawear.tile.CarTileService
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class TeslaApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        Diag.init(this)
        graph.surfaceUpdaters += { ctx -> CarTileService.requestRefresh(ctx) }
        graph.surfaceUpdaters += { ctx -> ComplicationUpdates.requestAll(ctx) }
        graph.onSnapshotSaved = { graph.notifySurfaces() }
    }
}

val Context.graph: AppGraph get() = (applicationContext as TeslaApp).graph

/** Manual dependency container. Pieces needing secrets are available once provisioned. */
class AppGraph(private val context: Context) {
    val secretsStore = SecretsStore(context)
    val setup = Setup(PrefsSetupStore(context))
    val http: OkHttpClient = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    val budget = PrefsBudgetStore(context)
    val meter = UsageMeter(PrefsUsageStore(context), budget = budget::load)

    /** For work that must outlive a screen (post-command refresh). */
    val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    val carImages = carImages(context, http) { setup.state.profile }
    private val tokenStore = EncryptedTokenStore(context)
    private val regionStore = PrefsRegionStore(context)

    /** Invoked after every snapshot save (Tile and complication refresh hook up here). */
    var onSnapshotSaved: (Snapshot) -> Unit = {}
    val snapshots = DataStoreSnapshotRepository(context) { onSnapshotSaved(it) }

    private var provisioned: Provisioned? = null

    /**
     * The car the watch drives, once its VIN is known (Bluetooth signatures include it); null before.
     * Installs provisioned by `setup.sh push-watch` are migrated to the Setup state first.
     */
    fun provisioned(): Provisioned? {
        provisioned?.let { return it }
        secretsStore.fleet()?.let { setup.adoptImportedSecrets(it.vin, hasBleKey = secretsStore.importedBleKey() != null) }
        val vin = setup.state.car?.vin ?: return null
        return Provisioned(vin).also { provisioned = it }
    }

    /** Leaves Fleet mode: deletes the Fleet credentials, tokens and region; Bluetooth keeps working. */
    fun disableFleet() {
        secretsStore.forgetFleet()
        tokenStore.clear()
        regionStore.clear()
        setup.disableFleet()
        resetProvisioning()
    }

    fun resetProvisioning() { provisioned = null }

    /**
     * Forgets the car: Setup state, the watch's BLE key, imported secrets, Fleet tokens, sessions, vehicle
     * key pins and the last snapshot. The key stays listed in the car until removed there (Controlli → Blocchi).
     */
    suspend fun removeCar() {
        secretsStore.forgetAll()
        tokenStore.clear()
        regionStore.clear()
        KeystoreEcdhKey.delete()
        listOf("ble", "ble_keystore", "cloud").forEach { PrefsSessionCache(context, it).clear() }
        PrefsVehicleKeyPins(context).clear()
        snapshots.clear()
        setup.removeCar()
        resetProvisioning()
        notifySurfaces()
    }

    /** Asks Tile and complications to redraw from the latest snapshot. */
    fun notifySurfaces() { surfaceUpdaters.forEach { it(context) } }

    /** Registered by the Tile and complication services. */
    val surfaceUpdaters = mutableListOf<(Context) -> Unit>()

    inner class Provisioned(val vin: String) {
        private val pins = PrefsVehicleKeyPins(context)
        /** Present only in Fleet mode with imported credentials; Bluetooth mode never reaches the Fleet API. */
        val fleet: FleetPieces? = secretsStore.fleet()?.takeIf { setup.state.fleetEnabled }?.let { FleetPieces(it, pins) }
        // Bluetooth uses a BLE key, never the Cloud key: one imported from the Mac, else the watch's own.
        val bleLink = secretsStore.importedBleKey()
            ?.let { AndroidBleLink(context, vin, it, PrefsSessionCache(context, "ble"), pins) }
            ?: AndroidBleLink(context, vin, KeystoreEcdhKey(), PrefsSessionCache(context, "ble_keystore"), pins)
        val router get() = CommandRouter(bleLink, fleet?.cloudLink)
        val refresher get() = StatusRefresher(
            bleLink, fleet?.cloudLink, meter, snapshots, onCarConfig = ::applyFleetConfig,
            onCloudError = { Diag.w("Fleet read failed: ${it.message}") },
        )

        private fun applyFleetConfig(config: FleetCarConfig) {
            if (setup.state.profile?.let { it.source == ProfileSource.FLEET && it.model == config.model && it.paint == config.paint && it.wheels == config.wheels } == true) return
            setup.applyFleetProfile(config.model, config.paint, config.wheels)
            notifySurfaces()
        }
    }

    /** Fleet mode with the user's own Developer app; the regional server is found after login. */
    inner class FleetPieces(val secrets: SecretsStore.Fleet, pins: PrefsVehicleKeyPins) {
        val oauth = OAuthClient(http, secrets.clientId, secrets.clientSecret, TeslaConfig.TOKEN_URL.toHttpUrl())
        val tokens = TokenProvider(tokenStore, oauth)
        val fleetApi = FleetApiClient(TeslaConfig.FLEET_REGIONAL_SERVERS.map { it.toHttpUrl() }, tokens, http, meter, regionStore)
        val cloudLink: CloudLink = object : CloudLink {
            override val commands =
                CommandExecutor(
                    secrets.vin, secrets.cloudKey, FleetTransport(fleetApi, secrets.vin), PrefsSessionCache(context, "cloud"),
                    pins = pins, trustedPins = true,
                )
            override val api = fleetApi
            override val vin = secrets.vin
        }
    }
}
