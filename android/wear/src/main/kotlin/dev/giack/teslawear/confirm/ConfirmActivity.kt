package dev.giack.teslawear.confirm

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.wear.compose.material3.Text
import dev.giack.teslawear.R
import dev.giack.teslawear.core.QuickAction
import dev.giack.teslawear.core.RouteOutcome
import dev.giack.teslawear.diag.Diag
import dev.giack.teslawear.graph
import dev.giack.teslawear.ui.HoldToConfirmButton
import dev.giack.teslawear.ui.iconFor
import dev.giack.teslawear.ui.string
import dev.giack.teslawear.ui.theme.TeslaColors
import dev.giack.teslawear.ui.theme.TeslaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ConfirmViewModel(app: Application, action: QuickAction) : AndroidViewModel(app) {
    private val graph = app.graph
    val controller = ConfirmController(
        action = action,
        snapshot = { graph.snapshots.load() },
        refresh = { trigger -> graph.provisioned()?.refresher?.refresh(trigger) ?: graph.snapshots.load() },
        execute = { cmd, progress ->
            (graph.provisioned()?.router?.execute(cmd, progress) ?: RouteOutcome.AuthRequired).also { Diag.i("Tile command $cmd → $it") }
        },
        afterCommand = { graph.notifySurfaces() },
        save = { graph.snapshots.save(it) },
        background = { block -> graph.appScope.launch { block() } },
    )

    init { viewModelScope.launch { controller.resolve() } }

    fun confirm() { viewModelScope.launch { controller.confirm() } }
}

/** Full-screen hold-to-confirm confirmation opened from the Tile and complications. */
class ConfirmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Exported for the Tile's LaunchAction: refuse touches while another app's window covers us.
        window.decorView.filterTouchesWhenObscured = true
        val action = intent.getStringExtra(EXTRA_ACTION)?.let { runCatching { QuickAction.valueOf(it) }.getOrNull() }
        if (action == null) { finish(); return }
        setContent {
            TeslaTheme {
                val vm: ConfirmViewModel = viewModel(factory = viewModelFactory { initializer { ConfirmViewModel(application, action) } })
                val ui by vm.controller.ui.collectAsState()
                LaunchedEffect(ui.phase) { if (ui.phase == Phase.DONE) { delay(1_200); finish() } }
                Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val label = ui.label?.string().orEmpty()
                    Text(label, fontSize = 20.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center)
                    Text(ui.age?.string().orEmpty(), fontSize = 11.sp, color = TeslaColors.Muted, textAlign = TextAlign.Center)
                    when (ui.phase) {
                        Phase.DONE -> Image(
                            painterResource(R.drawable.ic_check), null,
                            modifier = Modifier.padding(top = 12.dp).size(64.dp), colorFilter = ColorFilter.tint(TeslaColors.Green),
                        )
                        else -> Column(Modifier.padding(top = 12.dp)) {
                            HoldToConfirmButton(
                                icon = iconFor(action), label = label, active = false, size = 72.dp,
                                enabled = ui.phase == Phase.READY || ui.phase == Phase.ERROR, onConfirmed = vm::confirm,
                            )
                        }
                    }
                    val message = ui.message?.string() ?: if (ui.phase == Phase.READY) stringResource(R.string.confirm_hold) else null
                    message?.let {
                        Text(
                            it, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp),
                            color = if (ui.phase == Phase.ERROR) TeslaColors.Red else TeslaColors.Muted,
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_ACTION = "action"

        fun intent(ctx: Context, action: QuickAction): Intent =
            Intent(ctx, ConfirmActivity::class.java).putExtra(EXTRA_ACTION, action.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
