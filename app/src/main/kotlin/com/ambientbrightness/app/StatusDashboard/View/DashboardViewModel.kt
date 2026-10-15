package com.ambientbrightness.app.StatusDashboard.View

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ambientbrightness.app.StatusDashboard.Controller.ControllerCommandHandler
import com.ambientbrightness.app.StatusDashboard.View.Task.DashboardUiStateMapper
import com.ambientbrightness.core.ResponseTuning.ResponseSpeed
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Publishes the dashboard state and forwards Start/Stop intents (US1–US3).
 *
 * It is a thin shell: the collector supplies [DashboardSnapshot]s, [DashboardUiStateMapper] applies
 * every rule, and this class only maps and shares the result. `distinctUntilChanged` keeps an equal
 * re-emission from recomposing the screen, and the [initialState] is a neutral placeholder shown only
 * until the first snapshot arrives.
 */
class DashboardViewModel(
    provider: DashboardStateProvider,
    private val commandHandler: ControllerCommandHandler,
    mapper: DashboardUiStateMapper = DashboardUiStateMapper(),
) : ViewModel() {

    /** The state the dashboard renders; collected only while the screen has subscribers. */
    val uiState: StateFlow<DashboardUiState> = provider.snapshots()
        .map(mapper::map)
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = initialState(),
        )

    /** Requests a user-initiated start (US2, AC2.1). */
    fun start() {
        commandHandler.start()
    }

    /** Requests a stop (US2, AC2.2). */
    fun stop() {
        commandHandler.stop()
    }

    private companion object {

        /** How long the upstream stays collected after the last subscriber disappears. */
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Neutral placeholder shown before the first snapshot; it claims nothing about the service. */
        fun initialState(): DashboardUiState = DashboardUiState(
            controller = ControllerUiState.Stopped,
            light = LightReadout.Unavailable,
            setupSummary = null,
            canStart = false,
            offsetPercent = 0,
            responseSpeedPercent = ResponseSpeed.DEFAULT,
            clampHint = ClampHint.None,
        )
    }
}
