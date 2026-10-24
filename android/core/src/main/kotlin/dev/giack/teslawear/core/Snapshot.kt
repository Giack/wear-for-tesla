package dev.giack.teslawear.core

import dev.giack.teslawear.core.fleet.FleetCarConfig
import dev.giack.teslawear.protocol.VehicleCommand

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
    val windowsOpen: Boolean? = null,
    val trunkOpen: Boolean? = null,
    /** The car was asleep at the last check: the other fields date from before it fell asleep. */
    val asleep: Boolean? = null,
    val updatedAtMs: Long,
    /** Last time the car was asked, asleep answers included (those keep [updatedAtMs]); null if never. */
    val checkedAtMs: Long? = null,
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
        windowsOpen = newer.windowsOpen ?: windowsOpen,
        trunkOpen = newer.trunkOpen ?: trunkOpen,
        asleep = newer.asleep ?: asleep,
        updatedAtMs = newer.updatedAtMs,
        checkedAtMs = newer.checkedAtMs ?: checkedAtMs,
        channel = newer.channel,
    )

    /** When the car was last heard from, data or an asleep answer. */
    val lastCheckMs: Long get() = maxOf(updatedAtMs, checkedAtMs ?: 0)
}

/** Expected state right after [cmd] succeeded, so surfaces stay correct until the next real read. */
fun Snapshot.afterCommand(cmd: VehicleCommand, nowMs: Long, channel: Channel): Snapshot {
    val updated = when (cmd) {
        VehicleCommand.Lock -> copy(locked = true)
        VehicleCommand.Unlock -> copy(locked = false)
        VehicleCommand.ClimateOn -> copy(climateOn = true)
        VehicleCommand.ClimateOff -> copy(climateOn = false)
        VehicleCommand.ChargePortOpen -> copy(chargePortOpen = true)
        VehicleCommand.ChargePortClose -> copy(chargePortOpen = false)
        VehicleCommand.VentWindows -> copy(windowsOpen = true)
        VehicleCommand.CloseWindows -> copy(windowsOpen = false)
        VehicleCommand.OpenTrunk -> copy(trunkOpen = true)
        VehicleCommand.CloseTrunk -> copy(trunkOpen = false)
        is VehicleCommand.SetTemp -> copy(setTempC = cmd.celsius)
        else -> return this
    }
    return updated.copy(updatedAtMs = nowMs, channel = channel)
}
