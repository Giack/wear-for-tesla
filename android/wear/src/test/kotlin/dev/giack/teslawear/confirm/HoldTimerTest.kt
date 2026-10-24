package dev.giack.teslawear.confirm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldTimerTest {
    @Test fun holdShorterThanDurationDoesNotConfirm() {
        val t = HoldTimer(600); t.press(0)
        assertFalse(t.release(599))
    }

    @Test fun holdOfDurationConfirms() {
        val t = HoldTimer(600); t.press(0)
        assertTrue(t.release(600))
    }

    @Test fun progressIsClampedFraction() {
        val t = HoldTimer(600)
        assertEquals(0f, t.progress(100), 0f)
        t.press(1_000)
        assertEquals(0.5f, t.progress(1_300), 1e-6f)
        assertEquals(1f, t.progress(5_000), 0f)
    }

    @Test fun releaseResetsProgress() {
        val t = HoldTimer(600); t.press(0); t.release(100)
        assertEquals(0f, t.progress(700), 0f)
    }
}
