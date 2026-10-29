package dev.giack.teslawear.core

import dev.giack.teslawear.core.fleet.AuthRequiredException
import dev.giack.teslawear.core.fleet.FleetRejectedException
import dev.giack.teslawear.core.fleet.FleetResult
import dev.giack.teslawear.core.fleet.VehicleAsleepException
import dev.giack.teslawear.protocol.CommandResult
import dev.giack.teslawear.protocol.TransportUnavailableException
import dev.giack.teslawear.protocol.VehicleCommand
import kotlinx.coroutines.delay
import java.io.IOException

/** Steps of a command worth telling the user about while it runs. */
enum class Progress { WAKING_CAR }

sealed interface RouteOutcome {
    data class Done(val result: CommandResult, val channel: Channel) : RouteOutcome
    data object NoConnection : RouteOutcome
    data object Unreachable : RouteOutcome
    data object AuthRequired : RouteOutcome
    /** Bluetooth mode only: the car is not within Bluetooth range. */
    data object OutOfRange : RouteOutcome
}

/**
 * Sends a command over BLE when the car is near, otherwise over Fleet API (waking it only for the command).
 * Without a Fleet link (Bluetooth mode) the car must be near.
 */
class CommandRouter(
    private val ble: BleLink,
    private val cloud: CloudLink?,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun execute(cmd: VehicleCommand, onProgress: (Progress) -> Unit = {}): RouteOutcome {
        if (ble.isReachable(BLE_PROBE_MS)) {
            try {
                return RouteOutcome.Done(ble.commands.execute(cmd), Channel.BLE)
            } catch (_: TransportUnavailableException) {
                // Link dropped before the command went out: fall back to the cloud.
            }
        }
        val cloud = cloud ?: return RouteOutcome.OutOfRange
        return try {
            when (val ready = ensureAwake(cloud, onProgress)) {
                null -> RouteOutcome.Done(cloud.commands.execute(cmd), Channel.CLOUD)
                else -> ready
            }
        } catch (_: AuthRequiredException) {
            RouteOutcome.AuthRequired
        } catch (e: FleetRejectedException) {
            RouteOutcome.Done(CommandResult.Failed("Fleet API ${e.code}"), Channel.CLOUD)
        } catch (_: VehicleAsleepException) {
            RouteOutcome.Unreachable
        } catch (_: TransportUnavailableException) {
            RouteOutcome.NoConnection
        } catch (_: IOException) {
            RouteOutcome.NoConnection
        }
    }

    /** Returns null once the car is online, or the outcome to report instead. */
    private suspend fun ensureAwake(cloud: CloudLink, onProgress: (Progress) -> Unit): RouteOutcome? {
        when (val state = cloud.api.vehicleState(cloud.vin)) {
            is FleetResult.Ok -> if (state.value == "online") return null
            is FleetResult.Error -> return RouteOutcome.Done(CommandResult.Failed("Fleet API ${state.code}"), Channel.CLOUD)
            FleetResult.Asleep -> Unit
        }
        onProgress(Progress.WAKING_CAR)
        return if (cloud.wakeAndWait(nowMs, sleep)) null else RouteOutcome.Unreachable
    }

    private companion object {
        const val BLE_PROBE_MS = 2_500L
    }
}
