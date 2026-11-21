package dev.giack.teslawear.core

import dev.giack.teslawear.core.fleet.AuthRequiredException
import dev.giack.teslawear.core.fleet.FleetRejectedException
import dev.giack.teslawear.core.fleet.FleetResult
import dev.giack.teslawear.core.fleet.VehicleAsleepException
import dev.giack.teslawear.protocol.BleKeyRole
import dev.giack.teslawear.protocol.CommandResult
import dev.giack.teslawear.protocol.TransportUnavailableException
import dev.giack.teslawear.protocol.VehicleCommand
import dev.giack.teslawear.protocol.deniedForRole
import kotlinx.coroutines.delay
import java.io.IOException
import java.io.InterruptedIOException

/** Steps of a command worth telling the user about while it runs. */
enum class Progress { WAKING_CAR }

sealed interface RouteOutcome {
    /** The car was woken for this command, over Bluetooth or with Fleet `wake_up`. Reads never set this. */
    val woke: Boolean get() = false

    data class Done(val result: CommandResult, val channel: Channel, override val woke: Boolean = false) : RouteOutcome
    data object NoConnection : RouteOutcome
    data object Unreachable : RouteOutcome
    data object AuthRequired : RouteOutcome
    /** Bluetooth mode only: the car is not within Bluetooth range. */
    data object OutOfRange : RouteOutcome
    /**
     * The current Bluetooth key's role cannot authorize this command, and Fleet mode is off.
     * Shown as a clear message rather than a protocol fault or a silent no-op.
     */
    data object NeedsFleet : RouteOutcome
}

/**
 * Sends a command over BLE when the car is near, otherwise over Fleet API (waking it only for the command).
 * Without a Fleet link (Bluetooth mode) the car must be near.
 *
 * A Fleet command waits until `state` is `online`. If the watch is in Bluetooth range the wake is the
 * VCSEC wake (free); otherwise it is Fleet `wake_up`. The command is then sent once. HTTP 408 on that
 * command means Tesla did not run it (the car was not online): wake again and send it once more.
 */
class CommandRouter(
    private val ble: BleLink,
    private val cloud: CloudLink?,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    /** Role of the key used over Bluetooth. The watch's own key is a driver key. */
    private val bleRole: BleKeyRole = BleKeyRole.DRIVER,
) {
    suspend fun execute(cmd: VehicleCommand, onProgress: (Progress) -> Unit = {}): RouteOutcome {
        // Bluetooth first, when this key's role can authorize the command. A refusal for role
        // (or a command the role can never send) goes through Fleet mode instead.
        var needsFleet = !cmd.authorizedFor(bleRole)
        var skipBleWake = false
        if (!needsFleet && ble.isReachable(BLE_PROBE_MS)) {
            try {
                val result = ble.commands.execute(cmd)
                if (result.deniedForRole()) needsFleet = true
                else return RouteOutcome.Done(result, Channel.BLE)
            } catch (_: TransportUnavailableException) {
                // Link dropped before the command went out: fall back to the cloud.
            }
        } else if (!needsFleet) {
            // Already probed and the car is not near: do not scan a second time for a BLE wake.
            skipBleWake = true
        }
        if (needsFleet && cloud == null) return RouteOutcome.NeedsFleet
        val cloud = cloud ?: return RouteOutcome.OutOfRange
        return try {
            sendOnceOnline(cloud, cmd, onProgress, skipBleWake, allowAsleepRetry = true)
        } catch (_: AuthRequiredException) {
            RouteOutcome.AuthRequired
        } catch (_: VehicleAsleepException) {
            RouteOutcome.Unreachable
        } catch (_: TransportUnavailableException) {
            RouteOutcome.NoConnection
        } catch (_: IOException) {
            RouteOutcome.NoConnection
        }
    }

    /**
     * Sends [cmd] after the car is online. On HTTP 408, wakes once more and sends it exactly once
     * again. A second 408 is reported as a timeout and is not sent a third time.
     */
    private suspend fun sendOnceOnline(
        cloud: CloudLink,
        cmd: VehicleCommand,
        onProgress: (Progress) -> Unit,
        skipBleWake: Boolean,
        allowAsleepRetry: Boolean,
    ): RouteOutcome {
        val first = ensureAwake(cloud, onProgress, skipBleWake, forceWake = false)
        val woke = when (first) {
            is Awake.Ready -> first.woke
            is Awake.Stop -> return first.outcome
        }
        return try {
            done(cloud, cmd, woke)
        } catch (_: VehicleAsleepException) {
            if (!allowAsleepRetry) return RouteOutcome.Unreachable
            val again = ensureAwake(cloud, onProgress, skipBleWake, forceWake = true)
            val wokeAgain = when (again) {
                is Awake.Ready -> woke || again.woke
                is Awake.Stop -> return again.outcome
            }
            try {
                done(cloud, cmd, wokeAgain)
            } catch (_: VehicleAsleepException) {
                RouteOutcome.Unreachable
            }
        }
    }

    private suspend fun done(cloud: CloudLink, cmd: VehicleCommand, woke: Boolean): RouteOutcome = try {
        RouteOutcome.Done(cloud.commands.execute(cmd), Channel.CLOUD, woke = woke)
    } catch (e: FleetRejectedException) {
        RouteOutcome.Done(CommandResult.Failed("Fleet API ${e.code}"), Channel.CLOUD, woke = woke)
    }

    /** The car is ready for the command, or the attempt stopped. [Awake.Ready.woke] is false when it was already online. */
    private sealed interface Awake {
        data class Ready(val woke: Boolean) : Awake
        data class Stop(val outcome: RouteOutcome) : Awake
    }

    /**
     * Returns [Awake.Ready] once the car is online, or [Awake.Stop] with the outcome to report instead.
     * [forceWake] skips the "already online" short circuit: a 408 can arrive even when `state`
     * just said online (Tesla best practices), so the retry has to wake again.
     */
    private suspend fun ensureAwake(
        cloud: CloudLink,
        onProgress: (Progress) -> Unit,
        skipBleWake: Boolean,
        forceWake: Boolean,
    ): Awake {
        if (!forceWake) {
            val state = try {
                cloud.api.vehicleState(cloud.vin)
            } catch (_: InterruptedIOException) {
                // Same shape as a sleeping car: Tesla held the call open. Wake and poll.
                null
            }
            when (state) {
                is FleetResult.Ok -> if (state.value == "online") return Awake.Ready(woke = false)
                is FleetResult.Error -> return Awake.Stop(RouteOutcome.Done(CommandResult.Failed("Fleet API ${state.code}"), Channel.CLOUD))
                FleetResult.Asleep, null -> Unit
            }
        }
        onProgress(Progress.WAKING_CAR)
        val waited = cloud.wakeAndWait(nowMs, sleep) {
            if (skipBleWake) false else runCatching { ble.wakeIfNear() }.getOrDefault(false)
        }
        return when (waited) {
            WakeWait.Online -> Awake.Ready(woke = true)
            WakeWait.TimedOut -> Awake.Stop(RouteOutcome.Unreachable)
            is WakeWait.Rejected -> Awake.Stop(RouteOutcome.Done(CommandResult.Failed("Fleet API ${waited.code}"), Channel.CLOUD))
        }
    }

    private companion object {
        const val BLE_PROBE_MS = 2_500L
    }
}
