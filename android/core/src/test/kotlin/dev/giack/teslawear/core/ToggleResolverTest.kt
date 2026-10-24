package dev.giack.teslawear.core

import dev.giack.teslawear.protocol.VehicleCommand
import kotlin.test.Test
import kotlin.test.assertEquals

class ToggleResolverTest {
    private val now = 10L * 24 * 3_600_000

    private fun r(action: QuickAction, s: Snapshot?) = ToggleResolver.resolve(action, s, now)

    @Test fun lockToggleUnlocksWhenLocked() = assertEquals(
        ResolvedAction(VehicleCommand.Unlock, Age.Hours(2)),
        r(QuickAction.LOCK, snap(locked = true, updatedAtMs = now - 2 * 3_600_000)))

    @Test fun lockToggleLocksWhenUnknown() = assertEquals(ResolvedAction(VehicleCommand.Lock, null), r(QuickAction.LOCK, null))

    @Test fun lockToggleLocksWhenUnlocked() = assertEquals<VehicleCommand>(VehicleCommand.Lock, r(QuickAction.LOCK, snap(locked = false, updatedAtMs = now)).command)

    @Test fun toggleTable() {
        val on = snap(climateOn = true, chargePortOpen = true, windowsOpen = true, trunkOpen = true, updatedAtMs = now)
        val off = snap(climateOn = false, chargePortOpen = false, windowsOpen = false, trunkOpen = false, updatedAtMs = now)
        assertEquals(ResolvedAction(VehicleCommand.ClimateOff, Age.Now), r(QuickAction.CLIMATE, on))
        assertEquals(ResolvedAction(VehicleCommand.ClimateOn, Age.Now), r(QuickAction.CLIMATE, off))
        assertEquals(ResolvedAction(VehicleCommand.ChargePortClose, Age.Now), r(QuickAction.CHARGE_PORT, on))
        assertEquals(ResolvedAction(VehicleCommand.ChargePortOpen, Age.Now), r(QuickAction.CHARGE_PORT, off))
        assertEquals(ResolvedAction(VehicleCommand.CloseTrunk, Age.Now), r(QuickAction.TRUNK, on))
        assertEquals(ResolvedAction(VehicleCommand.OpenTrunk, Age.Now), r(QuickAction.TRUNK, off))
        assertEquals(ResolvedAction(VehicleCommand.CloseWindows, Age.Now), r(QuickAction.WINDOWS, on))
        assertEquals(ResolvedAction(VehicleCommand.VentWindows, Age.Now), r(QuickAction.WINDOWS, off))
        assertEquals(ResolvedAction(VehicleCommand.OpenFrunk, Age.Now), r(QuickAction.FRUNK, on))
        assertEquals(ResolvedAction(VehicleCommand.FlashLights, Age.Now), r(QuickAction.LIGHTS, on))
        assertEquals(ResolvedAction(VehicleCommand.Honk, Age.Now), r(QuickAction.HORN, on))
    }

    @Test fun ages() {
        assertEquals(Age.Now, Age.of(now - 30_000, now))
        assertEquals(Age.Minutes(5), Age.of(now - 5 * 60_000, now))
        assertEquals(Age.Hours(2), Age.of(now - 2 * 3_600_000, now))
        assertEquals(Age.Yesterday, Age.of(now - 26 * 3_600_000, now))
        assertEquals(Age.Days(3), Age.of(now - 3 * 24 * 3_600_000, now))
        assertEquals(Age.Now, Age.of(now + 5_000, now))
    }
}
