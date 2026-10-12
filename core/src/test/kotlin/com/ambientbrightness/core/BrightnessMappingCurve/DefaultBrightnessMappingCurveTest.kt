package com.ambientbrightness.core.BrightnessMappingCurve

import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessMappingCurve.DefaultBrightnessMappingCurve
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class DefaultBrightnessMappingCurveTest {
    private val curve = DefaultBrightnessMappingCurve()

    @Test
    fun `AC3_1 AC3_3 - returns the approved exact anchor fractions`() {
        listOf(
            0f to 0.10f,
            1f to 0.12f,
            10f to 0.18f,
            100f to 0.35f,
            1_000f to 0.60f,
            10_000f to 0.85f,
            100_000f to 1.00f,
        ).forEach { (lux, expected) ->
            assertEquals(expected, curve.toFraction(lux), 0.000001f, "$lux lux")
        }
    }

    @Test
    fun `AC3_3 - zero maps to readable floor and values above anchor saturate`() {
        assertEquals(0.10f, curve.toFraction(0f))
        assertEquals(1f, curve.toFraction(100_000f))
        assertEquals(1f, curve.toFraction(Float.MAX_VALUE))
        assertEquals(1f, curve.toFraction(Float.POSITIVE_INFINITY))
    }

    @Test
    fun `AC3_1 - interpolates linearly in transformed log-lux coordinates`() {
        val midpointInTransformedSpace = (kotlin.math.sqrt(22.0) - 1.0).toFloat()
        assertEquals(0.15f, curve.toFraction(midpointInTransformedSpace), 0.00001f)
    }

    @Test
    fun `AC3_1 - is nondecreasing for ordered samples and randomized valid lux pairs`() {
        val values = (0..10_000).map { it * 10f } +
            List(2_000) { Random(it).nextFloat() * Float.MAX_VALUE }
        val ordered = values.sorted()
        ordered.zipWithNext().forEach { (lower, higher) ->
            assertTrue(curve.toFraction(lower) <= curve.toFraction(higher))
        }
        repeat(5_000) { seed ->
            val random = Random(seed)
            val lower = random.nextFloat() * 100_000f
            val higher = lower + random.nextFloat() * (Float.MAX_VALUE - lower)
            assertTrue(curve.toFraction(lower) <= curve.toFraction(higher))
        }
    }

    @Test
    fun `AC3_1 - every finite nonnegative input remains within normalized bounds`() {
        val samples = listOf(0f, Float.MIN_VALUE, 0.5f, 1f, 999f, 100_000f, Float.MAX_VALUE) +
            List(5_000) { Random(it).nextFloat() * Float.MAX_VALUE }
        samples.forEach { lux ->
            val fraction = curve.toFraction(lux)
            assertTrue(fraction in 0f..1f, "lux=$lux mapped to $fraction")
        }
    }

    @Test
    fun `invalid inputs safely map low invalid values to floor`() {
        assertEquals(0.10f, curve.toFraction(-1f))
        assertEquals(0.10f, curve.toFraction(Float.NEGATIVE_INFINITY))
        assertEquals(0.10f, curve.toFraction(Float.NaN))
    }
}
