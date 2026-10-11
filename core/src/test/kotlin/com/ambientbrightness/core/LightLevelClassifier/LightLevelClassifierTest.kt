package com.ambientbrightness.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Boundary tests for [LightLevelClassifier] (Decision D10, AC3.1, AC3.3, AC3.4).
 *
 * The classifier is the only place that turns a stabilised lux value into the qualitative level
 * every consumer displays, so every interval boundary is asserted from both sides.
 */
class LightLevelClassifierTest {

    @ParameterizedTest(name = "{0} lux -> {1}")
    @CsvSource(
        "0.0, DARK",
        "0.001, DARK",
        "9.999, DARK",
        "10.0, DIM",
        "10.001, DIM",
        "50.0, DIM",
        "99.999, DIM",
        "100.0, INDOOR",
        "100.001, INDOOR",
        "999.999, INDOOR",
        "1000.0, BRIGHT",
        "1000.001, BRIGHT",
        "9999.999, BRIGHT",
        "10000.0, FULL_SUN",
        "10000.001, FULL_SUN",
        "100000.0, FULL_SUN",
    )
    fun `classifies lux at and around every level boundary (AC3_1)`(lux: Float, expected: LightLevel) {
        assertEquals(expected, LightLevelClassifier.classify(lux))
    }

    @Test
    fun `AC3_3 - 0 lux is a valid very-low-light reading classified as DARK`() {
        assertEquals(LightLevel.DARK, LightLevelClassifier.classify(0f))
    }

    @Test
    fun `AC3_4 - negative lux is UNKNOWN because clamping happens before classification`() {
        assertEquals(LightLevel.UNKNOWN, LightLevelClassifier.classify(-0.001f))
        assertEquals(LightLevel.UNKNOWN, LightLevelClassifier.classify(-1f))
        assertEquals(LightLevel.UNKNOWN, LightLevelClassifier.classify(Float.NEGATIVE_INFINITY))
    }

    @Test
    fun `every interval boundary is lower-inclusive and upper-exclusive`() {
        assertEquals(LightLevel.DARK, LightLevelClassifier.classify(0f))
        assertEquals(
            LightLevel.DIM,
            LightLevelClassifier.classify(LightLevelClassifier.DARK_UPPER_BOUND_LUX),
        )
        assertEquals(
            LightLevel.INDOOR,
            LightLevelClassifier.classify(LightLevelClassifier.DIM_UPPER_BOUND_LUX),
        )
        assertEquals(
            LightLevel.BRIGHT,
            LightLevelClassifier.classify(LightLevelClassifier.INDOOR_UPPER_BOUND_LUX),
        )
        assertEquals(
            LightLevel.FULL_SUN,
            LightLevelClassifier.classify(LightLevelClassifier.BRIGHT_UPPER_BOUND_LUX),
        )
    }

    @Test
    fun `the largest representable lux value is FULL_SUN`() {
        assertEquals(LightLevel.FULL_SUN, LightLevelClassifier.classify(Float.MAX_VALUE))
    }

    @Test
    fun `not-a-number is UNKNOWN`() {
        assertEquals(LightLevel.UNKNOWN, LightLevelClassifier.classify(Float.NaN))
    }

    @Test
    fun `positive infinity is UNKNOWN`() {
        assertEquals(LightLevel.UNKNOWN, LightLevelClassifier.classify(Float.POSITIVE_INFINITY))
    }
}
