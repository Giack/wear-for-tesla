package dev.giack.teslawear.core

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR

/** How old the known state is, in the steps the watch shows ("5 min ago", "yesterday"); the wording lives in the app. */
sealed interface Age {
    data object Now : Age
    data class Minutes(val count: Int) : Age
    data class Hours(val count: Int) : Age
    data object Yesterday : Age
    data class Days(val count: Int) : Age

    companion object {
        fun of(updatedAtMs: Long, nowMs: Long): Age {
            val age = (nowMs - updatedAtMs).coerceAtLeast(0)
            return when {
                age < MINUTE -> Now
                age < HOUR -> Minutes((age / MINUTE).toInt())
                age < DAY -> Hours((age / HOUR).toInt())
                age < 2 * DAY -> Yesterday
                else -> Days((age / DAY).toInt())
            }
        }
    }
}
