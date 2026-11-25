package dev.giack.teslawear.core

import dev.giack.teslawear.core.fleet.FleetResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComplicationReadTest {
    @Test fun readReturnsTheStoredSnapshotAndDoesNotCallFleetOrWake() = runTest {
        val stored = snap(
            locked = true, climateOn = false, sentryOn = true, batteryPct = 70,
            updatedAtMs = 1_000, channel = Channel.BLE,
        )
        val store = MemorySnapshots(stored)
        // Fresh answers sitting on the links. A complication must not pick them up.
        val ble = FakeBle(
            reachable = true, canWakeOverBle = true,
            status = snap(locked = false, batteryPct = 1, updatedAtMs = 9_000),
        )
        val cloud = FakeCloud().apply {
            api.states += FleetResult.Ok("online")
            api.data = FleetResult.Ok(snap(locked = false, batteryPct = 2, updatedAtMs = 9_000))
        }

        assertEquals(stored, readComplicationSnapshot(store))
        assertEquals(stored, store.snapshot)
        assertEquals(0, cloud.api.wakes)
        assertEquals(0, cloud.api.dataCalls)
        assertEquals(0, cloud.api.stateReads)
        assertEquals(0, ble.bleWakes)
        assertEquals(0, ble.probes)
        assertTrue(cloud.runner.executed.isEmpty())
        assertTrue(ble.runner.executed.isEmpty())
    }

    @Test fun complicationCommandsAreLockClimateAndSentry() {
        assertEquals(listOf(QuickAction.LOCK, QuickAction.CLIMATE, QuickAction.SENTRY), COMPLICATION_COMMANDS)
    }
}
