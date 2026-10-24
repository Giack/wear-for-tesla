package dev.giack.teslawear.confirm

/** Press-and-hold bookkeeping: a command fires only after [durationMs] of continuous pressure. */
class HoldTimer(private val durationMs: Long = 600) {
    private var pressedAt: Long? = null

    fun press(atMs: Long) { pressedAt = atMs }

    /** Ends the press; true if it lasted the full duration. */
    fun release(atMs: Long): Boolean {
        val start = pressedAt ?: return false
        pressedAt = null
        return atMs - start >= durationMs
    }

    fun progress(atMs: Long): Float {
        val start = pressedAt ?: return 0f
        return ((atMs - start).toFloat() / durationMs).coerceIn(0f, 1f)
    }
}
