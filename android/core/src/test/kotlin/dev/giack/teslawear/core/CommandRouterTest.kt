package dev.giack.teslawear.core

import dev.giack.teslawear.core.fleet.AuthRequiredException
import dev.giack.teslawear.core.fleet.FleetRejectedException
import dev.giack.teslawear.core.fleet.FleetResult
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
