package dev.giack.teslawear.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagLogTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun log(maxLines: Int = 5) = DiagLog(tmp.root.resolve("diag.log"), maxLines) { 0L }

    /** Entries carry a time prefix; the tests look at the message after it. */
    private fun DiagLog.messages(n: Int = 100) = tail(n).map { it.substringAfter(" | ") }

    @Test fun linesSurviveANewInstance() {
        log().add("scan: found")
        assertEquals(listOf("scan: found"), log().messages())
    }

    @Test fun keepsOnlyTheNewestLines() {
        val l = log(maxLines = 3)
        (1..10).forEach { l.add("line $it") }
        assertEquals(listOf("line 8", "line 9", "line 10"), l.messages())
    }

    @Test fun tailReturnsTheLastN() {
        val l = log()
        (1..4).forEach { l.add("line $it") }
        assertEquals(listOf("line 3", "line 4"), l.messages(2))
    }

    @Test fun multiLineMessagesStayOneEntry() {
        val l = log()
        l.add("error\ncaused by x")
        assertEquals(listOf("error ⏎ caused by x"), l.messages())
    }

    @Test fun entriesAreTimestamped() {
        log().add("x")
        assertTrue(log().tail(1).single().matches(Regex("""\d\d:\d\d:\d\d \| x""")))
    }
}
