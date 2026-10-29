package dev.giack.teslawear.core

import dev.giack.teslawear.core.fleet.VehicleApi
import dev.giack.teslawear.protocol.CommandRunner

/** Direct Bluetooth path to the car. */
interface BleLink {
    suspend fun isReachable(timeoutMs: Long): Boolean
    val commands: CommandRunner
    /** Status readable without waking the car; null if nothing could be read. */
    suspend fun readStatus(): Snapshot?
    /**
     * Like [readStatus] but without scanning: connects straight to the car seen last time (scans
     * return nothing in the background). Null if no car is known or it is out of range.
     */
    suspend fun readKnownCarStatus(): Snapshot? = null
}

/** Fleet API path to the car. */
interface CloudLink {
    val commands: CommandRunner
    val api: VehicleApi
    val vin: String
}

interface SnapshotRepository {
    suspend fun load(): Snapshot?
    suspend fun save(s: Snapshot)
}
