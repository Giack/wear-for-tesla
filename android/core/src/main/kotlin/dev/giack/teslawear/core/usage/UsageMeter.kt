package dev.giack.teslawear.core.usage

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

enum class UsageKind { DATA, COMMAND, WAKE }

enum class BudgetLevel { OK, WARN, EXHAUSTED }

data class UsageCounts(val month: String, val data: Int, val commands: Int, val wakes: Int)

interface UsageStore {
    fun load(): UsageCounts?
    fun save(c: UsageCounts)
}

/** Tracks Fleet API usage against the user's monthly budget (default: Tesla's $10/month discount). */
class UsageMeter(
    private val store: UsageStore,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val budget: () -> Double = { DEFAULT_BUDGET_USD },
) {
    @Synchronized
    fun record(kind: UsageKind) {
        val c = counts()
        store.save(
            when (kind) {
                UsageKind.DATA -> c.copy(data = c.data + 1)
                UsageKind.COMMAND -> c.copy(commands = c.commands + 1)
                UsageKind.WAKE -> c.copy(wakes = c.wakes + 1)
            },
        )
    }

    @Synchronized
    fun counts(): UsageCounts {
        val month = MONTH.format(Instant.ofEpochMilli(nowMs()))
        val stored = store.load()
        return if (stored?.month == month) stored else UsageCounts(month, 0, 0, 0)
    }

    fun estimateUsd(): Double = counts().let { it.data * DATA_USD + it.commands * COMMAND_USD + it.wakes * WAKE_USD }

    fun budgetUsd(): Double = budget()

    fun level(): BudgetLevel {
        val usd = estimateUsd() + EPSILON
        val limit = budgetUsd()
        return when {
            usd >= limit -> BudgetLevel.EXHAUSTED
            usd >= limit * WARN_FRACTION -> BudgetLevel.WARN
            else -> BudgetLevel.OK
        }
    }

    companion object {
        const val DEFAULT_BUDGET_USD = 10.0
        /** Budgets the settings screen cycles through. */
        val BUDGET_CHOICES = listOf(2.0, 5.0, 10.0, 20.0, 50.0)
        const val WARN_FRACTION = 0.8
        const val DATA_USD = 0.002
        const val COMMAND_USD = 0.001
        const val WAKE_USD = 0.02
        private const val EPSILON = 1e-9
        private val MONTH = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC)
    }
}
