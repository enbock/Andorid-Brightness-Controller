package com.ambientbrightness.app.StatusDashboard.View

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffset
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffsetApplication
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffsetRange
import com.ambientbrightness.core.BrightnessOffsetService.BrightnessOffsetService
import com.ambientbrightness.core.ResponseTuning.ResponseSpeed
import com.ambientbrightness.core.ResponseTuning.ResponseTuningService.ResponseTuningService
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Publishes the offset and response-speed settings and forwards the settings intents (US4).
 *
 * It holds no state of its own: every value is read from F3's `offset`/`application` and F4's
 * `responseSpeed`, so the screen is pre-filled with what is in effect (US4, AC4.1). Persistence is
 * F3/F4's (`SettingsStore` behind the services), which means a change made while the controller is
 * stopped is stored and takes effect on the next start (US4, AC4.4) without this class tracking it.
 *
 * The constructor takes one [BrightnessOffsetService] instead of a [BrightnessOffsetSource] plus a
 * writer, because it extends the source and the composition root always has the writable service
 * available: the screen reads through the same object it writes to.
 */
class SettingsViewModel(
    private val offsetService: BrightnessOffsetService,
    private val responseTuningService: ResponseTuningService,
) : ViewModel() {

    /** The state the settings screen renders; collected only while the screen has subscribers. */
    val uiState: StateFlow<SettingsUiState> = combine(
        offsetService.offset,
        offsetService.application,
        responseTuningService.responseSpeed,
    ) { offset, application, speed ->
        SettingsUiState(
            offsetPercent = offset.percent,
            canResetOffset = offset.percent != BrightnessOffsetRange.DEFAULT,
            offsetApplication = application,
            responseSpeedPercent = speed.percent,
            canResetResponseSpeed = speed.percent != ResponseSpeed.DEFAULT,
        )
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = initialState(),
        )

    /**
     * Sets the offset to [percent], snapped to the domain grid and clamped to
     * [BrightnessOffsetRange] first, so a continuous slider value can never throw (US4, AC4.1).
     */
    fun setOffsetPercent(percent: Int) {
        viewModelScope.launch {
            offsetService.setOffset(BrightnessOffset.of(snapToOffsetGrid(percent)))
        }
    }

    /** Returns the offset to the neutral value (US4, AC4.2). */
    fun resetOffset() {
        viewModelScope.launch {
            offsetService.reset()
        }
    }

    /** Sets the response speed to [percent], clamped to 0..100 (US4, AC4.1). */
    fun setResponseSpeedPercent(percent: Int) {
        viewModelScope.launch {
            responseTuningService.setResponseSpeed(ResponseSpeed.of(percent))
        }
    }

    /** Returns the response speed to the default (US4, AC4.1). */
    fun resetResponseSpeed() {
        viewModelScope.launch {
            responseTuningService.setResponseSpeed(ResponseSpeed.of(ResponseSpeed.DEFAULT))
        }
    }

    private companion object {

        /** How long the upstream stays collected after the last subscriber disappears. */
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Neutral placeholder shown before the first service emission; it claims nothing. */
        fun initialState(): SettingsUiState = SettingsUiState(
            offsetPercent = BrightnessOffsetRange.DEFAULT,
            canResetOffset = false,
            offsetApplication = BrightnessOffsetApplication.NotEvaluated,
            responseSpeedPercent = ResponseSpeed.DEFAULT,
            canResetResponseSpeed = false,
        )

        /** Clamps [percent] into [BrightnessOffsetRange] and aligns it to the offset step. */
        fun snapToOffsetGrid(percent: Int): Int {
            val clamped = percent.coerceIn(BrightnessOffsetRange.MIN, BrightnessOffsetRange.MAX)
            val steps = ((clamped - BrightnessOffsetRange.DEFAULT).toFloat() / BrightnessOffsetRange.STEP)
                .roundToInt()
            return (BrightnessOffsetRange.DEFAULT + steps * BrightnessOffsetRange.STEP)
                .coerceIn(BrightnessOffsetRange.MIN, BrightnessOffsetRange.MAX)
        }
    }
}
