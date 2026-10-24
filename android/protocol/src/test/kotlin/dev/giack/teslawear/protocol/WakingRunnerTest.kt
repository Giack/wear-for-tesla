package dev.giack.teslawear.protocol

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WakingRunnerTest {
    private class FakeVcsec(var asleepReads: Int) : VehicleSleepProbe {
        val sent = mutableListOf<VehicleCommand>()
        override suspend fun isAsleep(): Boolean? = (asleepReads-- > 0)
        override suspend fun execute(cmd: VehicleCommand): CommandResult { sent += cmd; return CommandResult.Ok }
    }

    @Test fun wakesSleepingCarBeforeInfotainmentCommand() = runTest {
        val probe = FakeVcsec(asleepReads = 2)
        val runner = WakingRunner(probe, sleep = {})
        assertEquals(CommandResult.Ok, runner.execute(VehicleCommand.ClimateOn))
        assertEquals(listOf(VehicleCommand.WakeVehicle, VehicleCommand.ClimateOn), probe.sent)
    }

    @Test fun vcsecCommandsDoNotWake() = runTest {
        val probe = FakeVcsec(asleepReads = 5)
        WakingRunner(probe, sleep = {}).execute(VehicleCommand.Lock)
        assertEquals(listOf<VehicleCommand>(VehicleCommand.Lock), probe.sent)
    }

    @Test fun awakeCarIsNotWoken() = runTest {
        val probe = FakeVcsec(asleepReads = 0)
        WakingRunner(probe, sleep = {}).execute(VehicleCommand.Honk)
        assertEquals(listOf<VehicleCommand>(VehicleCommand.Honk), probe.sent)
    }

    @Test fun carThatNeverWakesFailsWithoutSending() = runTest {
        val probe = FakeVcsec(asleepReads = 1_000)
        val result = WakingRunner(probe, sleep = {}).execute(VehicleCommand.ClimateOn)
        assertEquals(CommandResult.Failed("vehicle did not wake up"), result)
        assertEquals(listOf<VehicleCommand>(VehicleCommand.WakeVehicle), probe.sent)
    }
}
