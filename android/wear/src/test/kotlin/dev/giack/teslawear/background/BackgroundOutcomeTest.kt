package dev.giack.teslawear.background

import dev.giack.teslawear.core.Channel
import dev.giack.teslawear.core.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundOutcomeTest {
    private val old = Snapshot(batteryPct = 50, updatedAtMs = 1_000, channel = Channel.CLOUD)

    @Test fun freshBluetoothRead() =
        assertEquals("read over Bluetooth", backgroundOutcome(old, old.copy(updatedAtMs = 2_000, channel = Channel.BLE)))

    @Test fun freshCloudRead() =
        assertEquals("read over Fleet API", backgroundOutcome(old, old.copy(updatedAtMs = 2_000)))

    @Test fun carFoundAsleep() =
        assertEquals("car asleep, not woken", backgroundOutcome(old, old.copy(asleep = true, checkedAtMs = 2_000)))

    @Test fun nothingNew() = assertEquals("not near, cloud skipped", backgroundOutcome(old, old))

    @Test fun nothingAtAll() = assertEquals("not near, cloud skipped", backgroundOutcome(null, null))
}
