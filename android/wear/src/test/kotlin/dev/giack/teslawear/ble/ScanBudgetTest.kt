package dev.giack.teslawear.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanBudgetTest {
    private var now = 100_000L
    private val budget = ScanBudget(nowMs = { now })

    private fun startScans(n: Int) = repeat(n) { budget.reserve(3_000); now += 1_000 }

    @Test fun firstFiveScansStartAtOnce() {
        repeat(5) { assertEquals(0L, budget.reserve(3_000)) }
    }

    // Seen on the watch: Home, Tile and resume asked together and all passed a check-then-record budget.
    @Test fun simultaneousRequestsCannotOverbook() {
        repeat(5) { assertEquals(0L, budget.reserve(3_000)) }
        assertNull(budget.reserve(3_000))
    }

    // Starts at 100, 101, 102, 103, 104 s: the oldest leaves the window at 130 s.
    @Test fun sixthScanWaitsForTheOldestToLeaveTheWindow() {
        startScans(5)
        now = 128_000
        assertEquals(2_000L, budget.reserve(4_000))
    }

    // A reserved wait holds the slot: the next caller must wait for the following one (101 s + 30).
    @Test fun aReservedWaitCountsAsAStart() {
        startScans(5)
        now = 128_000
        assertEquals(2_000L, budget.reserve(4_000))
        assertEquals(3_000L, budget.reserve(4_000))
        assertNull(budget.reserve(4_000))
    }

    // Waiting would leave less than a second of scanning: not worth it, the car counts as not seen.
    @Test fun sixthScanIsSkippedWhenTheWaitDoesNotFit() {
        startScans(5)
        assertNull(budget.reserve(3_000))
    }

    @Test fun startsOlderThanTheWindowNoLongerCount() {
        startScans(5)
        now = 131_000
        assertEquals(0L, budget.reserve(3_000))
    }
}
