package com.ambientbrightness.core.ResponseTuning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ResponseSpeedTest {

    @Test
    fun `AC4_2 - the default sits in the middle of the range`() {
        assertEquals(50, ResponseSpeed.DEFAULT)
        assertEquals(50, ResponseSpeed.of(ResponseSpeed.DEFAULT).percent)
    }

    @Test
    fun `out-of-range input is clamped into the range`() {
        assertEquals(0, ResponseSpeed.of(-10).percent)
        assertEquals(100, ResponseSpeed.of(140).percent)
        assertEquals(0, ResponseSpeed.of(Int.MIN_VALUE).percent)
        assertEquals(100, ResponseSpeed.of(Int.MAX_VALUE).percent)
    }

    @Test
    fun `in-range input passes through unchanged`() {
        assertEquals(0, ResponseSpeed.of(0).percent)
        assertEquals(63, ResponseSpeed.of(63).percent)
        assertEquals(100, ResponseSpeed.of(100).percent)
    }

    @Test
    fun `equal percents are equal values`() {
        assertEquals(ResponseSpeed.of(70), ResponseSpeed.of(70))
        assertEquals(ResponseSpeed.of(70).hashCode(), ResponseSpeed.of(70).hashCode())
    }
}
