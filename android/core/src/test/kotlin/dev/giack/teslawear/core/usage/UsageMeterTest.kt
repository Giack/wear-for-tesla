package dev.giack.teslawear.core.usage

import dev.giack.teslawear.core.fleet.MemoryUsageStore
import kotlin.test.Test
import kotlin.test.assertEquals

class UsageMeterTest {
    private val oct = 1_792_238_400_000L // 2026-10-17 UTC
    private val nov = 1_793_534_400_000L // 2026-11-01 UTC

    private fun meter(data: Int = 0, commands: Int = 0, wakes: Int = 0, now: Long = oct) =
        UsageMeter(MemoryUsageStore(UsageCounts("2026-10", data, commands, wakes)), nowMs = { now })

    @Test fun usageMeterEstimatesCost() = assertEquals(3.0, meter(data = 500, commands = 1000, wakes = 50).estimateUsd(), 1e-9)

    @Test fun usageMeterLevels() {
        assertEquals(BudgetLevel.OK, meter(data = 3995).level())     // $7.99
        assertEquals(BudgetLevel.WARN, meter(data = 4000).level())   // $8.00
        assertEquals(BudgetLevel.EXHAUSTED, meter(wakes = 500).level()) // $10.00
    }

    @Test fun levelsFollowTheChosenBudget() {
        fun m(budget: Double) = UsageMeter(MemoryUsageStore(UsageCounts("2026-10", 1000, 0, 0)), nowMs = { oct }, budget = { budget })
        assertEquals(BudgetLevel.EXHAUSTED, m(2.0).level()) // $2.00 of $2
        assertEquals(BudgetLevel.WARN, m(2.5).level())      // 80 %
        assertEquals(BudgetLevel.OK, m(10.0).level())
        assertEquals(2.5, m(2.5).budgetUsd())
    }

    @Test fun recordIncrementsAndPersists() {
        val store = MemoryUsageStore()
        val m = UsageMeter(store, nowMs = { oct })
        m.record(UsageKind.DATA); m.record(UsageKind.COMMAND); m.record(UsageKind.WAKE); m.record(UsageKind.WAKE)
        assertEquals(UsageCounts("2026-10", 1, 1, 2), store.counts)
    }

    @Test fun usageMeterResetsOnNewMonth() {
        val m = meter(data = 4000, now = nov)
        assertEquals(0.0, m.estimateUsd())
        m.record(UsageKind.DATA)
        assertEquals(UsageCounts("2026-11", 1, 0, 0), m.counts())
    }
}
