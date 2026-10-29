package dev.giack.teslawear.core

import dev.giack.teslawear.core.fleet.FleetCarConfig
import dev.giack.teslawear.core.fleet.FleetResult
import dev.giack.teslawear.core.usage.BudgetLevel
import dev.giack.teslawear.core.usage.UsageMeter
import dev.giack.teslawear.protocol.TransportUnavailableException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

enum class RefreshTrigger {
    APP_OPEN, AFTER_COMMAND, TILE_VIEW, CONFIRM_SCREEN,
    /** The user asked for fresh data: the only refresh that may wake the car. */
    MANUAL,
    /** Periodic check while the app is closed: Bluetooth to the known car, else one Fleet read, never a wake. */
    BACKGROUND,
}

/**
 * Refresh policy: Tile and confirm screen only read over BLE (free; the Tile and the background check
 * connect to the known car without scanning); app open and post-command
 * may read over Fleet API unless the monthly budget is exhausted (app open only when the state is not fresh). Only a manual refresh wakes the car.
 * Fleet API has no data for a sleeping car: the stored snapshot is kept and marked asleep.
 * Without a Fleet link (Bluetooth mode) only Bluetooth is read.
 * AuthRequiredException propagates so the UI can ask for login.
 */
class StatusRefresher(
    private val ble: BleLink,
    private val cloud: CloudLink?,
    private val meter: UsageMeter,
    private val store: SnapshotRepository,
    private val nowMs: () -> Long = System::currentTimeMillis,
    /** Receives the car's configuration whenever a Fleet read carries it (feeds the car profile). */
    private val onCarConfig: (FleetCarConfig) -> Unit = {},
    /** Told why a Fleet read gave nothing (the refresh itself falls back to the last known state). */
    private val onCloudError: (Exception) -> Unit = {},
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun refresh(trigger: RefreshTrigger): Snapshot? {
        val current = store.load()
        val fresh: Snapshot? = when (trigger) {
            // The Tile is a service: Android rejects its scans (location is foreground-only), so it connects directly.
            RefreshTrigger.TILE_VIEW ->
                if (current != null && nowMs() - current.updatedAtMs < TILE_STALE_MS) null else knownCarViaBle()
            RefreshTrigger.CONFIRM_SCREEN -> withTimeoutOrNull(CONFIRM_BUDGET_MS) { viaBle(CONFIRM_BUDGET_MS) }
            RefreshTrigger.APP_OPEN ->
                viaBle(BLE_PROBE_MS) ?: if (current != null && nowMs() - current.lastCheckMs < APP_OPEN_FRESH_MS) null else viaCloud(current)
            RefreshTrigger.AFTER_COMMAND -> viaBle(BLE_PROBE_MS) ?: viaCloud(current)
            RefreshTrigger.MANUAL -> viaBle(MANUAL_BLE_PROBE_MS) ?: viaCloud(current, wake = true)
            RefreshTrigger.BACKGROUND -> knownCarViaBle()
                ?: if (current != null && nowMs() - current.lastCheckMs < BACKGROUND_FRESH_MS) null else viaCloud(current)
        }
        if (fresh == null) return current
        val merged = current?.mergedWith(fresh) ?: fresh
        store.save(merged)
        return merged
    }

    private suspend fun viaBle(probeMs: Long): Snapshot? = try {
        if (ble.isReachable(probeMs)) ble.readStatus() else null
    } catch (_: TransportUnavailableException) {
        null
    }

    private suspend fun knownCarViaBle(): Snapshot? = try {
        ble.readKnownCarStatus()
    } catch (_: TransportUnavailableException) {
        null
    }

    private suspend fun viaCloud(current: Snapshot?, wake: Boolean = false): Snapshot? {
        val cloud = cloud ?: return null
        if (meter.level() == BudgetLevel.EXHAUSTED) return null
        return try {
            when (val result = cloud.api.vehicleData(cloud.vin)) {
                is FleetResult.Ok -> result.value.also { it.carConfig?.let(onCarConfig) }
                is FleetResult.Error -> null.also { onCloudError(IOException("Fleet API ${result.code}: ${result.message}")) }
                FleetResult.Asleep ->
                    if (wake && cloud.wakeAndWait(nowMs, sleep)) viaCloud(current) else current?.copy(asleep = true, checkedAtMs = nowMs())
            }
        } catch (e: IOException) {
            onCloudError(e)
            null
        }
    }

    private companion object {
        const val TILE_STALE_MS = 5 * 60_000L
        const val APP_OPEN_FRESH_MS = 2 * 60_000L
        const val BACKGROUND_FRESH_MS = 25 * 60_000L
        const val BLE_PROBE_MS = 2_500L
        const val MANUAL_BLE_PROBE_MS = 4_000L
        const val CONFIRM_BUDGET_MS = 1_500L
    }
}
