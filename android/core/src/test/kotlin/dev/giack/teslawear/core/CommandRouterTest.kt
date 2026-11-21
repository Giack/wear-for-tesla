package dev.giack.teslawear.core

import dev.giack.teslawear.core.fleet.AuthRequiredException
import dev.giack.teslawear.core.fleet.FleetRejectedException
import dev.giack.teslawear.core.fleet.FleetResult
import dev.giack.teslawear.core.fleet.VehicleAsleepException
import dev.giack.teslawear.protocol.BleKeyRole
import dev.giack.teslawear.protocol.CommandResult
import dev.giack.teslawear.protocol.TransportUnavailableException
import dev.giack.teslawear.protocol.VehicleCommand
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.net.SocketTimeoutException
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
        assertEquals(RouteOutcome.Done(CommandResult.Ok, Channel.CLOUD, woke = true), outcome)
        assertEquals(1, cloud.api.wakes)
        assertEquals(listOf(Progress.WAKING_CAR), progress)
    }

    @Test fun routerReportsUnreachableAfterWakeTimeout() = runTest {
        val cloud = FakeCloud().apply { api.states += FleetResult.Ok("asleep") }
        assertEquals(RouteOutcome.Unreachable, router(FakeBle(), cloud).execute(VehicleCommand.ClimateOn))
        // Backoff poll, capped at 45s: one wake, the state checks, and no command.
        assertEquals(45_000, clock)
        assertEquals(1, cloud.api.wakes)
        assertEquals(9, cloud.api.stateReads)
        assertTrue(cloud.runner.executed.isEmpty())
    }

    @Test fun routerWakesOfflineCarThenSendsOnce() = runTest {
        val cloud = FakeCloud().apply {
            api.states += FleetResult.Ok("offline"); api.states += FleetResult.Ok("offline"); api.states += FleetResult.Ok("online")
        }
        assertEquals(RouteOutcome.Done(CommandResult.Ok, Channel.CLOUD, woke = true), router(FakeBle(), cloud).execute(VehicleCommand.Lock))
        assertEquals(listOf<VehicleCommand>(VehicleCommand.Lock), cloud.runner.executed)
        assertEquals(1, cloud.api.wakes)
    }

    @Test fun privilegesFaultOnAsleepCarWakesOverBleThenSendsOnce() = runTest {
        val ble = FakeBle(reachable = true, canWakeOverBle = true).apply {
            var calls = 0
            runner.result = {
                calls++
                // The user's command is refused for this key's role; the VCSEC wake itself succeeds.
                if (calls == 1) CommandResult.Failed("MESSAGEFAULT_ERROR_INSUFFICIENT_PRIVILEGES") else CommandResult.Ok
            }
        }
        val cloud = FakeCloud().apply {
            api.states += FleetResult.Ok("asleep"); api.states += FleetResult.Ok("online")
        }
        val progress = mutableListOf<Progress>()
        val outcome = router(ble, cloud).execute(VehicleCommand.SentryOn) { progress += it }
        assertEquals(RouteOutcome.Done(CommandResult.Ok, Channel.CLOUD, woke = true), outcome)
        assertEquals(listOf(Progress.WAKING_CAR), progress)
        assertEquals(1, ble.bleWakes)
        assertEquals(listOf(VehicleCommand.SentryOn, VehicleCommand.WakeVehicle), ble.runner.executed)
        assertEquals(0, cloud.api.wakes)
        assertEquals(listOf<VehicleCommand>(VehicleCommand.SentryOn), cloud.runner.executed)
    }

    @Test fun doorUnlatchInRangeWakesOverBleThenSendsOnce() = runTest {
        val ble = FakeBle(reachable = true, canWakeOverBle = true)
        val cloud = FakeCloud().apply {
            api.states += FleetResult.Ok("asleep"); api.states += FleetResult.Ok("online")
        }
        assertEquals(RouteOutcome.Done(CommandResult.Ok, Channel.CLOUD, woke = true), router(ble, cloud).execute(VehicleCommand.UnlatchDriverDoor))
        assertEquals(listOf<VehicleCommand>(VehicleCommand.WakeVehicle), ble.runner.executed)
        assertEquals(0, cloud.api.wakes)
        assertEquals(listOf<VehicleCommand>(VehicleCommand.UnlatchDriverDoor), cloud.runner.executed)
    }

    @Test fun wakeReadTimeoutStillPollsAndSendsOnce() = runTest {
        val cloud = FakeCloud().apply {
            api.states += FleetResult.Ok("asleep"); api.states += FleetResult.Ok("online")
            api.wakeFailure = SocketTimeoutException("read timed out")
        }
        assertEquals(RouteOutcome.Done(CommandResult.Ok, Channel.CLOUD, woke = true), router(FakeBle(), cloud).execute(VehicleCommand.Honk))
        assertEquals(1, cloud.api.wakes)
        assertEquals(listOf<VehicleCommand>(VehicleCommand.Honk), cloud.runner.executed)
    }

    @Test fun stateReadTimeoutWakesInsteadOfNoConnection() = runTest {
        val cloud = FakeCloud().apply {
            api.stateFailures += SocketTimeoutException("read timed out")
            api.states += FleetResult.Ok("online")
        }
        assertEquals(RouteOutcome.Done(CommandResult.Ok, Channel.CLOUD, woke = true), router(FakeBle(), cloud).execute(VehicleCommand.Lock))
        assertEquals(1, cloud.api.wakes)
        assertEquals(listOf<VehicleCommand>(VehicleCommand.Lock), cloud.runner.executed)
    }

    @Test fun command408WakesAndRetriesOnce() = runTest {
        val cloud = FakeCloud().apply { api.states += FleetResult.Ok("online") }
        var attempts = 0
        cloud.runner.result = {
            attempts++
            if (attempts == 1) throw VehicleAsleepException() else CommandResult.Ok
        }
        val progress = mutableListOf<Progress>()
        val outcome = router(FakeBle(), cloud).execute(VehicleCommand.Lock) { progress += it }
        assertEquals(RouteOutcome.Done(CommandResult.Ok, Channel.CLOUD, woke = true), outcome)
        assertEquals(listOf<VehicleCommand>(VehicleCommand.Lock, VehicleCommand.Lock), cloud.runner.executed)
        assertEquals(1, cloud.api.wakes)
        assertEquals(listOf(Progress.WAKING_CAR), progress)
    }

    @Test fun command408TwiceDoesNotSendAThirdTime() = runTest {
        val cloud = FakeCloud().apply { api.states += FleetResult.Ok("online") }
        cloud.runner.result = { throw VehicleAsleepException() }
        assertEquals(RouteOutcome.Unreachable, router(FakeBle(), cloud).execute(VehicleCommand.Honk))
        assertEquals(listOf<VehicleCommand>(VehicleCommand.Honk, VehicleCommand.Honk), cloud.runner.executed)
        assertEquals(1, cloud.api.wakes)
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
