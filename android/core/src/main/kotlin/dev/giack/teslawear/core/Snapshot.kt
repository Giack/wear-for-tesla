package dev.giack.teslawear.core

import dev.giack.teslawear.core.fleet.FleetCarConfig
import dev.giack.teslawear.protocol.CommandResult
import dev.giack.teslawear.protocol.VehicleCommand
import kotlin.math.abs

enum class Channel { BLE, CLOUD }

/** Last known vehicle status. Null fields are unknown. */
data class Snapshot(
    val batteryPct: Int? = null,
    val rangeKm: Int? = null,
    val locked: Boolean? = null,
    val climateOn: Boolean? = null,
    val insideTempC: Float? = null,
    val setTempC: Float? = null,
    val chargePortOpen: Boolean? = null,
    val charging: Boolean? = null,
    /** Sentry mode is armed (any state other than off). Null when the car has not reported it. */
    val sentryOn: Boolean? = null,
    val windowsOpen: Boolean? = null,
    val trunkOpen: Boolean? = null,
    val frunkOpen: Boolean? = null,
    /** The car was asleep at the last check: the other fields date from before it fell asleep. */
    val asleep: Boolean? = null,
    val updatedAtMs: Long,
    /** Last time the car was asked, asleep answers included (those keep [updatedAtMs]); null if never. */
    val checkedAtMs: Long? = null,
    /**
     * When a wake or a command last succeeded. Opening the app treats data from before this as stale
     * for two minutes, without waking the car. Cleared once a later read is trusted.
     */
    val actedAtMs: Long? = null,
    /**
     * State change still waiting to show up in a read (`unlock`, `temp:21.5`, …). Null when nothing is pending.
     * Kept across reads that still show the old value, so a stale `vehicle_data` cannot undo the command.
     */
    val pendingCommand: String? = null,
    val channel: Channel,
    /** Configuration read through Fleet mode; not persisted (the Setup state keeps the car profile). */
    val carConfig: FleetCarConfig? = null,
) {
    /** Fields known in [newer] replace ours; unknown ones keep the previous value. */
    fun mergedWith(newer: Snapshot): Snapshot = Snapshot(
        batteryPct = newer.batteryPct ?: batteryPct,
        rangeKm = newer.rangeKm ?: rangeKm,
        locked = newer.locked ?: locked,
        climateOn = newer.climateOn ?: climateOn,
        insideTempC = newer.insideTempC ?: insideTempC,
        setTempC = newer.setTempC ?: setTempC,
        chargePortOpen = newer.chargePortOpen ?: chargePortOpen,
        charging = newer.charging ?: charging,
        sentryOn = newer.sentryOn ?: sentryOn,
        windowsOpen = newer.windowsOpen ?: windowsOpen,
        trunkOpen = newer.trunkOpen ?: trunkOpen,
        frunkOpen = newer.frunkOpen ?: frunkOpen,
        asleep = newer.asleep ?: asleep,
        updatedAtMs = newer.updatedAtMs,
        checkedAtMs = newer.checkedAtMs ?: checkedAtMs,
        actedAtMs = newer.actedAtMs ?: actedAtMs,
        pendingCommand = newer.pendingCommand ?: pendingCommand,
        channel = newer.channel,
    )

    /** When the car was last heard from, data or an asleep answer. */
    val lastCheckMs: Long get() = maxOf(updatedAtMs, checkedAtMs ?: 0)
}

/**
 * Expected state right after [cmd] succeeded, so surfaces stay correct until a read confirms it.
 * [updatedAtMs] is left alone: "Updated … ago" stays the time of the last real read.
 * The car is no longer marked asleep, and [pendingCommand] remembers what the next read should show.
 */
fun Snapshot.afterCommand(cmd: VehicleCommand, nowMs: Long, channel: Channel): Snapshot {
    val code = cmd.stateCode() ?: return this
    return withExpected(code).copy(asleep = false, actedAtMs = nowMs, pendingCommand = code, channel = channel)
}

/** A wake succeeded. Clears [asleep] without pretending a new state read happened. */
fun Snapshot.noteAwake(nowMs: Long): Snapshot =
    if (asleep == false && actedAtMs != null) this else copy(asleep = false, actedAtMs = actedAtMs ?: nowMs)

