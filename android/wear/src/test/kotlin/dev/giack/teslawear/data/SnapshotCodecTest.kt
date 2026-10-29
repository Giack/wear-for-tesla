package dev.giack.teslawear.data

import dev.giack.teslawear.core.Channel
import dev.giack.teslawear.core.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SnapshotCodecTest {
    @Test fun roundTripsAllFields() {
        val s = Snapshot(
            batteryPct = 78, rangeKm = 312, locked = true, climateOn = false, insideTempC = 19.5f, setTempC = 21.5f,
            chargePortOpen = false, charging = true, windowsOpen = false, trunkOpen = true, frunkOpen = true, asleep = true,
            updatedAtMs = 1_792_238_400_000L, checkedAtMs = 1_792_238_500_000L, channel = Channel.BLE,
        )
        assertEquals(s, SnapshotCodec.decode(SnapshotCodec.encode(s)))
    }

    @Test fun decodesNullFields() {
        val s = Snapshot(updatedAtMs = 5, channel = Channel.CLOUD)
        val decoded = SnapshotCodec.decode(SnapshotCodec.encode(s))!!
        assertNull(decoded.batteryPct)
        assertNull(decoded.locked)
        assertEquals(s, decoded)
    }

    @Test fun garbageDecodesToNull() = assertNull(SnapshotCodec.decode("not json"))
}
