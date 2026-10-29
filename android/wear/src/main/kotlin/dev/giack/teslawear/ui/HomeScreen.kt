package dev.giack.teslawear.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import dev.giack.teslawear.R
import dev.giack.teslawear.core.Channel
import dev.giack.teslawear.core.QuickAction
import dev.giack.teslawear.core.RouteOutcome
import dev.giack.teslawear.core.ToggleResolver
import dev.giack.teslawear.core.usage.BudgetLevel
import dev.giack.teslawear.diag.Diag
import dev.giack.teslawear.graph
import dev.giack.teslawear.ui.theme.TeslaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = app.graph
    val controller = HomeController(
        refresh = { t -> graph.provisioned()?.refresher?.refresh(t) ?: graph.snapshots.load() },
        snapshot = { graph.snapshots.load() },
        execute = { cmd, p ->
            (graph.provisioned()?.router?.execute(cmd, p) ?: RouteOutcome.AuthRequired).also { Diag.i("App command $cmd → $it") }
        },
        usage = {
            graph.provisioned()?.fleet?.let { UsageInfo(graph.meter.estimateUsd(), graph.meter.budgetUsd(), graph.meter.level()) }
        },
        scope = viewModelScope,
    )
    private val _frames = MutableStateFlow<List<ImageBitmap>?>(null)
    val frames: StateFlow<List<ImageBitmap>?> = _frames
    val model get() = graph.setup.state.profile?.model

    fun open() = viewModelScope.launch {
        _frames.value = loadFrames()
        controller.open()
        if (_frames.value == null && graph.carImages.prepare(graph.secretsStore.renderOptions())) {
            _frames.value = loadFrames()
            graph.notifySurfaces()
        }
    }
    // Decoding renders or drawing the illustration takes too long for the main thread.
    private suspend fun loadFrames() = withContext(Dispatchers.Default) { graph.carImages.frames() }
    fun send(action: QuickAction) = viewModelScope.launch { controller.send(action); graph.notifySurfaces() }
    fun adjustTemp(delta: Float) = controller.adjustTemp(delta)
    fun refreshNow() = viewModelScope.launch { controller.refreshNow(); graph.notifySurfaces() }
}