/** Optimistic state for a finished command, including a wake that did not change a field. */
fun Snapshot.afterOutcome(cmd: VehicleCommand, outcome: RouteOutcome, nowMs: Long): Snapshot {
    val done = outcome as? RouteOutcome.Done
    val updated = if (done?.result == CommandResult.Ok) afterCommand(cmd, nowMs, done.channel) else this
    return if (outcome.woke) updated.noteAwake(nowMs) else updated
}

/** True when this read already shows the effect of [cmd]. Commands with no state field always match. */
fun Snapshot.reflects(cmd: VehicleCommand): Boolean = cmd.stateCode()?.let(::reflectsCode) ?: true

/** True when this read shows the effect recorded in [code] (see [VehicleCommand.stateCode]). */
fun Snapshot.reflectsCode(code: String): Boolean = expectation(code)?.matches?.invoke(this) ?: true

/** Puts the expected field for [code] back, leaving the read's timestamp and the other fields as they are. */
fun Snapshot.withExpected(code: String): Snapshot = expectation(code)?.apply?.invoke(this) ?: this

/**
 * Stable id of the field [this] changes, or null for a command that only acts (horn, lights, door, wake).
 * Persisted on [Snapshot.pendingCommand].
 */
fun VehicleCommand.stateCode(): String? = when (this) {
    VehicleCommand.Lock -> "lock"
    VehicleCommand.Unlock -> "unlock"
    VehicleCommand.ClimateOn -> "climate"
    VehicleCommand.ClimateOff -> "climate-off"
    VehicleCommand.ChargePortOpen -> "port"
    VehicleCommand.ChargePortClose -> "port-off"
    VehicleCommand.VentWindows -> "windows"
    VehicleCommand.CloseWindows -> "windows-off"
    VehicleCommand.OpenTrunk -> "trunk"
    VehicleCommand.CloseTrunk -> "trunk-off"
    VehicleCommand.OpenFrunk -> "frunk"
    VehicleCommand.SentryOn -> "sentry"
    VehicleCommand.SentryOff -> "sentry-off"
    VehicleCommand.ChargeStart -> "charge"
    VehicleCommand.ChargeStop -> "charge-off"
    is VehicleCommand.SetTemp -> "temp:$celsius"
    else -> null
}

private data class Expectation(val matches: (Snapshot) -> Boolean, val apply: (Snapshot) -> Snapshot)

private fun expectation(code: String): Expectation? = when (code) {
    "lock" -> Expectation({ it.locked == true }, { it.copy(locked = true) })
    "unlock" -> Expectation({ it.locked == false }, { it.copy(locked = false) })
    "climate" -> Expectation({ it.climateOn == true }, { it.copy(climateOn = true) })
    "climate-off" -> Expectation({ it.climateOn == false }, { it.copy(climateOn = false) })
    "port" -> Expectation({ it.chargePortOpen == true }, { it.copy(chargePortOpen = true) })
    "port-off" -> Expectation({ it.chargePortOpen == false }, { it.copy(chargePortOpen = false) })
    "windows" -> Expectation({ it.windowsOpen == true }, { it.copy(windowsOpen = true) })
    "windows-off" -> Expectation({ it.windowsOpen == false }, { it.copy(windowsOpen = false) })
    "trunk" -> Expectation({ it.trunkOpen == true }, { it.copy(trunkOpen = true) })
    "trunk-off" -> Expectation({ it.trunkOpen == false }, { it.copy(trunkOpen = false) })
    "frunk" -> Expectation({ it.frunkOpen == true }, { it.copy(frunkOpen = true) })
    "sentry" -> Expectation({ it.sentryOn == true }, { it.copy(sentryOn = true) })
    "sentry-off" -> Expectation({ it.sentryOn == false }, { it.copy(sentryOn = false) })
    "charge" -> Expectation({ it.charging == true }, { it.copy(charging = true) })
    "charge-off" -> Expectation({ it.charging == false }, { it.copy(charging = false) })
    else -> code.removePrefix("temp:").toFloatOrNull()?.takeIf { code.startsWith("temp:") }?.let { want ->
        Expectation(
            { s -> s.setTempC != null && abs(s.setTempC - want) < 0.05f },
            { it.copy(setTempC = want) },
        )
    }
}
