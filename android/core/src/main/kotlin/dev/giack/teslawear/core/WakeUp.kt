package dev.giack.teslawear.core

import dev.giack.teslawear.core.fleet.FleetResult

/** Wakes the car through Fleet API and polls until it reports online; false if it did not wake in time. */
internal suspend fun CloudLink.wakeAndWait(
    nowMs: () -> Long,
    sleep: suspend (Long) -> Unit,
    timeoutMs: Long = 45_000,
    pollMs: Long = 3_000,
): Boolean {
    api.wakeUp(vin)
    val deadline = nowMs() + timeoutMs
    while (nowMs() < deadline) {
        sleep(pollMs)
        val state = api.vehicleState(vin)
        if (state is FleetResult.Ok && state.value == "online") return true
    }
    return false
}
