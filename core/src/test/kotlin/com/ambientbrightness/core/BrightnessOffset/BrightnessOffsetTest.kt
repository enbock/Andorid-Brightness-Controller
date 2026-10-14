package com.ambientbrightness.core.BrightnessOffset

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class BrightnessOffsetTest {

    @Test
    fun `AC3_1 - both bounds are valid offsets`() {
        assertEquals(-50, BrightnessOffset.of(BrightnessOffsetRange.MIN).percent)
        assertEquals(50, BrightnessOffset.of(BrightnessOffsetRange.MAX).percent)
    }

    @Test
    fun `an offset below the minimum is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            BrightnessOffset.of(BrightnessOffsetRange.MIN - 1)
        }
        assertThrows(IllegalArgumentException::class.java) { BrightnessOffset.of(Int.MIN_VALUE) }
    }

    @Test
    fun `an offset above the maximum is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            BrightnessOffset.of(BrightnessOffsetRange.MAX + 1)
        }
        assertThrows(IllegalArgumentException::class.java) { BrightnessOffset.of(Int.MAX_VALUE) }
    }

    @Test
    fun `AC2_4 - zero is the default and the identity`() {
        assertEquals(0, BrightnessOffset.DEFAULT.percent)
        assertEquals(BrightnessOffsetRange.DEFAULT, BrightnessOffset.DEFAULT.percent)
        assertEquals(BrightnessOffset.DEFAULT, BrightnessOffset.of(0))
    }

    @Test
    fun `the range is symmetric around the neutral default`() {
        assertEquals(-BrightnessOffsetRange.MIN, BrightnessOffsetRange.MAX)
        assertEquals(0, BrightnessOffsetRange.DEFAULT)
        assertEquals(BrightnessOffset.of(BrightnessOffsetRange.MIN).percent, -BrightnessOffset.of(BrightnessOffsetRange.MAX).percent)
    }

    @Test
    fun `every whole percent of the range is a valid step`() {
        for (percent in BrightnessOffsetRange.MIN..BrightnessOffsetRange.MAX) {
            assertEquals(percent, BrightnessOffset.of(percent).percent)
        }
    }

    @Test
    fun `equal percents are equal offsets`() {
        assertEquals(BrightnessOffset.of(17), BrightnessOffset.of(17))
        assertEquals(BrightnessOffset.of(-17).hashCode(), BrightnessOffset.of(-17).hashCode())
        assertNotEquals(BrightnessOffset.of(17), BrightnessOffset.of(-17))
    }
}
