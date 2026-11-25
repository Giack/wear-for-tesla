package dev.giack.teslawear.core

/**
 * Quick commands offered as their own watch-face complications.
 * Fixed, unlike [TileCommands]: the Tile's saved choice does not add or remove these.
 * A tap opens hold-to-confirm for the [QuickAction]. Nothing here sends the command.
 */
val COMPLICATION_COMMANDS: List<QuickAction> = listOf(
    QuickAction.LOCK, QuickAction.CLIMATE, QuickAction.SENTRY,
)

/**
 * The only read a complication may make: the snapshot already stored on the watch.
 * No [BleLink] and no [CloudLink], so this cannot wake the car or call the Fleet API.
 * A confirmed command, or a manual refresh, is what may wake the car.
 */
suspend fun readComplicationSnapshot(store: SnapshotRepository): Snapshot? = store.load()
