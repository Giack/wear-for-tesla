package dev.giack.teslawear.setup

import android.Manifest
import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import dev.giack.teslawear.R
import dev.giack.teslawear.core.RefreshTrigger
import dev.giack.teslawear.core.car.Paint
import dev.giack.teslawear.core.setup.CarModel
import dev.giack.teslawear.core.setup.CarProfile
import dev.giack.teslawear.core.setup.ProfileSource
import dev.giack.teslawear.diag.Diag
import dev.giack.teslawear.protocol.VehicleCommand
import dev.giack.teslawear.core.fleet.AuthRequiredException
import dev.giack.teslawear.core.usage.UsageMeter
import dev.giack.teslawear.graph
import dev.giack.teslawear.ui.UiText
import dev.giack.teslawear.ui.lines
import dev.giack.teslawear.ui.modelName
import dev.giack.teslawear.ui.paintName
import dev.giack.teslawear.ui.resultText
import dev.giack.teslawear.ui.string
import dev.giack.teslawear.ui.text
import dev.giack.teslawear.ui.wheelsName
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SetupUi(
    val provisioned: Boolean,
    val loggedIn: Boolean,
    val busy: Boolean = false,
    val message: UiText? = null,
    val profile: CarProfile? = null,
    val fleet: Boolean = false,
    val budgetUsd: Double = UsageMeter.DEFAULT_BUDGET_USD,
    /** This month's Fleet API spending, e.g. "0.12 $ · 40 reads · 3 commands · 1 wake-up". */
    val usage: UiText? = null,
    /** First tap on "Remove car" done: the next one removes it. */
    val confirmRemove: Boolean = false,
)

class SetupViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = app.graph
    private val _ui = MutableStateFlow(current())
    val ui: StateFlow<SetupUi> = _ui

    private fun current(message: UiText? = null) = graph.provisioned().let {
        SetupUi(
            provisioned = it != null, loggedIn = it?.fleet?.tokens?.hasTokens == true, message = message,
            profile = graph.setup.state.profile, fleet = it?.fleet != null,
            budgetUsd = graph.meter.budgetUsd(), usage = usageText(),
        )
    }

    private fun usageText(): UiText {
        val c = graph.meter.counts()
        return text(
            R.string.setup_usage, graph.meter.estimateUsd(), UiText.Plural(R.plurals.setup_usage_reads, c.data),
            UiText.Plural(R.plurals.setup_usage_commands, c.commands), UiText.Plural(R.plurals.setup_usage_wakes, c.wakes),
        )
    }

    /** Monthly Fleet API budget: each tap moves to the next choice. Cloud reads stop once it is used up. */
    fun nextBudget() {
        graph.budget.save(UsageMeter.BUDGET_CHOICES.nextAfter(graph.meter.budgetUsd()))
        graph.notifySurfaces()
        _ui.value = current(_ui.value.message)
    }

    private fun List<Double>.nextAfter(value: Double): Double = firstOrNull { it > value + 1e-9 } ?: first()

    /** Car profile picker: each tap moves one setting to its next value. */
    fun nextModel() = pick { it.copy(model = CarModel.entries.next(it.model)) }
    fun nextPaint() = pick { it.copy(paint = Paint.entries.next(Paint.fromName(it.paint) ?: Paint.WHITE).name) }
    fun nextWheels() = pick { it.copy(wheels = WHEELS.next(it.wheels)) }

    private fun pick(change: (CarProfile) -> CarProfile) {
        val profile = change(graph.setup.state.profile ?: DEFAULT_PROFILE)
        graph.setup.pickProfile(profile.model, profile.paint, profile.wheels)
        graph.notifySurfaces()
        _ui.value = current(_ui.value.message)
    }

    private fun <T> List<T>.next(item: T): T = this[(indexOf(item) + 1) % size]

    fun reload() { _ui.value = current(_ui.value.message) }

    fun login() {
        val p = graph.provisioned()?.fleet ?: return
        _ui.value = _ui.value.copy(busy = true, message = text(R.string.setup_continue_on_phone))
        viewModelScope.launch {
            val result = LoginFlow(getApplication(), p).login()
            _ui.value = current(if (result is LoginFlow.Result.Error) result.message else text(R.string.setup_signed_in_done))
        }
    }

    fun pair() {
        val p = graph.provisioned()?.fleet ?: return
        val opened = LoginFlow(getApplication(), p).openPairingOnPhone()
        _ui.value = _ui.value.copy(
            message = text(if (opened) R.string.setup_confirm_on_phone else R.string.setup_domain_missing),
        )
    }

    /** Back to Bluetooth mode: Fleet credentials and tokens leave the watch. */
    fun disableFleet() {
        graph.disableFleet()
        graph.notifySurfaces()
        _ui.value = current(text(R.string.setup_fleet_disabled))
    }

    /** Diagnostics used during setup at the car. */
    fun bleCheck(flash: Boolean) {
        val p = graph.provisioned() ?: return
        _ui.value = _ui.value.copy(busy = true, message = text(R.string.diag_searching))
        viewModelScope.launch {
            val link = p.bleLink as? dev.giack.teslawear.ble.AndroidBleLink
            val report = link?.diagnose()
            val message = if (report != null && !report.found) {
                lines(
                    text(R.string.diag_not_found),
                    if (!report.permissionsOk) text(R.string.diag_permissions_missing) else null,
                    if (!report.bluetoothOn) text(R.string.diag_bluetooth_off) else null,
                    if (!report.locationOn) text(R.string.diag_location_off) else null,
                    text(R.string.diag_devices_seen, report.devicesSeen),
                    text(R.string.diag_teslas_nearby, report.teslaNames.size),
                )
            } else try {
                if (flash) {
                    p.bleLink.commands.execute(VehicleCommand.FlashLights).let { resultText(it) ?: text(R.string.diag_lights_ok) }
                } else {
                    p.bleLink.readStatus()?.let { text(if (it.locked == true) R.string.diag_ble_locked else R.string.diag_ble_unlocked) }
                        ?: lines(text(R.string.diag_status_unreadable), link?.lastError?.let { UiText.Raw(it) })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Diagnostics must report, never crash the app.
                Diag.w("BLE check failed", e)
                lines(text(R.string.diag_ble_error, e.javaClass.simpleName), e.message?.let { UiText.Raw(it) })
            }
            Diag.i("${if (flash) "Flash lights" else "BLE status"} → ${message.resolve(getApplication<Application>().resources)}")
            _ui.value = current(message)
        }
    }

    /** Pairs the watch's own BLE key: request, Keycard tap, then checks until the car accepts it. */
    fun pairBleKey() {
        val p = graph.provisioned() ?: return
        _ui.value = _ui.value.copy(busy = true, message = text(R.string.pairing_sending))
        viewModelScope.launch {
            val message = BleKeyPairing(p.bleLink, graph.setup).pair { _ui.value = _ui.value.copy(message = it) }
            _ui.value = current(message)
        }
    }

    /** Two taps: the first asks for confirmation, the second forgets the car and its BLE key. */
    fun removeCar(onRemoved: () -> Unit) {
        if (!_ui.value.confirmRemove) {
            _ui.value = _ui.value.copy(confirmRemove = true, message = text(R.string.setup_tap_again_to_remove))
            return
        }
        viewModelScope.launch {
            graph.removeCar()
            Diag.i("Car removed")
            _ui.value = current()
            onRemoved()
        }
    }

    /** The newest on-watch diagnostic lines, newest first, so a test at the car can be read without adb. */
    fun showLog() {
        val log = Diag.tail(LOG_LINES).asReversed().joinToString("\n")
        _ui.value = current(if (log.isEmpty()) text(R.string.setup_log_empty) else UiText.Raw(log))
    }

    fun test() {
        val p = graph.provisioned() ?: return
        _ui.value = _ui.value.copy(busy = true, message = text(R.string.setup_reading_state))
        viewModelScope.launch {
            val message = try {
                p.refresher.refresh(RefreshTrigger.APP_OPEN)?.let { s ->
                    text(if (s.asleep == true) R.string.setup_test_asleep else R.string.setup_test_ok, s.batteryPct?.toString() ?: "--")
                } ?: text(R.string.setup_test_no_data)
            } catch (e: AuthRequiredException) {
                text(R.string.outcome_sign_in_again)
            }
            _ui.value = current(message)
        }
    }

    private companion object {
        val WHEELS = listOf("STANDARD", "SPORT")
        val DEFAULT_PROFILE = CarProfile(CarModel.MODEL_Y, Paint.WHITE.name, WHEELS.first(), ProfileSource.USER)
        const val LOG_LINES = 40
    }
}

