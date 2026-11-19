package dev.giack.teslawear.core

import dev.giack.teslawear.core.fleet.AuthRequiredException
import dev.giack.teslawear.core.fleet.FleetRejectedException
import dev.giack.teslawear.core.fleet.FleetResult
import dev.giack.teslawear.protocol.BleKeyRole
import dev.giack.teslawear.protocol.CommandResult
import dev.giack.teslawear.protocol.TransportUnavailableException
import dev.giack.teslawear.protocol.VehicleCommand
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandRouterTest {
    private var clock = 0L
    private fun router(ble: FakeBle, cloud: FakeCloud) = CommandRouter(ble, cloud, nowMs = { clock }, sleep = { clock += it })

    @Test fun routerPrefersBle() = runTest {
        val ble = FakeBle(reachable = true); val cloud = FakeCloud()
        assertEquals(RouteOutcome.Done(CommandResult.Ok, Channel.BLE), router(ble, cloud).execute(VehicleCommand.Lock))
        assertEquals(listOf<VehicleCommand>(VehicleCommand.Lock), ble.runner.executed)
        assertTrue(cloud.runner.executed.isEmpty())
    }

    @Test fun routerFallsBackToCloudWhenBleLinkDrops() = runTest {
        val ble = FakeBle(reachable = true).apply { runner.result = { throw TransportUnavailableException("gatt") } }
        val cloud = FakeCloud().apply { api.states += FleetResult.Ok("online") }
        assertEquals(RouteOutcome.Done(CommandResult.Ok, Channel.CLOUD), router(ble, cloud).execute(VehicleCommand.Lock))
    }

    @Test fun routerSendsOnlineCloudWithoutWake() = runTest {
        val cloud = FakeCloud().apply { api.states += FleetResult.Ok("online") }
        assertEquals(RouteOutcome.Done(CommandResult.Ok, Channel.CLOUD), router(FakeBle(), cloud).execute(VehicleCommand.ClimateOn))
        assertEquals(0, cloud.api.wakes)
    }

    @Test fun routerWakesThenSendsOnCloudWhenAsleep() = runTest {
        val cloud = FakeCloud().apply {
            api.states += FleetResult.Ok("asleep"); api.states += FleetResult.Ok("asleep"); api.states += FleetResult.Ok("online")
        }
        val progress = mutableListOf<Progress>()
        val outcome = router(FakeBle(), cloud).execute(VehicleCommand.ClimateOn) { progress += it }
        assertEquals(RouteOutcome.Done(CommandResult.Ok, Channel.CLOUD), outcome)
        assertEquals(1, cloud.api.wakes)
        assertEquals(listOf(Progress.WAKING_CAR), progress)
    }

    @Test fun routerReportsUnreachableAfterWakeTimeout() = runTest {
        val cloud = FakeCloud().apply { api.states += FleetResult.Ok("asleep") }
        assertEquals(RouteOutcome.Unreachable, router(FakeBle(), cloud).execute(VehicleCommand.ClimateOn))
        assertTrue(clock >= 45_000)
        assertTrue(cloud.runner.executed.isEmpty())
    }

    @Test fun routerFailsCleanlyWithNoChannel() = runTest {
        val cloud = FakeCloud().apply { api.failWith = IOException("offline") }
        assertEquals(RouteOutcome.NoConnection, router(FakeBle(), cloud).execute(VehicleCommand.Lock))
    }

    @Test fun routerReportsAuthRequired() = runTest {
        val cloud = FakeCloud().apply { api.failWith = AuthRequiredException() }
        assertEquals(RouteOutcome.AuthRequired, router(FakeBle(), cloud).execute(VehicleCommand.Lock))
    }

    @Test fun sentryAndChargingPreferBluetooth() = runTest {
        val ble = FakeBle(reachable = true)
        val cloud = FakeCloud()
        assertEquals(RouteOutcome.Done(CommandResult.Ok, Channel.BLE), router(ble, cloud).execute(VehicleCommand.SentryOn))
        assertEquals(RouteOutcome.Done(CommandResult.Ok, Channel.BLE), router(ble, cloud).execute(VehicleCommand.ChargeStart))
        assertEquals(RouteOutcome.Done(CommandResult.Ok, Channel.BLE), router(ble, cloud).execute(VehicleCommand.ChargeStop))
        assertEquals(listOf<VehicleCommand>(VehicleCommand.SentryOn, VehicleCommand.ChargeStart, VehicleCommand.ChargeStop), ble.runner.executed)
        assertTrue(cloud.runner.executed.isEmpty())
    }

    @Test fun doorUnlatchSkipsBluetoothAndUsesFleet() = runTest {
        val ble = FakeBle(reachable = true)
        val cloud = FakeCloud().apply { api.states += FleetResult.Ok("online") }
        assertEquals(RouteOutcome.Done(CommandResult.Ok, Channel.CLOUD), router(ble, cloud).execute(VehicleCommand.UnlatchDriverDoor))
        assertTrue(ble.runner.executed.isEmpty())
        assertEquals(0, ble.probes)
        assertEquals(listOf<VehicleCommand>(VehicleCommand.UnlatchDriverDoor), cloud.runner.executed)
    }

    @Test fun doorUnlatchWithoutFleetSaysSo() = runTest {
        val ble = FakeBle(reachable = true)
        assertEquals(RouteOutcome.NeedsFleet, CommandRouter(ble, cloud = null).execute(VehicleCommand.UnlatchDriverDoor))
        assertTrue(ble.runner.executed.isEmpty())
    }

    @Test fun ownerKeyUnlatchesOverBluetooth() = runTest {
        val ble = FakeBle(reachable = true)
        val router = CommandRouter(ble, FakeCloud(), bleRole = BleKeyRole.OWNER, nowMs = { clock }, sleep = { clock += it })
        assertEquals(RouteOutcome.Done(CommandResult.Ok, Channel.BLE), router.execute(VehicleCommand.UnlatchDriverDoor))
        assertEquals(listOf<VehicleCommand>(VehicleCommand.UnlatchDriverDoor), ble.runner.executed)
    }

    @Test fun privilegesFaultFallsBackToFleet() = runTest {
        val ble = FakeBle(reachable = true).apply {
            runner.result = { CommandResult.Failed("MESSAGEFAULT_ERROR_INSUFFICIENT_PRIVILEGES") }
        }
        val cloud = FakeCloud().apply { api.states += FleetResult.Ok("online") }
        assertEquals(RouteOutcome.Done(CommandResult.Ok, Channel.CLOUD), router(ble, cloud).execute(VehicleCommand.SentryOff))
        assertEquals(listOf<VehicleCommand>(VehicleCommand.SentryOff), cloud.runner.executed)
    }

    @Test fun privilegesFaultWithoutFleetSaysSo() = runTest {
        val ble = FakeBle(reachable = true).apply {
            runner.result = { CommandResult.Failed("MESSAGEFAULT_ERROR_INSUFFICIENT_PRIVILEGES") }
        }
        assertEquals(RouteOutcome.NeedsFleet, CommandRouter(ble, cloud = null).execute(VehicleCommand.ChargeStart))
    }

    @Test fun ordinaryBluetoothFailureIsNotRetriedOnFleet() = runTest {
        val ble = FakeBle(reachable = true).apply { runner.result = { CommandResult.Failed("not_charging") } }
        val cloud = FakeCloud()
        assertEquals(
            RouteOutcome.Done(CommandResult.Failed("not_charging"), Channel.BLE),
            router(ble, cloud).execute(VehicleCommand.ChargeStop),
        )
        assertTrue(cloud.runner.executed.isEmpty())
    }

    @Test fun commandRejectedByFleetApiNamesTheCode() = runTest {
        val cloud = FakeCloud().apply {
            api.states += FleetResult.Ok("online")
            runner.result = { throw FleetRejectedException(429) }
        }
        assertEquals(
            RouteOutcome.Done(CommandResult.Failed("Fleet API 429"), Channel.CLOUD),
            router(FakeBle(), cloud).execute(VehicleCommand.Lock),
        )
    }
}
