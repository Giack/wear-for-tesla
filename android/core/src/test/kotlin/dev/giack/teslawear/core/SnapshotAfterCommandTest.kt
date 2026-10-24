package dev.giack.teslawear.core

import dev.giack.teslawear.protocol.VehicleCommand
import kotlin.test.Test
import kotlin.test.assertEquals

class SnapshotAfterCommandTest {
    private val base = Snapshot(locked = true, climateOn = false, chargePortOpen = false, windowsOpen = false, trunkOpen = false,
        batteryPct = 78, updatedAtMs = 1, channel = Channel.CLOUD)

    @Test fun commandsUpdateExpectedFields() {
        assertEquals(false, base.afterCommand(VehicleCommand.Unlock, 5, Channel.BLE).locked)
        assertEquals(true, base.afterCommand(VehicleCommand.ClimateOn, 5, Channel.BLE).climateOn)
        assertEquals(true, base.afterCommand(VehicleCommand.ChargePortOpen, 5, Channel.BLE).chargePortOpen)
        assertEquals(true, base.afterCommand(VehicleCommand.VentWindows, 5, Channel.BLE).windowsOpen)
        assertEquals(true, base.afterCommand(VehicleCommand.OpenTrunk, 5, Channel.BLE).trunkOpen)
        assertEquals(21.5f, base.afterCommand(VehicleCommand.SetTemp(21.5f), 5, Channel.BLE).setTempC)
    }

    @Test fun keepsOtherFieldsAndStampsTime() {
        val s = base.afterCommand(VehicleCommand.Lock, 99, Channel.BLE)
        assertEquals(78, s.batteryPct)
        assertEquals(99, s.updatedAtMs)
        assertEquals(Channel.BLE, s.channel)
    }

    @Test fun commandsWithoutStateLeaveSnapshotUnchanged() =
        assertEquals(base, base.afterCommand(VehicleCommand.Honk, 99, Channel.BLE))
}