@Composable
fun SetupScreen(vm: SetupViewModel = viewModel(), onCarRemoved: () -> Unit = {}, onOpenAbout: () -> Unit = {}) {
    val ui by vm.ui.collectAsState()
    // Opening Setup from Home is no activity resume: re-read usage and state so they match Home.
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.reload() }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.reload() }
    val state = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = state) { padding ->
        TransformingLazyColumn(state = state, contentPadding = padding) {
            item { ListHeader { Text(stringResource(R.string.setup_title)) } }
            // Fleet mode (credentials pushed from the Mac): Tesla login, Cloud key pairing, budget.
            if (ui.fleet) {
                item {
                    Button(onClick = vm::login, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (ui.loggedIn) R.string.setup_signed_in else R.string.setup_sign_in))
                    }
                }
                item {
                    Button(onClick = vm::pair, enabled = ui.loggedIn, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.setup_pair_cloud_key)) }
                }
                item {
                    Button(onClick = vm::test, enabled = ui.loggedIn && !ui.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.setup_test_fleet)) }
                }
                item {
                    Button(onClick = vm::nextBudget, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.setup_budget, ui.budgetUsd))
                    }
                }
                item {
                    Text(stringResource(R.string.setup_this_month, ui.usage?.string().orEmpty()), textAlign = TextAlign.Center, fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth())
                }
                item {
                    Button(onClick = vm::disableFleet, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.setup_disable_fleet))
                    }
                }
            }
            item { ListHeader { Text(stringResource(R.string.setup_your_car)) } }
            item {
                Button(onClick = vm::nextModel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.profile_model, stringResource(modelName(ui.profile?.model))))
                }
            }
            item {
                Button(onClick = vm::nextPaint, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.profile_paint, stringResource(paintName(Paint.fromName(ui.profile?.paint)))))
                }
            }
            item {
                Button(onClick = vm::nextWheels, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.profile_wheels, stringResource(wheelsName(ui.profile?.wheels))))
                }
            }
            item {
                Button(onClick = { vm.removeCar(onCarRemoved) }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (ui.confirmRemove) R.string.setup_remove_car_confirm else R.string.setup_remove_car))
                }
            }
            item { ListHeader { Text(stringResource(R.string.setup_diagnostics)) } }
            item {
                Button(
                    onClick = { permissions.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)) },
                    enabled = ui.provisioned, modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.setup_bluetooth_permissions)) }
            }
            item {
                Button(onClick = { vm.bleCheck(flash = false) }, enabled = ui.provisioned && !ui.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.setup_ble_status))
                }
            }
            item {
                Button(onClick = vm::pairBleKey, enabled = ui.provisioned && !ui.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.setup_pair_ble_key))
                }
            }
            item {
                Button(onClick = { vm.bleCheck(flash = true) }, enabled = ui.provisioned && !ui.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.setup_flash_lights_ble))
                }
            }
            item {
                Button(onClick = vm::showLog, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.setup_ble_log)) }
            }
            item {
                Button(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.setup_about)) }
            }
            ui.message?.let { item { Text(it.string(), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) } }
        }
    }
}
