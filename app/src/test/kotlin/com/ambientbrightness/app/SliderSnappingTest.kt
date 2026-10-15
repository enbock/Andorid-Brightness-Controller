package com.ambientbrightness.app

import com.ambientbrightness.app.StatusDashboard.View.SubViews.snapOffsetPercent
import com.ambientbrightness.app.StatusDashboard.View.SubViews.snapResponseSpeedPercent
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffsetRange
import com.ambientbrightness.core.ResponseTuning.ResponseSpeed
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A Material slider emits every value a drag passes over, but the domain only accepts whole percent
 * steps, so the snapping must be exact and must never leave the valid range (US4, AC4.1).
 */
class SliderSnappingTest {

    @Test
    fun `an offset drag is snapped to whole percent`() {
        assertEquals(0, snapOffsetPercent(0f))
        assertEquals(7, snapOffsetPercent(7.4f))
        assertEquals(8, snapOffsetPercent(7.6f))
        assertEquals(-12, snapOffsetPercent(-11.6f))
        assertEquals(-11, snapOffsetPercent(-11.4f))
    }

    @Test
    fun `an offset beyond the range is clamped to its bounds`() {
        assertEquals(BrightnessOffsetRange.MAX, snapOffsetPercent(120f))
        assertEquals(BrightnessOffsetRange.MIN, snapOffsetPercent(-999f))
        assertEquals(BrightnessOffsetRange.MIN, snapOffsetPercent(Float.NEGATIVE_INFINITY))
        assertEquals(BrightnessOffsetRange.MAX, snapOffsetPercent(Float.POSITIVE_INFINITY))
    }

    @Test
    fun `a response-speed drag is snapped and clamped to the speed range`() {
        assertEquals(50, snapResponseSpeedPercent(50.4f))
        assertEquals(51, snapResponseSpeedPercent(50.5f))
        assertEquals(ResponseSpeed.SLOWEST_PERCENT, snapResponseSpeedPercent(150f))
        assertEquals(ResponseSpeed.FASTEST_PERCENT, snapResponseSpeedPercent(-20f))
    }
}
