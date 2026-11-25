package dev.giack.teslawear.core

import dev.giack.teslawear.protocol.VehicleCommand

/**
 * User-facing actions; toggles are resolved against the last known state.
 * [requiresFleet] is the driver door: the watch's BLE key is a driver key, and the car refuses
 * that command for the role, so it only runs in Fleet mode.
 */
enum class QuickAction {
    LOCK, CLIMATE, FRUNK, CHARGE_PORT, TRUNK, WINDOWS, LIGHTS, HORN, SENTRY, CHARGING, UNLATCH_DOOR;

    val requiresFleet: Boolean get() = this == UNLATCH_DOOR
}

/** The command a quick action stands for now, and how old the state that chose it is (null: unknown). */
data class ResolvedAction(val command: VehicleCommand, val age: Age?)
