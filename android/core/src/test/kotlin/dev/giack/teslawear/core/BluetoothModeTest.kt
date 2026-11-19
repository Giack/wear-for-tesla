package dev.giack.teslawear.core

import dev.giack.teslawear.core.fleet.MemoryUsageStore
import dev.giack.teslawear.core.usage.UsageMeter
import dev.giack.teslawear.protocol.CommandResult
import dev.giack.teslawear.protocol.TransportUnavailableException
import dev.giack.teslawear.protocol.VehicleCommand
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Bluetooth mode: no Fleet link at all. */
class BluetoothModeTest {
    private val meter = UsageMeter(MemoryUsageStore())

    @Test fun commandGoesOverBluetoothWhenTheCarIsNear() = runTest {
        val ble = FakeBle(reachable = true)
        assertEquals(RouteOutcome.Done(CommandResult.Ok, Channel.BLE), CommandRouter(ble, cloud = null).execute(VehicleCommand.Lock))
        assertEquals(listOf<VehicleCommand>(VehicleCommand.Lock), ble.runner.executed)
    }

    @Test fun carOutOfBluetoothRangeIsReportedAsSuch() = runTest {
        assertEquals(RouteOutcome.OutOfRange, CommandRouter(FakeBle(reachable = false), cloud = null).execute(VehicleCommand.Lock))
    }

    @Test fun sentryAndChargingStayOnBluetooth() = runTest {
        val ble = FakeBle(reachable = true)
        val router = CommandRouter(ble, cloud = null)
        assertEquals(RouteOutcome.Done(CommandResult.Ok, Channel.BLE), router.execute(VehicleCommand.SentryOn))
        assertEquals(RouteOutcome.Done(CommandResult.Ok, Channel.BLE), router.execute(VehicleCommand.ChargeStop))
        assertEquals(listOf<VehicleCommand>(VehicleCommand.SentryOn, VehicleCommand.ChargeStop), ble.runner.executed)
    }

    @Test fun doorUnlatchInBluetoothModeAsksForFleet() = runTest {
        assertEquals(RouteOutcome.NeedsFleet, CommandRouter(FakeBle(reachable = true), cloud = null).execute(VehicleCommand.UnlatchDriverDoor))
        assertEquals(RouteOutcome.NeedsFleet, CommandRouter(FakeBle(reachable = false), cloud = null).execute(VehicleCommand.UnlatchDriverDoor))
    }

    @Test fun droppedBluetoothLinkIsReportedAsOutOfRange() = runTest {
        val ble = FakeBle(reachable = true).apply { runner.result = { throw TransportUnavailableException("gatt") } }
        assertEquals(RouteOutcome.OutOfRange, CommandRouter(ble, cloud = null).execute(VehicleCommand.Lock))
    }

    @Test fun openingTheAppFarFromTheCarKeepsTheLastKnownState() = runTest {
        val store = MemorySnapshots(snap(batteryPct = 50))
        val result = StatusRefresher(FakeBle(reachable = false), cloud = null, meter, store).refresh(RefreshTrigger.APP_OPEN)
        assertEquals(50, result?.batteryPct)
    }

    @Test fun openingTheAppNearTheCarReadsItOverBluetooth() = runTest {
        val store = MemorySnapshots(snap(batteryPct = 50, locked = false))
        val ble = FakeBle(reachable = true, status = snap(locked = true, channel = Channel.BLE))
        val result = StatusRefresher(ble, cloud = null, meter, store).refresh(RefreshTrigger.APP_OPEN)
        assertEquals(true, result?.locked)
        assertEquals(50, result?.batteryPct)
    }
}
