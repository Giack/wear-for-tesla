package dev.giack.teslawear.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.wear.compose.material3.AppScaffold
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import dev.giack.teslawear.core.setup.OnboardingStep
import dev.giack.teslawear.setup.OnboardingScreen
import dev.giack.teslawear.setup.OnboardingViewModel
import dev.giack.teslawear.setup.SetupScreen
import dev.giack.teslawear.setup.SetupViewModel
import dev.giack.teslawear.ui.theme.TeslaTheme

class MainActivity : ComponentActivity() {
    private val setup: SetupViewModel by viewModels()
    private val home: HomeViewModel by viewModels()
    private val onboarding: OnboardingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.filterTouchesWhenObscured = true
        setContent {
            TeslaTheme {
                AppScaffold {
                    val setupUi by setup.ui.collectAsState()
                    val homeUi by home.controller.ui.collectAsState()
                    val onboardingUi by onboarding.ui.collectAsState()
                    var showSetup by remember { mutableStateOf(false) }
                    var showAbout by remember { mutableStateOf(false) }
                    val onboarded = onboardingUi.step == OnboardingStep.DONE
                    // onResume opens Home on every launch; this covers only onboarding finishing on screen.
                    var wasOnboarded by remember { mutableStateOf(onboarded) }
                    LaunchedEffect(onboarded) {
                        if (onboarded && !wasOnboarded) { setup.reload(); home.open() }
                        wasOnboarded = onboarded
                    }
                    BackHandler(enabled = onboarded && showSetup && !showAbout) { showSetup = false }
                    BackHandler(enabled = showAbout) { showAbout = false }
                    when {
                        !onboarded -> OnboardingScreen(onboarding)
                        showAbout -> AboutScreen()
                        // Fleet mode needs the Tesla login before Home can read anything remotely.
                        showSetup || (setupUi.fleet && !setupUi.loggedIn) || homeUi.authRequired -> SetupScreen(setup, onCarRemoved = {
                            showSetup = false
                            onboarding.reload()
                        }, onOpenAbout = { showAbout = true })
                        else -> HomeScreen(home, onOpenSetup = { showSetup = true })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        onboarding.reload()
        setup.reload()
        if (onboarding.ui.value.step == OnboardingStep.DONE) home.open()
    }
}
