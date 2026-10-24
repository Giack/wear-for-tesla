package dev.giack.teslawear.setup

import android.app.Application
import android.app.RemoteInput
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.viewModelScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper
import dev.giack.teslawear.R
import dev.giack.teslawear.ble.BleScanner
import dev.giack.teslawear.core.car.Paint
import dev.giack.teslawear.core.setup.Car
import dev.giack.teslawear.core.setup.CarModel
import dev.giack.teslawear.core.setup.CarProfile
import dev.giack.teslawear.core.setup.CarSearch
import dev.giack.teslawear.core.setup.OnboardingStep
import dev.giack.teslawear.core.setup.ProfileSource
import dev.giack.teslawear.core.setup.Vin
import dev.giack.teslawear.core.setup.onboardingStep
import dev.giack.teslawear.diag.Diag
import dev.giack.teslawear.graph
import dev.giack.teslawear.ui.UiText
import dev.giack.teslawear.ui.lines
import dev.giack.teslawear.ui.modelName
import dev.giack.teslawear.ui.paintName
import dev.giack.teslawear.ui.string
import dev.giack.teslawear.ui.text
import dev.giack.teslawear.ui.wheelsName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class OnboardingUi(
    val step: OnboardingStep,
    /** The intro is shown once per launch until the profile is picked. */
    val introDone: Boolean = false,
    val draft: CarProfile = DEFAULT_PROFILE,
    val vin: String? = null,
    val busy: Boolean = false,
    val message: UiText? = null,
)

/** Bluetooth-mode onboarding: intro, car profile, VIN checked by a scan, BLE key with the Keycard. */
class OnboardingViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = app.graph
    private val scanner = BleScanner(app)
    // provisioned() first: it migrates installs set up by `setup.sh push-watch`, which need no onboarding.
    private val _ui = MutableStateFlow(OnboardingUi(graph.provisioned().let { graph.setup.state.onboardingStep }))
    val ui: StateFlow<OnboardingUi> = _ui

    val bluetoothPermissions get() = scanner.requiredPermissions
    fun permissionsOk() = scanner.permissionsOk()

    fun reload() { _ui.value = _ui.value.copy(step = graph.provisioned().let { graph.setup.state.onboardingStep }) }

    fun start() { _ui.value = _ui.value.copy(introDone = true) }

    fun nextModel() = edit { it.copy(model = CarModel.entries.next(it.model)) }
    fun nextPaint() = edit { it.copy(paint = Paint.entries.next(Paint.fromName(it.paint) ?: Paint.WHITE).name) }
    fun nextWheels() = edit { it.copy(wheels = WHEELS.next(it.wheels)) }

    private fun edit(change: (CarProfile) -> CarProfile) { _ui.value = _ui.value.copy(draft = change(_ui.value.draft)) }

    private fun <T> List<T>.next(item: T): T = this[(indexOf(item) + 1) % size]

    fun confirmProfile() {
        val p = _ui.value.draft
        graph.setup.pickProfile(p.model, p.paint, p.wheels)
        graph.notifySurfaces()
        _ui.value = _ui.value.copy(step = graph.setup.state.onboardingStep, message = null)
    }

    fun enterVin(typed: String) {
        val vin = Vin.parse(typed)
        _ui.value = _ui.value.copy(
            vin = vin ?: _ui.value.vin,
            message = if (vin == null) text(R.string.onboarding_vin_invalid) else null,
        )
    }

    /** Looks for the car advertising this VIN's name; a nearby match confirms the VIN before pairing. */
    fun search() {
        val vin = _ui.value.vin ?: return
        _ui.value = _ui.value.copy(busy = true, message = text(R.string.onboarding_searching))
        viewModelScope.launch {
            val (_, report) = scanner.diagnose(Car.fromVin(vin).localName, SCAN_MS)
            val message = when {
                !report.permissionsOk -> text(R.string.onboarding_allow_permissions)
                !report.bluetoothOn -> text(R.string.onboarding_bluetooth_off)
                else -> when (val found = CarSearch.match(vin, report.teslaNames)) {
                    is CarSearch.Found -> {
                        graph.setup.chooseCar(found.car)
                        graph.resetProvisioning()
                        null
                    }
                    is CarSearch.OtherTeslas -> UiText.Plural(R.plurals.onboarding_other_teslas, found.count)
                    CarSearch.NoTesla ->
                        lines(text(R.string.onboarding_no_tesla), if (!report.locationOn) text(R.string.onboarding_location_off) else null)
                }
            }
            Diag.i("Onboarding: car search → ${message?.resolve(getApplication<Application>().resources) ?: "found"}")
            _ui.value = _ui.value.copy(busy = false, step = graph.setup.state.onboardingStep, message = message)
        }
    }

    fun pair() {
        val p = graph.provisioned() ?: return
        _ui.value = _ui.value.copy(busy = true, message = text(R.string.pairing_sending))
        viewModelScope.launch {
            val message = BleKeyPairing(p.bleLink, graph.setup).pair { _ui.value = _ui.value.copy(message = it) }
            _ui.value = _ui.value.copy(busy = false, step = graph.setup.state.onboardingStep, message = message)
        }
    }

    /** Back to the VIN: the car found is forgotten, nothing was sent to it yet. */
    fun changeCar() {
        graph.setup.reset()
        graph.setup.pickProfile(_ui.value.draft.model, _ui.value.draft.paint, _ui.value.draft.wheels)
        graph.resetProvisioning()
        _ui.value = _ui.value.copy(step = graph.setup.state.onboardingStep, vin = null, message = null)
    }

    private companion object {
        val WHEELS = listOf("STANDARD", "SPORT")
        const val SCAN_MS = 8_000L
    }
}

