package dev.giack.teslawear.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import dev.giack.teslawear.R
import dev.giack.teslawear.core.QuickAction
import dev.giack.teslawear.core.TileCommands
import dev.giack.teslawear.graph
import dev.giack.teslawear.ui.theme.TeslaColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Edits the Tile and asks it to redraw. The choice is already on disk before the redraw. */
class TileCommandsViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = app.graph
    private val _selected = MutableStateFlow(graph.tileCommands.selected())
    val selected: StateFlow<List<QuickAction>> = _selected

    fun toggle(action: QuickAction) = change { graph.tileCommands.toggle(action) }
    fun move(index: Int, delta: Int) = change { graph.tileCommands.move(index, index + delta) }

    private fun change(block: () -> List<QuickAction>) {
        val next = block()
        if (next == _selected.value) return
        _selected.value = next
        graph.notifySurfaces()
    }
}

@Composable
fun TileCommandsScreen(vm: TileCommandsViewModel = viewModel()) {
    val selected by vm.selected.collectAsState()
    val state = rememberTransformingLazyColumnState()
    val full = selected.size >= TileCommands.MAX
    ScreenScaffold(scrollState = state) { padding ->
        TransformingLazyColumn(state = state, contentPadding = padding) {
            item { ListHeader { Text(stringResource(R.string.tile_settings_title)) } }
            item {
                Text(
                    stringResource(R.string.tile_settings_hint),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = TeslaColors.Muted,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
            item { ListHeader { Text(stringResource(R.string.tile_settings_on_tile)) } }
            if (selected.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.tile_settings_empty),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        color = TeslaColors.Muted,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            selected.forEachIndexed { index, action ->
                item {
                    Text(
                        stringResource(R.string.tile_settings_slot, index + 1, actionLabel(action)),
                        textAlign = TextAlign.Center,
                        color = if (action.requiresFleet) TeslaColors.Amber else TeslaColors.Ink,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = { vm.move(index, -1) },
                            enabled = index > 0,
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.tile_move_up), maxLines = 1, fontSize = 12.sp) }
                        TextButton(
                            onClick = { vm.move(index, 1) },
                            enabled = index < selected.lastIndex,
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.tile_move_down), maxLines = 1, fontSize = 12.sp) }
                        TextButton(
                            onClick = { vm.toggle(action) },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.tile_remove), maxLines = 1, fontSize = 12.sp) }
                    }
                }
            }
            item { ListHeader { Text(stringResource(R.string.tile_settings_add)) } }
            if (full) {
                item {
                    Text(
                        stringResource(R.string.tile_settings_full),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        color = TeslaColors.Amber,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
            }
            QuickAction.entries.forEach { action ->
                if (action !in selected) {
                    item {
                        Button(
                            onClick = { vm.toggle(action) },
                            enabled = !full,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                actionLabel(action),
                                maxLines = 1,
                                color = if (action.requiresFleet) TeslaColors.Amber else TeslaColors.Ink,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun actionLabel(action: QuickAction): String {
    val name = stringResource(actionName(action))
    return if (action.requiresFleet) stringResource(R.string.tile_fleet_suffix, name) else name
}
