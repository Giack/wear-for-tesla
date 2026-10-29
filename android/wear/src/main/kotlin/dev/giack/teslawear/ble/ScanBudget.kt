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
     * Books a scan of [timeoutMs]: returns how long to wait before starting it, or null (nothing booked)
     * when the wait would leave less than [MIN_SCAN_MS] of scanning. Checking and booking are one step,
     * so callers asking at the same moment cannot overbook.
     */
    @Synchronized
    fun reserve(timeoutMs: Long): Long? {
        val now = nowMs()
        while (starts.isNotEmpty() && now - starts.first() >= windowMs) starts.removeFirst()
        val wait = if (starts.size < maxStarts) 0 else starts.first() + windowMs - now
        if (timeoutMs - wait < MIN_SCAN_MS) return null
        if (starts.size >= maxStarts) starts.removeFirst()
        starts.addLast(now + wait)
        return wait
    }

    companion object {
        const val MIN_SCAN_MS = 1_000L

        /** The limit applies to the whole app, so every scanner shares one budget. */
        val shared = ScanBudget()
    }
}
