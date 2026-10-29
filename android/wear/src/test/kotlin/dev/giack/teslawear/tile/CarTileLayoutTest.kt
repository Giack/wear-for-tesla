package dev.giack.teslawear.tile

import dev.giack.teslawear.R
import dev.giack.teslawear.core.Channel
import dev.giack.teslawear.core.QuickAction
import dev.giack.teslawear.core.Snapshot
import dev.giack.teslawear.core.setup.CarModel
import dev.giack.teslawear.core.usage.BudgetLevel
import dev.giack.teslawear.ui.UiText
import dev.giack.teslawear.ui.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarTileLayoutTest {
    @Test fun tileModelForLockedCar() {
        val s = Snapshot(batteryPct = 78, rangeKm = 312, insideTempC = 19.4f, locked = true, climateOn = false,
            chargePortOpen = true, frunkOpen = true, updatedAtMs = 0, channel = Channel.BLE)
        val m = buildTileModel(s, nowMs = 60_000, meterLevel = BudgetLevel.OK)
        assertEquals(R.string.model_y, m.title)
        assertEquals(R.string.channel_ble, m.chip)
        assertEquals(UiText.Raw("78% · 312 km · 19°"), m.line)
        assertEquals(
            listOf(
                TileButton(QuickAction.LOCK, R.string.tile_locked, active = true),
                TileButton(QuickAction.CLIMATE, R.string.action_climate, active = false),
                TileButton(QuickAction.FRUNK, R.string.action_frunk, active = true),
                TileButton(QuickAction.CHARGE_PORT, R.string.tile_port, active = true),
            ),
            m.buttons,
        )
    }

    @Test fun tileModelForUnlockedCloudCar() {
        val s = Snapshot(batteryPct = 50, locked = false, updatedAtMs = 0, channel = Channel.CLOUD)
        val m = buildTileModel(s, nowMs = 0, meterLevel = BudgetLevel.OK)
        assertEquals(R.string.channel_cloud, m.chip)
        assertEquals(UiText.Raw("50%"), m.line)
        assertEquals(TileButton(QuickAction.LOCK, R.string.tile_unlocked, active = false), m.buttons.first())
    }

    @Test fun tileTitleFollowsTheCarModel() {
        assertEquals(R.string.model_3, buildTileModel(null, nowMs = 0, meterLevel = BudgetLevel.OK, model = CarModel.MODEL_3).title)
        assertEquals(R.string.model_y, buildTileModel(null, nowMs = 0, meterLevel = BudgetLevel.OK, model = null).title)
    }

    @Test fun tileModelWithoutSnapshot() {
        val m = buildTileModel(null, nowMs = 0, meterLevel = BudgetLevel.OK)
        assertEquals(text(R.string.tile_open_app), m.line)
        assertEquals(null, m.chip)
        assertTrue(m.buttons.isNotEmpty())
    }
}
