package dev.giack.teslawear.core

/**
 * The commands on the Tile, in left-to-right order. At most [MAX].
 * A missing value is not the same as an empty one: missing means the user has never chosen,
 * and the Tile keeps [DEFAULT].
 */
interface TileCommandStore {
    fun load(): List<QuickAction>?
    fun save(actions: List<QuickAction>)
}

object TileCommands {
    const val MAX = 4

    /** Lock, climate, frunk, charge port: the Tile as it shipped before a choice existed. */
    val DEFAULT: List<QuickAction> = listOf(
        QuickAction.LOCK, QuickAction.CLIMATE, QuickAction.FRUNK, QuickAction.CHARGE_PORT,
    )

    // Stable ids, not enum names: R8 may rename the enum, and a saved choice has to survive that.
    private val code = mapOf(
        QuickAction.LOCK to "lock",
        QuickAction.CLIMATE to "climate",
        QuickAction.FRUNK to "frunk",
        QuickAction.CHARGE_PORT to "charge_port",
        QuickAction.TRUNK to "trunk",
        QuickAction.WINDOWS to "windows",
        QuickAction.LIGHTS to "lights",
        QuickAction.HORN to "horn",
        QuickAction.SENTRY to "sentry",
        QuickAction.CHARGING to "charging",
        QuickAction.UNLATCH_DOOR to "door",
    )
    private val byCode = code.entries.associate { (action, id) -> id to action }

    fun resolve(saved: List<QuickAction>?): List<QuickAction> = saved?.let(::sanitize) ?: DEFAULT

    fun sanitize(actions: List<QuickAction>): List<QuickAction> =
        actions.distinct().filter { it in code }.take(MAX)

    fun encode(actions: List<QuickAction>): String =
        sanitize(actions).joinToString(",") { code.getValue(it) }

    /**
     * Null means unset, so the caller keeps [DEFAULT]. An empty string is a saved Tile with no
     * commands. A string with no known id is treated as unset, so a renamed id cannot blank the Tile.
     */
    fun decode(raw: String?): List<QuickAction>? {
        if (raw == null) return null
        if (raw.isEmpty()) return emptyList()
        val known = raw.split(',').mapNotNull { byCode[it] }
        if (known.isEmpty()) return null
        return sanitize(known)
    }

    fun toggle(current: List<QuickAction>, action: QuickAction): List<QuickAction> {
        if (action !in code) return current
        if (action in current) return current.filterNot { it == action }
        if (current.size >= MAX) return current
        return current + action
    }

    fun move(actions: List<QuickAction>, from: Int, to: Int): List<QuickAction> {
        if (from !in actions.indices || to !in actions.indices || from == to) return actions
        val next = actions.toMutableList()
        next.add(to, next.removeAt(from))
        return next
    }
}

/** The user's Tile. Nothing is written until the list actually changes, so a fresh install stays on [TileCommands.DEFAULT]. */
class TileCommandSelection(private val store: TileCommandStore) {
    fun selected(): List<QuickAction> = TileCommands.resolve(store.load())

    fun isUnchangedDefault(): Boolean = store.load() == null

    fun toggle(action: QuickAction): List<QuickAction> = update(TileCommands.toggle(selected(), action))

    fun move(from: Int, to: Int): List<QuickAction> = update(TileCommands.move(selected(), from, to))

    private fun update(next: List<QuickAction>): List<QuickAction> {
        val current = selected()
        if (next == current) return current
        val clean = TileCommands.sanitize(next)
        store.save(clean)
        return clean
    }
}
