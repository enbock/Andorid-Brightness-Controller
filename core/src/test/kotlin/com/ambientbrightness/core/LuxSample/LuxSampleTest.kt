package com.ambientbrightness.core

import com.ambientbrightness.core.AmbientLightSensing.LuxSample
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Tests for the sample value object handed to the brightness pipeline (AC3.3).
 */
class LuxSampleTest {

    @Test
    fun `AC3_3 - 0 lux is carried as a valid sample and not treated as missing`() {
        val sample = LuxSample(lux = 0f, timestampMillis = 1_700_000_000_000L)

        assertEquals(0f, sample.lux)
        assertEquals(1_700_000_000_000L, sample.timestampMillis)
    }

    @Test
    fun `keeps the lux value and its wall-clock timestamp together`() {
        val sample = LuxSample(lux = 42.5f, timestampMillis = 123L)

        assertEquals(42.5f, sample.lux)
        assertEquals(123L, sample.timestampMillis)
    }

    @Test
    fun `compares by value`() {
        assertEquals(LuxSample(10f, 1L), LuxSample(10f, 1L))
        assertNotEquals(LuxSample(10f, 1L), LuxSample(10f, 2L))
        assertNotEquals(LuxSample(10f, 1L), LuxSample(11f, 1L))
    }
}
