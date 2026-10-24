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

class FakeBle(var reachable: Boolean = false, var status: Snapshot? = null) : BleLink {
    val runner = FakeRunner()
    var probes = 0
    override suspend fun isReachable(timeoutMs: Long): Boolean { probes++; return reachable }
    override val commands: CommandRunner get() = runner
    override suspend fun readStatus(): Snapshot? = status
}

class FakeApi : VehicleApi {
    var states = ArrayDeque<FleetResult<String>>()
    var data: FleetResult<Snapshot> = FleetResult.Asleep
    var dataCalls = 0
    var wakes = 0
    var failWith: Exception? = null
    override suspend fun vehicleState(vin: String): FleetResult<String> {
        failWith?.let { throw it }
        return if (states.size > 1) states.removeFirst() else states.first()
    }
    /** What vehicleData answers once the car has been woken (null: same as [data]). */
    var dataAfterWake: FleetResult<Snapshot>? = null
    override suspend fun vehicleData(vin: String): FleetResult<Snapshot> {
        failWith?.let { throw it }
        dataCalls++
        return dataAfterWake?.takeIf { wakes > 0 } ?: data
    }
    override suspend fun wakeUp(vin: String): FleetResult<Unit> { wakes++; return FleetResult.Ok(Unit) }
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
    trunkOpen: Boolean? = null, batteryPct: Int? = null, updatedAtMs: Long = 0, channel: Channel = Channel.CLOUD,
) = Snapshot(batteryPct = batteryPct, locked = locked, climateOn = climateOn, chargePortOpen = chargePortOpen,
    windowsOpen = windowsOpen, trunkOpen = trunkOpen, updatedAtMs = updatedAtMs, channel = channel)
