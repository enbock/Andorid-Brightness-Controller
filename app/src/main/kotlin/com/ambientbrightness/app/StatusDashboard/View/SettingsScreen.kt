package com.ambientbrightness.app.StatusDashboard.View

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ambientbrightness.app.R
import com.ambientbrightness.app.StatusDashboard.View.SubViews.OffsetControl
import com.ambientbrightness.app.StatusDashboard.View.SubViews.ResponseSpeedControl
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffsetApplication

/**
 * The dedicated settings screen (US4): the offset and response-speed controls, plus a way back.
 *
 * It is stateless on purpose, so it renders in a test with a plain [SettingsUiState]; [SettingsRoute]
 * is the stateful wrapper that collects the ViewModel. Every value is read from the services through
 * the ViewModel, so the screen opens **pre-filled** with what is in effect (US4, AC1.1).
 *
 * The screen is inset-aware (edge-to-edge cannot be opted out on API 36) and does not intercept back
 * itself: the back affordance and the system back gesture both go through the `NavHost`, which keeps
 * predictive back working.
 */
@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    onOffsetChange: (Int) -> Unit,
    onResetOffset: () -> Unit,
    onResponseSpeedChange: (Int) -> Unit,
    onResetResponseSpeed: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag(TAG_SETTINGS),
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            OffsetControl(
                offsetPercent = uiState.offsetPercent,
                canReset = uiState.canResetOffset,
                onOffsetChange = onOffsetChange,
                onReset = onResetOffset,
            )

            offsetLimitMessage(uiState.offsetApplication)?.let { message ->
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
            }

            ResponseSpeedControl(
                responseSpeedPercent = uiState.responseSpeedPercent,
                canReset = uiState.canResetResponseSpeed,
                onResponseSpeedChange = onResponseSpeedChange,
                onReset = onResetResponseSpeed,
            )

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.testTag(TAG_SETTINGS_BACK_BUTTON),
            ) {
                Text(stringResource(R.string.settings_back))
            }
        }
    }
}

/** Explains a limited offset on the settings screen; `null` when nothing was clamped (US3, AC1.1). */
@Composable
private fun offsetLimitMessage(application: BrightnessOffsetApplication): String? =
    when (application) {
        is BrightnessOffsetApplication.ClampedToMinimum ->
            stringResource(R.string.settings_offset_clamped_minimum)
        is BrightnessOffsetApplication.ClampedToMaximum ->
            stringResource(R.string.settings_offset_clamped_maximum)
        is BrightnessOffsetApplication.NotEvaluated, is BrightnessOffsetApplication.FullyApplied -> null
    }

/** Stateful wrapper: collects [SettingsViewModel.uiState] and forwards the intents (US4). */
@Composable
internal fun SettingsRoute(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(
        uiState = uiState,
        onOffsetChange = viewModel::setOffsetPercent,
        onResetOffset = viewModel::resetOffset,
        onResponseSpeedChange = viewModel::setResponseSpeedPercent,
        onResetResponseSpeed = viewModel::resetResponseSpeed,
        onBack = onBack,
        modifier = modifier,
    )
}

internal const val TAG_SETTINGS = "settings"
internal const val TAG_SETTINGS_BACK_BUTTON = "settings_back_button"
