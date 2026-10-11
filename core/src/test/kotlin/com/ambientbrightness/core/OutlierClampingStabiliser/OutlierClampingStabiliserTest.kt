package com.ambientbrightness.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Tests for the outlier clamping stabiliser (AC3.1 - AC3.4).
 */
class OutlierClampingStabiliserTest {

    private fun stabiliser(
        maxRangeLux: Float = 1000f,
        consecutiveRejectionsBeforeAccept: Int = 3,
    ) = OutlierClampingStabiliser(
        maxRangeLux = maxRangeLux,
        consecutiveRejectionsBeforeAccept = consecutiveRejectionsBeforeAccept,
    )

    private fun sample(lux: Float, timestampMillis: Long = 1L) = LuxSample(lux, timestampMillis)

    @Test
    fun `AC3_4 - negative lux is clamped to zero`() {
        val stabiliser = stabiliser()
        assertEquals(0f, stabiliser.stabilise(sample(-42f))?.lux)
    }

    @Test
    fun `AC3_4 - lux above the sensor range is clamped to the maximum`() {
        val stabiliser = stabiliser(maxRangeLux = 1000f)
        assertEquals(1000f, stabiliser.stabilise(sample(10_000f))?.lux)
    }

    @Test
    fun `AC3_4 - clamping keeps the sample timestamp`() {
        val stabiliser = stabiliser()
        val result = stabiliser.stabilise(sample(lux = -5f, timestampMillis = 1234L))
        assertEquals(1234L, result?.timestampMillis)
    }

    @Test
    fun `AC3_3 - zero lux is a valid reading and is never rejected`() {
        val stabiliser = stabiliser()
        assertEquals(0f, stabiliser.stabilise(sample(0f))?.lux)
        assertEquals(0f, stabiliser.stabilise(sample(0f))?.lux)
    }

    @Test
    fun `AC3_1 - a single outlier is rejected and the previous value stays the reference`() {
        val stabiliser = stabiliser(maxRangeLux = 1000f)
        assertEquals(100f, stabiliser.stabilise(sample(100f))?.lux)

        // 500 is more than max(20, 3 * 100) = 300 away from the trend -> rejected.
        assertNull(stabiliser.stabilise(sample(500f)))

        // 401 is only accepted if the reference is still 100 (delta 301 > 300 -> rejected).
        assertNull(stabiliser.stabilise(sample(401f)))

        // A value inside the band is still accepted and does not move the emitted value by itself.
        assertEquals(110f, stabiliser.stabilise(sample(110f))?.lux)
    }

    @Test
    fun `AC3_2 - steady values inside the noise band do not jitter the output`() {
        val stabiliser = stabiliser(maxRangeLux = 1000f)
        assertEquals(100f, stabiliser.stabilise(sample(100f))?.lux)
        assertEquals(101f, stabiliser.stabilise(sample(101f))?.lux)
        assertEquals(103f, stabiliser.stabilise(sample(103f))?.lux)
        assertEquals(99f, stabiliser.stabilise(sample(99f))?.lux)
    }

    @Test
    fun `AC3_1 - recovery after the configured number of consecutive rejections`() {
        val stabiliser = stabiliser(maxRangeLux = 1000f, consecutiveRejectionsBeforeAccept = 3)
        assertEquals(100f, stabiliser.stabilise(sample(100f))?.lux)

        assertNull(stabiliser.stabilise(sample(1000f)))
        assertNull(stabiliser.stabilise(sample(1000f)))
        assertNull(stabiliser.stabilise(sample(1000f)))

        // The fourth consecutive outlier is accepted so a genuine lighting change is followed.
        val recovered = stabiliser.stabilise(sample(1000f))
        assertNotNull(recovered)
        assertEquals(1000f, recovered?.lux)
    }

    @Test
    fun `AC3_1 - recovery counter resets after an accepted value`() {
        val stabiliser = stabiliser(maxRangeLux = 10_000f, consecutiveRejectionsBeforeAccept = 1)
        assertEquals(100f, stabiliser.stabilise(sample(100f))?.lux)

        assertNull(stabiliser.stabilise(sample(500f)))
        assertEquals(500f, stabiliser.stabilise(sample(500f))?.lux)

        // After the accepted value the counter starts over: this outlier is rejected again.
        assertEquals(500f, stabiliser.stabilise(sample(500f))?.lux)
        assertNull(stabiliser.stabilise(sample(9_000f)))
    }
}
