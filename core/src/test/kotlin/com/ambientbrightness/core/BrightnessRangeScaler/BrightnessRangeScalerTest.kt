package com.ambientbrightness.core.BrightnessRangeScaler

import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessRange
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessRangeScaler.BrightnessRangeScaler
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class BrightnessRangeScalerTest {

    private val range = BrightnessRange(BrightnessValue(10), BrightnessValue(110))

    @Test
    fun `AC2_1 AC2_2 normalized endpoints and midpoint scale into inclusive range`() {
        assertEquals(BrightnessValue(10), BrightnessRangeScaler.scale(0f, range))
        assertEquals(BrightnessValue(60), BrightnessRangeScaler.scale(0.5f, range))
        assertEquals(BrightnessValue(110), BrightnessRangeScaler.scale(1f, range))
    }

    @Test
    fun `AC2_1 AC2_2 values outside normalized bounds saturate at range endpoints`() {
        assertEquals(BrightnessValue(10), BrightnessRangeScaler.scale(-0.25f, range))
        assertEquals(BrightnessValue(110), BrightnessRangeScaler.scale(1.25f, range))
        assertEquals(BrightnessValue(10), BrightnessRangeScaler.scale(Float.NEGATIVE_INFINITY, range))
        assertEquals(BrightnessValue(110), BrightnessRangeScaler.scale(Float.POSITIVE_INFINITY, range))
    }

    @Test
    fun `AC2_1 singleton device range always resolves to its only nonzero value`() {
        val singleton = BrightnessRange(BrightnessValue(42), BrightnessValue(42))

        assertEquals(BrightnessValue(42), BrightnessRangeScaler.scale(0f, singleton))
        assertEquals(BrightnessValue(42), BrightnessRangeScaler.scale(0.5f, singleton))
        assertEquals(BrightnessValue(42), BrightnessRangeScaler.scale(1f, singleton))
    }

    @Test
    fun `AC2_1 NaN fraction is rejected explicitly`() {
        assertThrows(IllegalArgumentException::class.java) {
            BrightnessRangeScaler.scale(Float.NaN, range)
        }
    }
}
