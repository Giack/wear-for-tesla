package dev.giack.teslawear.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TileCommandsTest {
    @Test fun unsetChoiceIsTodaysDefault() {
        assertNull(TileCommands.decode(null))
        assertEquals(
            listOf(QuickAction.LOCK, QuickAction.CLIMATE, QuickAction.FRUNK, QuickAction.CHARGE_PORT),
            TileCommands.resolve(null),
        )
        assertEquals(TileCommands.DEFAULT, TileCommands.resolve(null))
    }

    @Test fun savedChoiceKeepsOrderAcrossAReload() {
        val store = MemoryTileStore()
        val first = TileCommandSelection(store)
        assertTrue(first.isUnchangedDefault())
        val saved = first.toggle(QuickAction.HORN)
        // Default already has four, so a fifth is refused and nothing is written.
        assertEquals(TileCommands.DEFAULT, saved)
        assertTrue(first.isUnchangedDefault())
        assertNull(store.raw)

        val trimmed = first.toggle(QuickAction.CHARGE_PORT)
        assertEquals(listOf(QuickAction.LOCK, QuickAction.CLIMATE, QuickAction.FRUNK), trimmed)
        val withHorn = first.toggle(QuickAction.HORN)
        assertEquals(listOf(QuickAction.LOCK, QuickAction.CLIMATE, QuickAction.FRUNK, QuickAction.HORN), withHorn)

        val reloaded = TileCommandSelection(store)
        assertFalse(reloaded.isUnchangedDefault())
        assertEquals(withHorn, reloaded.selected())
        assertEquals("lock,climate,frunk,horn", store.raw)
    }

    @Test fun moveReordersAndSurvivesAReload() {
        val store = MemoryTileStore()
        val selection = TileCommandSelection(store)
        val moved = selection.move(0, 2)
        assertEquals(
            listOf(QuickAction.CLIMATE, QuickAction.FRUNK, QuickAction.LOCK, QuickAction.CHARGE_PORT),
            moved,
        )
        assertEquals(moved, TileCommandSelection(store).selected())
        // A no-op move does not reshuffle.
        assertEquals(moved, selection.move(0, 0))
        assertEquals(moved, selection.move(-1, 0))
    }

    @Test fun decodeDropsUnknownCodesAndCapsAtFour() {
        assertEquals(emptyList(), TileCommands.decode(""))
        assertEquals(listOf(QuickAction.LOCK), TileCommands.decode("nope,lock"))
        assertNull(TileCommands.decode("nope"))
        assertEquals(
            listOf(QuickAction.HORN, QuickAction.SENTRY, QuickAction.TRUNK, QuickAction.WINDOWS),
            TileCommands.decode("horn,horn,sentry,trunk,windows,lights"),
        )
    }

    @Test fun onlyTheDriverDoorRequiresFleet() {
        assertEquals(listOf(QuickAction.UNLATCH_DOOR), QuickAction.entries.filter { it.requiresFleet })
    }
}

private class MemoryTileStore(var raw: String? = null) : TileCommandStore {
    override fun load(): List<QuickAction>? = TileCommands.decode(raw)
    override fun save(actions: List<QuickAction>) { raw = TileCommands.encode(actions) }
}
