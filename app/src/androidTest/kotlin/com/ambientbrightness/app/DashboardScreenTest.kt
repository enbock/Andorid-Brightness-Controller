package com.ambientbrightness.app

import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ambientbrightness.app.StatusDashboard.View.ClampHint
import com.ambientbrightness.app.StatusDashboard.View.ControllerUiState
import com.ambientbrightness.app.StatusDashboard.View.DashboardScreen
import com.ambientbrightness.app.StatusDashboard.View.DashboardUiState
import com.ambientbrightness.app.StatusDashboard.View.LightReadout
import com.ambientbrightness.app.StatusDashboard.View.SubViews.TAG_START_BUTTON
import com.ambientbrightness.app.StatusDashboard.View.SubViews.TAG_STOP_BUTTON
import com.ambientbrightness.core.AmbientLightSensing.LightLevel.LightLevel
import com.ambientbrightness.core.AppSetupService.MissingReason
import com.ambientbrightness.core.AppSetupService.RequirementSeverity
import com.ambientbrightness.core.AppSetupService.RequirementState
import com.ambientbrightness.core.AppSetupService.SetupAction
import com.ambientbrightness.core.AppSetupService.SetupRequirement
import com.ambientbrightness.core.AppSetupService.SetupRequirementId
import com.ambientbrightness.core.AppSetupService.SetupStatus
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.ResponseTuning.ResponseSpeed
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders the dashboard without a device and asserts the states the user relies on (US1–US3).
 *
 * These tests need an emulator or device; in a hardware-less environment they are compile-verified
 * only, which is why every behaviour they touch is also covered by the `:app` JVM tests.
 */
@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun stoppedDashboardShowsTheStoppedMessageAndNoReading() {
        compose.setContent {
            DashboardScreen(
                uiState = state(controller = ControllerUiState.Stopped, light = LightReadout.Unavailable),
                onStart = {},
                onStop = {},
                onSetupAction = {},
                onOpenSettings = {},
            )
        }

        compose.onNodeWithText("Not running").assertIsDisplayed()
        compose.onNodeWithText("No current reading").assertIsDisplayed()
    }

    @Test
    fun runningDashboardShowsTheLuxValueAndItsLevel() {
        compose.setContent {
            DashboardScreen(
                uiState = state(
                    controller = ControllerUiState.Running(BrightnessValue(123)),
                    light = LightReadout.Reading(lux = 5_000f, level = LightLevel.BRIGHT),
                ),
                onStart = {},
                onStop = {},
                onSetupAction = {},
                onOpenSettings = {},
            )
        }

        compose.onNodeWithText("5000 lx").assertIsDisplayed()
        compose.onNodeWithText("Bright").assertIsDisplayed()
        compose.onNodeWithTag(TAG_STOP_BUTTON).assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun startIsDisabledAndTheReasonIsVisibleWhileASetupStepIsMissing() {
        val blockers = listOf(SetupRequirementId.WriteSettings)
        compose.setContent {
            DashboardScreen(
                uiState = state(
                    controller = ControllerUiState.Blocked(blockers),
                    canStart = false,
                    setupSummary = setupStatus(blockers),
                ),
                onStart = {},
                onStop = {},
                onSetupAction = {},
                onOpenSettings = {},
            )
        }

        compose.onNodeWithTag(TAG_START_BUTTON).assertIsNotEnabled()
        compose.onNodeWithText("Cannot start yet").assertIsDisplayed()
        compose.onNodeWithText(
            "Still needed: Permission to change the screen brightness",
        ).assertIsDisplayed()
    }

    @Test
    fun aFailedControllerShowsTheErrorInsteadOfRunning() {
        compose.setContent {
            DashboardScreen(
                uiState = state(controller = ControllerUiState.Failed("WRITE_SETTINGS revoked")),
                onStart = {},
                onStop = {},
                onSetupAction = {},
                onOpenSettings = {},
            )
        }

        compose.onNodeWithText("Stopped because of an error").assertIsDisplayed()
        compose.onNodeWithText("Reason: WRITE_SETTINGS revoked").assertIsDisplayed()
    }

    @Test
    fun aLimitedOffsetIsIndicated() {
        compose.setContent {
            DashboardScreen(
                uiState = state(clampHint = ClampHint.Limited(atMaximum = true)),
                onStart = {},
                onStop = {},
                onSetupAction = {},
                onOpenSettings = {},
            )
        }

        compose.onNodeWithText(
            "The offset is at its highest setting for this light level.",
        ).assertIsDisplayed()
    }

    @Test
    fun germanLocaleResolvesTheLocalisedLabels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.GERMAN)
        }
        val german = context.createConfigurationContext(configuration)

        assertEquals("Hell", german.getString(R.string.light_level_bright))
        assertEquals("Umgebungslicht", german.getString(R.string.dashboard_light_heading))
        assertEquals("Nicht aktiv", german.getString(R.string.dashboard_controller_stopped))
    }

    private fun state(
        controller: ControllerUiState = ControllerUiState.Stopped,
        light: LightReadout = LightReadout.Unavailable,
        setupSummary: SetupStatus? = setupStatus(emptyList()),
        canStart: Boolean = true,
        clampHint: ClampHint = ClampHint.None,
    ) = DashboardUiState(
        controller = controller,
        light = light,
        setupSummary = setupSummary,
        canStart = canStart,
        offsetPercent = 0,
        responseSpeedPercent = ResponseSpeed.DEFAULT,
        clampHint = clampHint,
    )

    private fun setupStatus(blockers: List<SetupRequirementId>) = SetupStatus(
        requirements = blockers.map { id ->
            SetupRequirement(
                id = id,
                severity = RequirementSeverity.Blocking,
                state = RequirementState.Missing(MissingReason.NotGranted),
                action = SetupAction.OpenWriteSettings,
            )
        },
        isReadyToStart = blockers.isEmpty(),
        blockers = blockers,
    )
}
