package dev.giack.teslawear.protocol

import kotlinx.coroutines.delay

/** What WakingRunner needs from the BLE link: VCSEC sleep state and a command channel. */
interface VehicleSleepProbe {
    /** True if infotainment sleeps, null if unknown. */
    suspend fun isAsleep(): Boolean?
    suspend fun execute(cmd: VehicleCommand): CommandResult
}

/**
 * Over BLE, infotainment commands to a sleeping car get no reply: wake it through VCSEC first
 * (RKE_ACTION_WAKE_VEHICLE, as tesla-control does) and wait until it reports awake.
 */
class WakingRunner(
    private val probe: VehicleSleepProbe,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val polls: Int = 10,
    private val pollMs: Long = 1_000,
) : CommandRunner {
    override suspend fun execute(cmd: VehicleCommand): CommandResult {
        if (cmd.domain == Domain.INFOTAINMENT && probe.isAsleep() == true) {
            val woke = probe.execute(VehicleCommand.WakeVehicle)
            if (woke !is CommandResult.Ok) return woke
            var awake = false
            for (i in 0 until polls) {
                sleep(pollMs)
                if (probe.isAsleep() == false) { awake = true; break }
            }
            if (!awake) return CommandResult.Failed("vehicle did not wake up")
        }
        return probe.execute(cmd)
    }
}
