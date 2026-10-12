package com.ambientbrightness.infrastructure

import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessRange
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidDeviceBrightnessRangeClientTest {

    @Test
    fun `valid maximum produces range with positive minimum`() {
        assertEquals(
            BrightnessRange(BrightnessValue(1), BrightnessValue(400)),
            brightnessRangeFromMaximum(400),
        )
    }

    @Test
    fun `missing or invalid maximum produces no range`() {
        assertNull(brightnessRangeFromMaximum(null))
        assertNull(brightnessRangeFromMaximum(0))
        assertNull(brightnessRangeFromMaximum(-1))
    }

    @Test
    fun `minimum is a valid singleton range maximum`() {
        assertEquals(
            BrightnessRange(BrightnessValue(1), BrightnessValue(1)),
            brightnessRangeFromMaximum(1),
        )
    }
}