@Composable
fun HomeScreen(vm: HomeViewModel, onOpenSetup: () -> Unit) {
    val ui by vm.controller.ui.collectAsState()
    val frames by vm.frames.collectAsState()
    val s = ui.snapshot
    val now = System.currentTimeMillis()
    val state = rememberTransformingLazyColumnState()
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    fun label(a: QuickAction) = commandText(ToggleResolver.resolve(a, s, now).command).resolve(resources)

    ScreenScaffold(scrollState = state) { padding ->
        TransformingLazyColumn(state = state, contentPadding = padding, horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(dev.giack.teslawear.R.drawable.ic_brand), contentDescription = null,
                    modifier = Modifier.height(16.dp),
                )
            }
            item {
                Text(ui.statusLine?.string() ?: stringResource(modelName(vm.model)).uppercase(), fontSize = 11.sp,
                    color = TeslaColors.Muted, letterSpacing = 1.sp)
            }
            item { CarView(frames, Modifier.fillMaxWidth(0.82f).height(64.dp)) }
            item {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(s?.batteryPct?.let { "$it%" } ?: "--", fontSize = 30.sp, fontWeight = FontWeight.ExtraLight)
                    Text(s?.rangeKm?.let { "$it km" } ?: "", fontSize = 13.sp, color = TeslaColors.Muted,
                        modifier = Modifier.padding(bottom = 5.dp))
                }
            }
            item { BatteryBar(s?.batteryPct) }
            item {
                // One fixed-height slot for progress and errors, else the refresh button: a message
                // appearing never shifts the controls below while the user is tapping them.
                Box(Modifier.fillMaxWidth(0.8f).height(36.dp), contentAlignment = Alignment.Center) {
                    val m = ui.message
                    if (m != null) {
                        Text(m.string(), fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2,
                            color = if (ui.messageIsError) TeslaColors.Red else TeslaColors.Muted)
                    } else {
                        // Reads never wake the car on their own; this is how the user asks for fresh data.
                        TextButton(onClick = { vm.refreshNow() }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth().height(32.dp)) {
                            Text(stringResource(R.string.home_refresh), fontSize = 12.sp, maxLines = 1, color = TeslaColors.Muted)
                        }
                    }
                }
            }
            item { ActionGrid(listOf(QuickAction.LOCK, QuickAction.FRUNK, QuickAction.TRUNK), s, ui.busy, vm, ::label) }
            item { ClimateSection(s, ui.pendingTemp, ui.busy, vm) }
            item { ActionGrid(listOf(QuickAction.CHARGE_PORT, QuickAction.WINDOWS), s, ui.busy, vm, ::label) }
            item { ActionGrid(listOf(QuickAction.LIGHTS, QuickAction.HORN), s, ui.busy, vm, ::label) }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 8.dp)) {
                    s?.let { Text(stringResource(if (it.channel == Channel.BLE) R.string.channel_ble else R.string.channel_cloud), fontSize = 9.sp,
                        color = if (it.channel == Channel.BLE) TeslaColors.Accent else TeslaColors.Muted) }
                    ui.updated?.let { Text(it.string(), fontSize = 10.sp, color = TeslaColors.Muted) }
                    ui.usageText?.let { usage -> Text(usage.string(), fontSize = 10.sp, color = when (ui.usageTone) {
                        BudgetLevel.OK -> TeslaColors.Muted
                        BudgetLevel.WARN -> TeslaColors.Amber
                        BudgetLevel.EXHAUSTED -> TeslaColors.Red
                    }) }
                }
            }
            item {
                androidx.wear.compose.material3.FilledTonalIconButton(
                    onClick = onOpenSetup,
                    modifier = Modifier.padding(top = 8.dp).size(52.dp),
                ) {
                    androidx.compose.foundation.Image(
                        androidx.compose.ui.res.painterResource(dev.giack.teslawear.R.drawable.ic_settings), contentDescription = stringResource(R.string.home_settings),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionGrid(
    actions: List<QuickAction>, s: dev.giack.teslawear.core.Snapshot?, busy: Boolean, vm: HomeViewModel, label: (QuickAction) -> String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        actions.forEach { a ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HoldToConfirmButton(iconFor(a, s), label(a), active = activeFor(a, s), size = 52.dp, enabled = !busy) { vm.send(a) }
                Text(
                    stringResource(actionName(a)), fontSize = 11.sp, color = TeslaColors.Muted, textAlign = TextAlign.Center, maxLines = 1,
                    modifier = Modifier.width(64.dp).padding(top = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun BatteryBar(pct: Int?) {
    Box(Modifier.width(110.dp).height(3.dp).background(TeslaColors.Chip, RoundedCornerShape(2.dp))) {
        Box(Modifier.fillMaxWidth((pct ?: 0) / 100f).height(3.dp).background(TeslaColors.Green, RoundedCornerShape(2.dp)))
    }
}

private fun activeFor(a: QuickAction, s: dev.giack.teslawear.core.Snapshot?): Boolean = when (a) {
    QuickAction.LOCK -> s?.locked == true
    QuickAction.CLIMATE -> s?.climateOn == true
    QuickAction.CHARGE_PORT -> s?.chargePortOpen == true
    QuickAction.TRUNK -> s?.trunkOpen == true
    QuickAction.FRUNK -> s?.frunkOpen == true
    QuickAction.WINDOWS -> s?.windowsOpen == true
    else -> false
}

/** Cabin temperature and climate state from the car, plus the set point with ‹ › to change it. */
@Composable
private fun ClimateSection(s: dev.giack.teslawear.core.Snapshot?, pending: Float?, busy: Boolean, vm: HomeViewModel) {
    val on = s?.climateOn
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth(0.86f)
            .background(TeslaColors.Chip.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .padding(vertical = 10.dp),
    ) {
        Text(
            stringResource(when (on) { true -> R.string.climate_title_on; false -> R.string.climate_title_off; null -> R.string.climate_title }),
            fontSize = 11.sp, letterSpacing = 1.sp, color = if (on == true) TeslaColors.Accent else TeslaColors.Muted,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(s?.insideTempC?.let { temperatureText(it).string() } ?: "--", fontSize = 26.sp, fontWeight = FontWeight.ExtraLight)
            Text(stringResource(R.string.climate_inside), fontSize = 11.sp, color = TeslaColors.Muted, modifier = Modifier.padding(bottom = 5.dp))
        }
        val target = pending ?: s?.setTempC ?: HomeController.DEFAULT_TEMP
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp)) {
            TempStepButton("−", stringResource(R.string.climate_lower)) { vm.adjustTemp(-0.5f) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
                Text(temperatureText(target).string(), fontSize = 18.sp, maxLines = 1,
                    color = if (pending != null) TeslaColors.Accent else TeslaColors.Ink)
                Text(stringResource(R.string.climate_set), fontSize = 10.sp, color = TeslaColors.Muted)
            }
            TempStepButton("+", stringResource(R.string.climate_raise)) { vm.adjustTemp(+0.5f) }
        }
        // Climate on/off lives here, next to the temperatures it controls.
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 4.dp)) {
            HoldToConfirmButton(
                iconFor(QuickAction.CLIMATE),
                stringResource(if (on == true) R.string.command_climate_off else R.string.command_climate_on),
                active = on == true, size = 52.dp, enabled = !busy,
            ) { vm.send(QuickAction.CLIMATE) }
            Text(stringResource(if (on == true) R.string.climate_turn_off else R.string.climate_turn_on), fontSize = 11.sp, color = TeslaColors.Muted, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

/** Large round ± target: small glyph buttons were easy to miss and the miss scrolled the list instead. */
@Composable
private fun TempStepButton(glyph: String, description: String, onClick: () -> Unit) {
    androidx.wear.compose.material3.FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp).semantics { contentDescription = description },
    ) {
        Text(glyph, fontSize = 24.sp, fontWeight = FontWeight.Light)
    }
}
