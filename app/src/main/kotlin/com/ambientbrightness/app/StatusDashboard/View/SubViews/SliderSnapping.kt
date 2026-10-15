package com.ambientbrightness.app.StatusDashboard.View.SubViews

import com.ambientbrightness.core.BrightnessOffset.BrightnessOffsetRange
import com.ambientbrightness.core.ResponseTuning.ResponseSpeed
import kotlin.math.roundToInt

/**
 * Turns a continuous slider value into the discrete value the domain accepts (US4, AC4.1).
 *
 * Material 3 sliders emit every pixel they are dragged over, while `BrightnessOffset.of(...)` rejects
 * anything off the 1 % grid. Snapping here — in a pure function, so it is unit-testable — means a drag
 * can never reach the service as an invalid value or throw.
 */
internal fun snapOffsetPercent(value: Float): Int =
    value.roundToInt().coerceIn(BrightnessOffsetRange.MIN, BrightnessOffsetRange.MAX)

/** The same snapping for F4's response speed, clamped to [ResponseSpeed]'s range. */
internal fun snapResponseSpeedPercent(value: Float): Int =
    value.roundToInt().coerceIn(ResponseSpeed.FASTEST_PERCENT, ResponseSpeed.SLOWEST_PERCENT)
