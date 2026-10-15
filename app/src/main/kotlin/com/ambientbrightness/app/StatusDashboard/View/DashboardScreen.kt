package com.ambientbrightness.app.StatusDashboard.View

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ambientbrightness.app.StatusDashboard.View.SubViews.ControllerControlPanel
import com.ambientbrightness.app.StatusDashboard.View.SubViews.LiveReadoutPanel
import com.ambientbrightness.app.StatusDashboard.View.SubViews.SettingsEntryPoint
import com.ambientbrightness.app.StatusDashboard.View.SubViews.SetupSummaryPanel
import com.ambientbrightness.core.AppSetupService.SetupAction

/**
 * The app's main screen: what the sensor measures, what the app applies, and the start/stop control
 * (US1–US4).
 *
 * It is stateless on purpose — every value arrives in [uiState] and every interaction leaves through a
 * callback — so it can be rendered in a test without a ViewModel or a device. [DashboardRoute] is the
 * stateful wrapper that supplies the state and resolves the lifecycle-aware collection.
 *
 * The screen is inset-aware because edge-to-edge enforcement cannot be opted out on API 36, and it
 * never intercepts back itself: navigation is F7 T5's `NavHost`, which keeps predictive back working.
 */
@Composable
internal fun DashboardScreen(
    uiState: DashboardUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSetupAction: (SetupAction) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag(TAG_DASHBOARD),
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
            LiveReadoutPanel(light = uiState.light, clampHint = uiState.clampHint)
            ControllerControlPanel(
                controller = uiState.controller,
                canStart = uiState.canStart,
                onStart = onStart,
                onStop = onStop,
            )
            SettingsEntryPoint(
                offsetPercent = uiState.offsetPercent,
                responseSpeedPercent = uiState.responseSpeedPercent,
                onOpenSettings = onOpenSettings,
            )
            SetupSummaryPanel(
                setupSummary = uiState.setupSummary,
                onSetupAction = onSetupAction,
            )
        }
    }
}

/**
 * Stateful wrapper: collects [DashboardViewModel.uiState] for the current lifecycle and forwards the
 * screen's callbacks, so the state is only collected while the dashboard is actually visible.
 */
@Composable
internal fun DashboardRoute(
    viewModel: DashboardViewModel,
    onSetupAction: (SetupAction) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DashboardScreen(
        uiState = uiState,
        onStart = viewModel::start,
        onStop = viewModel::stop,
        onSetupAction = onSetupAction,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
    )
}

internal const val TAG_DASHBOARD = "dashboard"
