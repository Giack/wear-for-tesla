package dev.giack.teslawear.core

import dev.giack.teslawear.core.fleet.FleetResult
import dev.giack.teslawear.core.fleet.VehicleApi
import dev.giack.teslawear.protocol.CommandResult
import dev.giack.teslawear.protocol.CommandRunner
import dev.giack.teslawear.protocol.VehicleCommand

class FakeRunner(var result: () -> CommandResult = { CommandResult.Ok }) : CommandRunner {
    val executed = mutableListOf<VehicleCommand>()
    override suspend fun execute(cmd: VehicleCommand): CommandResult { executed += cmd; return result() }
}

class FakeBle(
    var reachable: Boolean = false,
    var status: Snapshot? = null,
    var known: Snapshot? = null,
    /** When true, [wakeIfNear] sends [VehicleCommand.WakeVehicle] and reports success. */
    var canWakeOverBle: Boolean = false,
) : BleLink {
    val runner = FakeRunner()
    var probes = 0
    var bleWakes = 0
    override suspend fun isReachable(timeoutMs: Long): Boolean { probes++; return reachable }
    override suspend fun wakeIfNear(): Boolean {
        if (!canWakeOverBle) return false
        bleWakes++
        runner.execute(VehicleCommand.WakeVehicle)
        return true
    }
    override val commands: CommandRunner get() = runner
    override suspend fun readStatus(): Snapshot? = status
    override suspend fun readKnownCarStatus(): Snapshot? = known
}

class FakeApi : VehicleApi {
    var states = ArrayDeque<FleetResult<String>>()
    var data: FleetResult<Snapshot> = FleetResult.Asleep
    var dataCalls = 0
    var stateReads = 0
    var wakes = 0
    /** Answered in order by [vehicleData]; empty falls through to [dataAfterWake] or [data]. */
    var scripted = ArrayDeque<FleetResult<Snapshot>>()
    /** Runs after a [vehicleData] call is counted, so a test can record it on the usage meter. */
    var onData: () -> Unit = {}
    var failWith: Exception? = null
    /** Thrown from the next [vehicleState] calls, one each, without failing the rest. */
    var stateFailures = ArrayDeque<Exception>()
    /** Thrown from [wakeUp] after the call is counted (a timed-out wake is still a wake). */
    var wakeFailure: Exception? = null
    var wakeResult: FleetResult<Unit> = FleetResult.Ok(Unit)
    override suspend fun vehicleState(vin: String): FleetResult<String> {
        stateReads++
        failWith?.let { throw it }
        stateFailures.removeFirstOrNull()?.let { throw it }
        return if (states.size > 1) states.removeFirst() else states.first()
    }
    /** What vehicleData answers once the car has been woken (null: same as [data]). */
    var dataAfterWake: FleetResult<Snapshot>? = null
    override suspend fun vehicleData(vin: String): FleetResult<Snapshot> {
        failWith?.let { throw it }
        dataCalls++
        onData()
        scripted.removeFirstOrNull()?.let { return it }
        return dataAfterWake?.takeIf { wakes > 0 } ?: data
    }
    override suspend fun wakeUp(vin: String): FleetResult<Unit> {
        wakes++
        wakeFailure?.let { throw it }
        return wakeResult
    }
}

class FakeCloud(override val api: FakeApi = FakeApi()) : CloudLink {
    val runner = FakeRunner()
    override val commands: CommandRunner get() = runner
    override val vin = "XP7YGCEK0SB000000"
}

class MemorySnapshots(var snapshot: Snapshot? = null) : SnapshotRepository {
    override suspend fun load() = snapshot
    override suspend fun save(s: Snapshot) { snapshot = s }
}

fun snap(
    locked: Boolean? = null, climateOn: Boolean? = null, chargePortOpen: Boolean? = null, windowsOpen: Boolean? = null,
    trunkOpen: Boolean? = null, batteryPct: Int? = null, charging: Boolean? = null, sentryOn: Boolean? = null,
    updatedAtMs: Long = 0, channel: Channel = Channel.CLOUD,
) = Snapshot(batteryPct = batteryPct, locked = locked, climateOn = climateOn, chargePortOpen = chargePortOpen,
    windowsOpen = windowsOpen, trunkOpen = trunkOpen, charging = charging, sentryOn = sentryOn,
    updatedAtMs = updatedAtMs, channel = channel)
