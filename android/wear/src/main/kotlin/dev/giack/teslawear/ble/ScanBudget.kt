package dev.giack.teslawear.ble

/**
 * Android silently returns no results to an app that starts more than [maxStarts] scans in [windowMs],
 * so the car would look out of range. Tracks this app's scan starts to stay within the limit.
 */
class ScanBudget(
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val maxStarts: Int = 5,
    private val windowMs: Long = 30_000,
) {
    private val starts = ArrayDeque<Long>()

    /**
     * How long to wait before a scan of [timeoutMs] may start, or null when the wait would leave
     * less than [MIN_SCAN_MS] of scanning.
     */
    @Synchronized
    fun delayBefore(timeoutMs: Long): Long? {
        val now = nowMs()
        while (starts.isNotEmpty() && now - starts.first() >= windowMs) starts.removeFirst()
        if (starts.size < maxStarts) return 0
        val wait = starts.first() + windowMs - now
        return wait.takeIf { timeoutMs - it >= MIN_SCAN_MS }
    }

    @Synchronized
    fun started() {
        starts.addLast(nowMs())
    }

    companion object {
        const val MIN_SCAN_MS = 1_000L

        /** The limit applies to the whole app, so every scanner shares one budget. */
        val shared = ScanBudget()
    }
}
