package dev.giack.teslawear.core

import dev.giack.teslawear.core.fleet.FleetCarConfig
import dev.giack.teslawear.core.fleet.FleetResult
import dev.giack.teslawear.core.usage.BudgetLevel
import dev.giack.teslawear.core.usage.UsageMeter
import dev.giack.teslawear.protocol.TransportUnavailableException
import dev.giack.teslawear.protocol.VehicleCommand
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
 * What [RefreshTrigger.AFTER_COMMAND] may chase. Extra reads happen only when [fleetOrWoken] is true
 * and [command] changes a field. They never call `wake_up` or the Bluetooth wake.
 */
data class CommandFollowUp(
    val command: VehicleCommand,
    /** The command went over Fleet, or the car was just woken. A nearby Bluetooth command needs one read. */
    val fleetOrWoken: Boolean,
)

/**
 * Refresh policy: Tile and confirm screen only read over BLE (free; the Tile and the background check
 * connect to the known car without scanning); app open and post-command
 * may read over Fleet API unless the monthly budget is exhausted (app open only when the state is not fresh).
 * Only a manual refresh wakes the car. A follow-up after a Fleet command or a wake reads `vehicle_data`
 * at most twice more (about 3s, then about 8s) and stops when the changed field matches, the budget is used up,
 * or there is nothing more to ask. Those reads prefer Bluetooth when the car is in range, and they never wake it.
 * Fleet API has no data for a sleeping car: the stored snapshot is kept and marked asleep, except in the
 * post-command follow-up, where a 408 means "not ready yet" and the expected state stays on screen.
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
    suspend fun refresh(
        trigger: RefreshTrigger,
        followUp: CommandFollowUp? = null,
        onUpdate: (Snapshot) -> Unit = {},
    ): Snapshot? {
        val current = store.load()
        if (trigger == RefreshTrigger.AFTER_COMMAND) return readAfterCommand(current, followUp, onUpdate)
        val read: Snapshot? = when (trigger) {
            // The Tile is a service: Android rejects its scans (location is foreground-only), so it connects directly.
            RefreshTrigger.TILE_VIEW ->
                if (current != null && nowMs() - current.updatedAtMs < TILE_STALE_MS) null else knownCarViaBle()
            RefreshTrigger.CONFIRM_SCREEN -> withTimeoutOrNull(CONFIRM_BUDGET_MS) { viaBle(CONFIRM_BUDGET_MS) }
            RefreshTrigger.APP_OPEN ->
                viaBle(BLE_PROBE_MS) ?: if (current != null && appOpenIsFresh(current)) null else viaCloud(current)
            RefreshTrigger.MANUAL -> viaBle(MANUAL_BLE_PROBE_MS) ?: viaCloud(current, wake = true)
            RefreshTrigger.BACKGROUND -> knownCarViaBle()
                ?: if (current != null && nowMs() - current.lastCheckMs < BACKGROUND_FRESH_MS) null else viaCloud(current)
            RefreshTrigger.AFTER_COMMAND -> null
        }
        if (read == null) return current
        return publish(current, read, onUpdate)
    }

    /**
     * One read, then — only after a Fleet command or a wake — at most two more.
     * A 408 in that window does not wake the car and does not put the asleep flag back:
     * the command already ran, and Tesla often still answers 408 for a few seconds.
     */
    private suspend fun readAfterCommand(
        current: Snapshot?,
        followUp: CommandFollowUp?,
        onUpdate: (Snapshot) -> Unit,
    ): Snapshot? {
        val chase = followUp?.takeIf { it.fleetOrWoken && it.command.stateCode() != null }
        var latest = current
        // True once this attempt should be the last (matched, budget, error, or a single read was enough).
        suspend fun take(hit: CloudHit): Boolean = when (hit) {
            is CloudHit.Blocked, is CloudHit.Empty -> true
            is CloudHit.Asleep -> false
            is CloudHit.Data -> {
                latest = publish(latest, hit.snapshot, onUpdate)
                chase == null || (hit.snapshot.asleep != true && hit.snapshot.reflects(chase.command))
            }
        }
        if (take(readPreferringBle(preserveAwake = chase != null))) return latest
        if (chase == null) return latest
        for (wait in FOLLOW_UP_DELAYS_MS) {
            sleep(wait)
            if (take(readPreferringBle(preserveAwake = true))) return latest
        }
        return latest
    }

    /** Opening the app skips a billed read when the stored state is recent, unless a wake or command is still newer than that data. */
    private fun appOpenIsFresh(current: Snapshot): Boolean {
        if (nowMs() - current.lastCheckMs >= APP_OPEN_FRESH_MS) return false
        // Already asked a sleeping car moments ago: another read would 408 and bill again.
        if (current.asleep == true) return true
        val acted = current.actedAtMs ?: return true
        if (nowMs() - acted >= APP_OPEN_FRESH_MS) return true
        if (current.pendingCommand != null) return false
        return current.updatedAtMs >= acted
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

    /** Bluetooth when the car answers; otherwise one Fleet read. Never wakes. */
    private suspend fun readPreferringBle(preserveAwake: Boolean): CloudHit {
        viaBle(BLE_PROBE_MS)?.let { return CloudHit.Data(it) }
        return cloudRead(wake = false, preserveAwake = preserveAwake)
    }

    private suspend fun viaCloud(current: Snapshot?, wake: Boolean = false): Snapshot? = when (
        val hit = cloudRead(wake = wake, preserveAwake = false)
    ) {
        is CloudHit.Data -> hit.snapshot
        else -> if (!wake && hit is CloudHit.Asleep) current?.copy(asleep = true, checkedAtMs = nowMs()) else null
    }

    private suspend fun cloudRead(wake: Boolean, preserveAwake: Boolean): CloudHit {
        val cloud = cloud ?: return CloudHit.Empty
        if (meter.level() == BudgetLevel.EXHAUSTED) return CloudHit.Blocked
        val current = store.load()
        return try {
            when (val result = cloud.api.vehicleData(cloud.vin)) {
                is FleetResult.Ok -> CloudHit.Data(result.value.also { it.carConfig?.let(onCarConfig) })
                is FleetResult.Error -> CloudHit.Empty.also {
                    onCloudError(IOException("Fleet API ${result.code}: ${result.message}"))
                }
                FleetResult.Asleep -> when {
                    // Post-command 408: the car was just awake enough to take the command. Do not wake, do not mark asleep.
                    preserveAwake -> CloudHit.Asleep
                    !wake -> markAsleep(current)
                    cloud.wakeAndWait(nowMs, sleep) == WakeWait.Online -> cloudRead(wake = false, preserveAwake = false)
                    else -> markAsleep(current)
                }
            }
        } catch (e: IOException) {
            onCloudError(e)
            CloudHit.Empty
        }
    }

    private fun markAsleep(current: Snapshot?): CloudHit =
        current?.copy(asleep = true, checkedAtMs = nowMs())?.let { CloudHit.Data(it) } ?: CloudHit.Empty

    private suspend fun publish(current: Snapshot?, read: Snapshot, onUpdate: (Snapshot) -> Unit): Snapshot {
        val merged = current?.mergedWith(read) ?: read
        val code = current?.pendingCommand
        val reflects = code != null && read.reflectsCode(code)
        val settled = when {
            // A read that shows the new value is the one that clears the pending command.
            reflects && read.asleep != true -> merged.copy(pendingCommand = null, actedAtMs = null, asleep = false)
            // Still the old value, even if the read also says asleep: keep what the command set, and don't put the flag back.
            code != null && !reflects -> merged.withExpected(code).copy(
                pendingCommand = code,
                actedAtMs = current.actedAtMs,
                asleep = false,
            )
            current?.actedAtMs != null && current.pendingCommand == null && read.asleep != true &&
                read.updatedAtMs >= current.actedAtMs -> merged.copy(actedAtMs = null)
            else -> merged
        }
        store.save(settled)
        onUpdate(settled)
        return settled
    }

    private sealed interface CloudHit {
        data class Data(val snapshot: Snapshot) : CloudHit
        /** HTTP 408 during a post-command follow-up: keep the stored state and do not wake. */
        data object Asleep : CloudHit
        data object Blocked : CloudHit
        data object Empty : CloudHit
    }

    private companion object {
        const val TILE_STALE_MS = 5 * 60_000L
        const val APP_OPEN_FRESH_MS = 2 * 60_000L
        const val BACKGROUND_FRESH_MS = 25 * 60_000L
        const val BLE_PROBE_MS = 2_500L
        const val MANUAL_BLE_PROBE_MS = 4_000L
        const val CONFIRM_BUDGET_MS = 1_500L
        val FOLLOW_UP_DELAYS_MS = longArrayOf(3_000L, 8_000L)
    }
}
