package dev.giack.teslawear.core

import dev.giack.teslawear.core.fleet.VehicleApi
import dev.giack.teslawear.protocol.CommandRunner

/** Direct Bluetooth path to the car. */
interface BleLink {
    suspend fun isReachable(timeoutMs: Long): Boolean

    /**
     * Wakes infotainment over VCSEC (`RKE_ACTION_WAKE_VEHICLE`) when the watch is connected
     * or in range. False when Bluetooth cannot reach the car, so the caller uses Fleet `wake_up`.
     * Links that do not implement it never pretend the car was woken.
     */
    suspend fun wakeIfNear(): Boolean = false

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
