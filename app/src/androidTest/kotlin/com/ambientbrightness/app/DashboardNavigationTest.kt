package com.ambientbrightness.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ambientbrightness.app.Composition.AmbientBrightnessComponent
import com.ambientbrightness.app.StatusDashboard.Adapter.DashboardStateCollector
import com.ambientbrightness.app.StatusDashboard.Controller.ControllerCommandHandler
import com.ambientbrightness.app.StatusDashboard.Navigator.DashboardNavHost
import com.ambientbrightness.app.StatusDashboard.View.DashboardViewModel
import com.ambientbrightness.app.StatusDashboard.View.SettingsViewModel
import com.ambientbrightness.app.StatusDashboard.View.SubViews.TAG_OFFSET_SLIDER
import com.ambientbrightness.app.StatusDashboard.View.SubViews.TAG_RESET_OFFSET_BUTTON
import com.ambientbrightness.app.StatusDashboard.View.SubViews.TAG_RESPONSE_SPEED_SLIDER
import com.ambientbrightness.app.StatusDashboard.View.TAG_DASHBOARD
import com.ambientbrightness.app.StatusDashboard.View.TAG_SETTINGS
import com.ambientbrightness.app.StatusDashboard.View.TAG_SETTINGS_BACK_BUTTON
import com.ambientbrightness.app.StatusDashboard.View.SubViews.TAG_OPEN_SETTINGS_BUTTON
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffset
import com.ambientbrightness.infrastructure.AmbientLightSensing.AmbientLightSensorClient.Dummy.DummyAmbientLightSensorClient
import com.ambientbrightness.infrastructure.AmbientLightSensing.ScreenStateClient.Dummy.DummyScreenStateClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real navigation graph over the real composition root with Dummy adapters: the dashboard
 * opens the settings screen pre-filled, a slider writes through the service, reset clears it, and back
 * returns to the dashboard (US4, AC4.1/AC4.2/AC4.4).
 *
 * These tests need an emulator or device; without one they are compile-verified only, which is why the
 * same behaviour is covered on the JVM by `SettingsViewModelTest` and `DashboardViewModelTest`.
 */
@RunWith(AndroidJUnit4::class)
class DashboardNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theEntryPointOpensThePrefilledSettingsAndBackReturns() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val component = AmbientBrightnessComponent(
            sensorClient = DummyAmbientLightSensorClient(),
            screenStateClient = DummyScreenStateClient(initiallyScreenOn = true),
            controllerScope = scope,
        )
        val dashboardViewModel = DashboardViewModel(
            provider = DashboardCollector(component),
            commandHandler = ControllerCommandHandler(component.backgroundService),
        )
        val settingsViewModel = SettingsViewModel(
            offsetService = component.brightnessOffsetService,
            responseTuningService = component.responseTuningService,
        )

        try {
            // The stored offset is what the settings screen has to show pre-filled (US4 AC1).
            runBlocking { component.brightnessOffsetService.setOffset(BrightnessOffset.of(-12)) }

            compose.setContent {
                MaterialTheme {
                    DashboardNavHost(
                        dashboardViewModel = dashboardViewModel,
                        settingsViewModel = settingsViewModel,
                        onSetupAction = {},
                    )
                }
            }

            compose.onNodeWithTag(TAG_DASHBOARD).assertIsDisplayed()

            compose.onNodeWithTag(TAG_OPEN_SETTINGS_BUTTON).performClick()
            compose.onNodeWithTag(TAG_SETTINGS).assertIsDisplayed()
            compose.onNodeWithText("Offset: -12%").assertIsDisplayed()

            compose.onNodeWithTag(TAG_RESPONSE_SPEED_SLIDER).assertIsDisplayed()

            compose.onNodeWithTag(TAG_OFFSET_SLIDER)
                .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(25f) }
            compose.waitUntil(TIMEOUT_MILLIS) {
                component.brightnessOffsetService.offset.value.percent == 25
            }

            compose.onNodeWithTag(TAG_RESET_OFFSET_BUTTON).performClick()
            compose.waitUntil(TIMEOUT_MILLIS) {
                component.brightnessOffsetService.offset.value.percent == 0
            }

            compose.onNodeWithTag(TAG_SETTINGS_BACK_BUTTON).performClick()
            compose.onNodeWithTag(TAG_DASHBOARD).assertIsDisplayed()
        } finally {
            component.stop()
            scope.cancel()
        }
    }

    /** Builds F7's provider from the same boundaries the app uses. */
    private fun DashboardCollector(component: AmbientBrightnessComponent) = DashboardStateCollector(
        ambientLightService = component.ambientLightService,
        backgroundService = component.backgroundService,
        setupService = component.setupService,
        brightnessOffsetSource = component.brightnessOffsetService,
        responseTuningService = component.responseTuningService,
    )

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
