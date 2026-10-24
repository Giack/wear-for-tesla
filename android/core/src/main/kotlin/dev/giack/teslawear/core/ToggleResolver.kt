package dev.giack.teslawear.core

import dev.giack.teslawear.protocol.VehicleCommand

/** Turns a quick action into the concrete command, using the snapshot to pick a toggle's direction. */
object ToggleResolver {
    fun resolve(action: QuickAction, snapshot: Snapshot?, nowMs: Long): ResolvedAction {
        val s = snapshot
        val command = when (action) {
            QuickAction.LOCK -> if (s?.locked == true) VehicleCommand.Unlock else VehicleCommand.Lock
            QuickAction.CLIMATE -> if (s?.climateOn == true) VehicleCommand.ClimateOff else VehicleCommand.ClimateOn
            QuickAction.FRUNK -> VehicleCommand.OpenFrunk
            QuickAction.CHARGE_PORT -> if (s?.chargePortOpen == true) VehicleCommand.ChargePortClose else VehicleCommand.ChargePortOpen
            QuickAction.TRUNK -> if (s?.trunkOpen == true) VehicleCommand.CloseTrunk else VehicleCommand.OpenTrunk
            QuickAction.WINDOWS -> if (s?.windowsOpen == true) VehicleCommand.CloseWindows else VehicleCommand.VentWindows
            QuickAction.LIGHTS -> VehicleCommand.FlashLights
            QuickAction.HORN -> VehicleCommand.Honk
        }
        return ResolvedAction(command, s?.let { Age.of(it.updatedAtMs, nowMs) })
    }
}
