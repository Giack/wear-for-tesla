package dev.giack.teslawear.core

import dev.giack.teslawear.protocol.VehicleCommand

/** User-facing actions; toggles are resolved against the last known state. */
enum class QuickAction { LOCK, CLIMATE, FRUNK, CHARGE_PORT, TRUNK, WINDOWS, LIGHTS, HORN }

/** The command a quick action stands for now, and how old the state that chose it is (null: unknown). */
data class ResolvedAction(val command: VehicleCommand, val age: Age?)
