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

    @Test fun defaultTileLayoutIsLockClimateFrunkPort() {
        val m = buildTileModel(null, nowMs = 0, meterLevel = BudgetLevel.OK)
        assertEquals(
            listOf(QuickAction.LOCK, QuickAction.CLIMATE, QuickAction.FRUNK, QuickAction.CHARGE_PORT),
            m.buttons.map { it.action },
        )
        assertTrue(m.buttons.none { it.fleetOnly })
    }

    @Test fun tileLayoutForOneCommand() {
        val m = buildTileModel(null, nowMs = 0, meterLevel = BudgetLevel.OK, commands = listOf(QuickAction.HORN))
        assertEquals(listOf(TileButton(QuickAction.HORN, R.string.action_horn, active = false)), m.buttons)
    }

    @Test fun tileLayoutForTwoCommandsKeepsOrder() {
        val s = Snapshot(locked = false, climateOn = true, updatedAtMs = 0, channel = Channel.BLE)
        val m = buildTileModel(s, nowMs = 0, meterLevel = BudgetLevel.OK, commands = listOf(QuickAction.CLIMATE, QuickAction.LOCK))
        assertEquals(
            listOf(
                TileButton(QuickAction.CLIMATE, R.string.action_climate, active = true),
                TileButton(QuickAction.LOCK, R.string.tile_unlocked, active = false),
            ),
            m.buttons,
        )
    }

    @Test fun tileLayoutForThreeCommands() {
        val s = Snapshot(trunkOpen = true, windowsOpen = false, sentryOn = true, updatedAtMs = 0, channel = Channel.BLE)
        val m = buildTileModel(
            s, nowMs = 0, meterLevel = BudgetLevel.OK,
            commands = listOf(QuickAction.TRUNK, QuickAction.WINDOWS, QuickAction.SENTRY),
        )
        assertEquals(
            listOf(
                TileButton(QuickAction.TRUNK, R.string.action_trunk, active = true),
                TileButton(QuickAction.WINDOWS, R.string.action_windows, active = false),
                TileButton(QuickAction.SENTRY, R.string.action_sentry, active = true),
            ),
            m.buttons,
        )
    }

    @Test fun tileLayoutForFourCommandsMarksFleetOnly() {
        val s = Snapshot(charging = true, updatedAtMs = 0, channel = Channel.CLOUD)
        val m = buildTileModel(
            s, nowMs = 0, meterLevel = BudgetLevel.OK,
            commands = listOf(QuickAction.CHARGING, QuickAction.LIGHTS, QuickAction.UNLATCH_DOOR, QuickAction.HORN),
        )
        assertEquals(4, m.buttons.size)
        assertEquals(
            listOf(
                TileButton(QuickAction.CHARGING, R.string.action_charging, active = true),
                TileButton(QuickAction.LIGHTS, R.string.action_lights, active = false),
                TileButton(QuickAction.UNLATCH_DOOR, R.string.action_door, active = false, fleetOnly = true),
                TileButton(QuickAction.HORN, R.string.action_horn, active = false),
            ),
            m.buttons,
        )
    }

    @Test fun tileLayoutDropsAnythingPastFour() {
        val m = buildTileModel(
            null, nowMs = 0, meterLevel = BudgetLevel.OK,
            commands = listOf(QuickAction.LOCK, QuickAction.CLIMATE, QuickAction.FRUNK, QuickAction.CHARGE_PORT, QuickAction.HORN),
        )
        assertEquals(4, m.buttons.size)
        assertTrue(m.buttons.none { it.action == QuickAction.HORN })
    }

    @Test fun everyCommandHasATileIcon() {
        val registered = TileIds.icons.map { it.first }.toSet()
        QuickAction.entries.forEach { action ->
            assertTrue(action.name, TileIds.icon(action, locked = true) in registered)
            assertTrue(action.name, TileIds.icon(action, locked = false) in registered)
        }
        assertEquals("ic_unlock", TileIds.icon(QuickAction.LOCK, locked = false))
        assertEquals("ic_lock", TileIds.icon(QuickAction.LOCK, locked = true))
    }
}
