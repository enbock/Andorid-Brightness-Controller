package com.ambientbrightness.app.StatusDashboard.View

import com.ambientbrightness.core.BrightnessOffset.BrightnessOffsetApplication

/**
 * Everything the settings screen renders, derived only from F3's and F4's service flows.
 *
 * Immutable and comparable so `SettingsViewModel` can `distinctUntilChanged`: an equal re-emission
 * does not recompose the screen. No field is a copy kept by the ViewModel — each is read from the
 * service flow, so the screen shows the value that is actually in effect (US4, AC4.1).
 */
data class SettingsUiState(
    /** The active offset in percent of the brightness span, within `BrightnessOffsetRange` (US4, AC4.1). */
    val offsetPercent: Int,
    /**
     * Whether the offset differs from the neutral `BrightnessOffsetRange.DEFAULT`, so the reset
     * affordance is only offered when it changes something.
     */
    val canResetOffset: Boolean,
    /**
     * How far the offset could be applied, so the screen can indicate a limited offset instead of
     * hiding it (US4, AC4.1; the hint itself is defined by F3).
     */
    val offsetApplication: BrightnessOffsetApplication,
    /** The active response speed, 0..100, where the lowest reacts fastest (US4, AC4.1). */
    val responseSpeedPercent: Int,
    /**
     * Whether the response speed differs from `ResponseSpeed.DEFAULT`, so the reset affordance is
     * only offered when it changes something.
     */
    val canResetResponseSpeed: Boolean,
)