private val DEFAULT_PROFILE = CarProfile(CarModel.MODEL_Y, Paint.WHITE.name, "STANDARD", ProfileSource.USER)

@Composable
fun OnboardingScreen(vm: OnboardingViewModel) {
    val ui by vm.ui.collectAsState()
    val state = rememberTransformingLazyColumnState()
    val vinInput = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.let { RemoteInput.getResultsFromIntent(it)?.getCharSequence(VIN_KEY)?.toString() }?.let(vm::enterVin)
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (vm.permissionsOk()) vm.search()
    }
    val vinLabel = stringResource(R.string.onboarding_vin_input)
    ScreenScaffold(scrollState = state) { padding ->
        TransformingLazyColumn(state = state, contentPadding = padding) {
            when {
                ui.step == OnboardingStep.PROFILE && !ui.introDone -> intro(vm)
                ui.step == OnboardingStep.PROFILE -> profile(ui, vm)
                ui.step == OnboardingStep.CAR -> car(ui, vm,
                    typeVin = { vinInput.launch(vinIntent(vinLabel)) },
                    search = { if (vm.permissionsOk()) vm.search() else permissions.launch(vm.bluetoothPermissions) },
                )
                else -> key(ui, vm)
            }
            ui.message?.let { m ->
                item { Text(m.string(), textAlign = TextAlign.Center, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) }
            }
        }
    }
}

private fun TransformingLazyColumnScope.intro(vm: OnboardingViewModel) {
    item { ListHeader { Text(stringResource(R.string.onboarding_welcome)) } }
    item {
        Text(
            stringResource(R.string.onboarding_intro),
            textAlign = TextAlign.Center, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(),
        )
    }
    item { Button(onClick = vm::start, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.onboarding_start)) } }
}

private fun TransformingLazyColumnScope.profile(ui: OnboardingUi, vm: OnboardingViewModel) {
    item { ListHeader { Text(stringResource(R.string.onboarding_step_car)) } }
    item {
        Button(onClick = vm::nextModel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.profile_model, stringResource(modelName(ui.draft.model))))
        }
    }
    item {
        Button(onClick = vm::nextPaint, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.profile_paint, stringResource(paintName(Paint.fromName(ui.draft.paint) ?: Paint.WHITE))))
        }
    }
    item {
        Button(onClick = vm::nextWheels, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.profile_wheels, stringResource(wheelsName(ui.draft.wheels))))
        }
    }
    item { Button(onClick = vm::confirmProfile, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.onboarding_next)) } }
}

private fun TransformingLazyColumnScope.car(ui: OnboardingUi, vm: OnboardingViewModel, typeVin: () -> Unit, search: () -> Unit) {
    item { ListHeader { Text(stringResource(R.string.onboarding_step_vin)) } }
    item {
        Text(
            stringResource(R.string.onboarding_vin_help),
            textAlign = TextAlign.Center, fontSize = 13.sp, modifier = Modifier.fillMaxWidth(),
        )
    }
    item {
        Button(onClick = typeVin, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
            Text(ui.vin ?: stringResource(R.string.onboarding_enter_vin), maxLines = 1, fontSize = if (ui.vin != null) 13.sp else 15.sp)
        }
    }
    item {
        Button(onClick = search, enabled = ui.vin != null && !ui.busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_search))
        }
    }
}

private fun TransformingLazyColumnScope.key(ui: OnboardingUi, vm: OnboardingViewModel) {
    item { ListHeader { Text(stringResource(R.string.onboarding_step_key)) } }
    item {
        Text(
            stringResource(R.string.onboarding_key_help),
            textAlign = TextAlign.Center, fontSize = 13.sp, modifier = Modifier.fillMaxWidth(),
        )
    }
    item { Button(onClick = vm::pair, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_pair)) } }
    item { Button(onClick = vm::changeCar, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_change_vin)) } }
}

private const val VIN_KEY = "vin"

private fun vinIntent(label: String): Intent = RemoteInputIntentHelper.createActionRemoteInputIntent().also {
    RemoteInputIntentHelper.putRemoteInputsExtra(it, listOf(RemoteInput.Builder(VIN_KEY).setLabel(label).build()))
}
