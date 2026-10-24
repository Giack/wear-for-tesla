package dev.giack.teslawear.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanBudgetTest {
    private var now = 100_000L
    private val budget = ScanBudget(nowMs = { now })

    private fun startScans(n: Int) = repeat(n) { budget.started(); now += 1_000 }

    @Test fun firstFiveScansStartAtOnce() {
        repeat(5) { assertEquals(0L, budget.delayBefore(3_000)); budget.started() }
    }

    // Starts at 100, 101, 102, 103, 104 s: the oldest leaves the window at 130 s.
    @Test fun sixthScanWaitsForTheOldestToLeaveTheWindow() {
        startScans(5)
        now = 128_000
        assertEquals(2_000L, budget.delayBefore(4_000))
    }

    // Waiting would leave less than a second of scanning: not worth it, the car counts as not seen.
    @Test fun sixthScanIsSkippedWhenTheWaitDoesNotFit() {
        startScans(5)
        assertNull(budget.delayBefore(3_000))
    }

    @Test fun startsOlderThanTheWindowNoLongerCount() {
        startScans(5)
        now = 131_000
        assertEquals(0L, budget.delayBefore(3_000))
    }
}
