package com.ambientbrightness.core

import com.ambientbrightness.core.AmbientLightSensing.SensorProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Tests for the diagnostic sensor description (AC2.2, AC3.4).
 */
class SensorProfileTest {

    @Test
    fun `AC2_2 - exposes name, maximum range and minimum delay for diagnostics`() {
        val profile = SensorProfile(name = "TMD3702", maxRangeLux = 40_000f, minDelayMicros = 1_000)

        assertEquals("TMD3702", profile.name)
        assertEquals(40_000f, profile.maxRangeLux)
        assertEquals(1_000, profile.minDelayMicros)
    }

    @Test
    fun `compares by value`() {
        assertEquals(
            SensorProfile("TMD3702", 40_000f, 1_000),
            SensorProfile("TMD3702", 40_000f, 1_000),
        )
        assertNotEquals(
            SensorProfile("TMD3702", 40_000f, 1_000),
            SensorProfile("TMD3702", 40_000f, 2_000),
        )
    }
}
