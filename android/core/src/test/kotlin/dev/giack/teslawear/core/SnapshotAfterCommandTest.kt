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
        assertEquals(true, base.afterCommand(VehicleCommand.OpenFrunk, 5, Channel.BLE).frunkOpen)
        assertEquals(21.5f, base.afterCommand(VehicleCommand.SetTemp(21.5f), 5, Channel.BLE).setTempC)
        assertEquals(true, base.afterCommand(VehicleCommand.SentryOn, 5, Channel.BLE).sentryOn)
        assertEquals(false, base.copy(sentryOn = true).afterCommand(VehicleCommand.SentryOff, 5, Channel.BLE).sentryOn)
        assertEquals(true, base.afterCommand(VehicleCommand.ChargeStart, 5, Channel.BLE).charging)
        assertEquals(false, base.copy(charging = true).afterCommand(VehicleCommand.ChargeStop, 5, Channel.BLE).charging)
    }

    @Test fun keepsOtherFieldsAndTheTimeOfTheLastRead() {
        val asleep = base.copy(asleep = true)
        val s = asleep.afterCommand(VehicleCommand.Lock, 99, Channel.BLE)
        assertEquals(78, s.batteryPct)
        assertEquals(1, s.updatedAtMs)
        assertEquals(99, s.actedAtMs)
        assertEquals(false, s.asleep)
        assertEquals("lock", s.pendingCommand)
        assertEquals(Channel.BLE, s.channel)
        assertEquals(true, s.reflects(VehicleCommand.Lock))
    }

    @Test fun aStaleReadDoesNotCountAsReflectingTheCommand() {
        assertEquals(false, base.reflects(VehicleCommand.Unlock))
        assertEquals(true, base.copy(locked = false).reflects(VehicleCommand.Unlock))
        assertEquals(true, base.copy(setTempC = 21.5f).reflects(VehicleCommand.SetTemp(21.5f)))
    }

    @Test fun commandsWithoutStateLeaveSnapshotUnchanged() {
        assertEquals(base, base.afterCommand(VehicleCommand.Honk, 99, Channel.BLE))
        assertEquals(base, base.afterCommand(VehicleCommand.UnlatchDriverDoor, 99, Channel.BLE))
    }
}
