package com.ambientbrightness.infrastructure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SensorProfileFactoryTest {

    @Test
    fun `valid values are kept as reported`() {
        val profile = SensorProfileFactory.create(
            name = "test-light",
            maxRangeLux = 12_000f,
            minDelayMicros = 1_000,
        )

        assertNotNull(profile)
        assertEquals("test-light", profile!!.name)
        assertEquals(12_000f, profile.maxRangeLux, 0.0f)
        assertEquals(1_000, profile.minDelayMicros)
    }

    @Test
    fun `zero maximum range is unusable`() {
        assertNull(SensorProfileFactory.create("test-light", maxRangeLux = 0f, minDelayMicros = 1_000))
    }

    @Test
    fun `negative maximum range is unusable`() {
        assertNull(SensorProfileFactory.create("test-light", maxRangeLux = -1f, minDelayMicros = 1_000))
    }

    @Test
    fun `negative minimum delay is clamped to zero`() {
        val profile = SensorProfileFactory.create("test-light", maxRangeLux = 100f, minDelayMicros = -5)

        assertNotNull(profile)
        assertEquals(0, profile!!.minDelayMicros)
    }

    @Test
    fun `name is preserved verbatim`() {
        val profile = SensorProfileFactory.create("  vendor sensor #7  ", maxRangeLux = 1f, minDelayMicros = 0)

        assertEquals("  vendor sensor #7  ", profile!!.name)
    }
}
