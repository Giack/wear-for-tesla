package dev.giack.teslawear.core

import dev.giack.teslawear.core.fleet.FleetResult
import java.io.InterruptedIOException

/**
 * Result of waiting for the car to report `state=online`.
 * Tesla documents that a wake can take 10–60 seconds, and that a command sent before then
 * comes back as HTTP 408 (vehicle unavailable). The wait here is capped at [WAKE_TIMEOUT_MS].
 */
internal sealed interface WakeWait {
    data object Online : WakeWait
    data object TimedOut : WakeWait
    /** Fleet API refused the wake itself (HTTP 4xx other than 408). The command was not sent. */
    data class Rejected(val code: Int) : WakeWait
}

/**
 * Wakes the car, then polls `GET /api/1/vehicles/{vin}` with backoff until `state` is `online`.
 *
 * A Bluetooth VCSEC wake ([preferBleWake] returning true) is used instead of Fleet `wake_up`
 * when the watch is in range, so that wake is not billed. A read timeout on `wake_up` is not
 * fatal: Tesla often holds that call open and the wake may already be underway, so polling
 * continues. Each `wake_up` and each poll goes through [CloudLink.api], which records it.
 *
 * https://developer.tesla.com/docs/fleet-api/getting-started/best-practices
 * https://developer.tesla.com/docs/fleet-api/endpoints/vehicle-endpoints
 */
internal suspend fun CloudLink.wakeAndWait(
    nowMs: () -> Long,
    sleep: suspend (Long) -> Unit,
    timeoutMs: Long = WAKE_TIMEOUT_MS,
    preferBleWake: suspend () -> Boolean = { false },
): WakeWait {
    if (!preferBleWake()) {
        try {
            when (val woke = api.wakeUp(vin)) {
                // 408 is Asleep: the car was not online, which is why we are polling.
                is FleetResult.Error -> if (woke.code in 400..499) return WakeWait.Rejected(woke.code)
                else -> Unit
            }
        } catch (_: InterruptedIOException) {
            // The socket gave up while Tesla was still holding wake_up. Poll anyway.
        }
    }
    return pollUntilOnline(nowMs, sleep, timeoutMs)
}

private suspend fun CloudLink.pollUntilOnline(
    nowMs: () -> Long,
    sleep: suspend (Long) -> Unit,
    timeoutMs: Long,
): WakeWait {
    val deadline = nowMs() + timeoutMs
    var wait = WAKE_POLL_INITIAL_MS
    while (true) {
        val remaining = deadline - nowMs()
        if (remaining <= 0) return WakeWait.TimedOut
        sleep(minOf(wait, remaining))
        val state = try {
            api.vehicleState(vin)
        } catch (_: InterruptedIOException) {
            null
        }
        when (state) {
            is FleetResult.Ok -> if (state.value == "online") return WakeWait.Online
            is FleetResult.Error -> if (state.code in 400..499) return WakeWait.Rejected(state.code)
            FleetResult.Asleep, null -> Unit
        }
        wait = minOf(wait * 2, WAKE_POLL_MAX_MS)
    }
}

/** Tesla says 10–60 s; the watch stops at the upper end of the requested bound. */
internal const val WAKE_TIMEOUT_MS = 45_000L
internal const val WAKE_POLL_INITIAL_MS = 1_000L
internal const val WAKE_POLL_MAX_MS = 8_000L
