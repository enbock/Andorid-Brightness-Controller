package com.ambientbrightness.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for the availability states surfaced to the user (AC2.1–AC2.3).
 */
class SensorAvailabilityTest {

    private val profile = SensorProfile(name = "TMD3702", maxRangeLux = 40_000f, minDelayMicros = 1_000)

    @Test
    fun `AC2_1 - a device without a sensor surfaces Unavailable(NoSensor) with a plain message`() {
        val availability: SensorAvailability =
            SensorAvailability.Unavailable(reason = SensorUnavailableReason.NoSensor)

        val unavailable = availability as SensorAvailability.Unavailable
        assertEquals(SensorUnavailableReason.NoSensor, unavailable.reason)
        assertTrue(unavailable.userMessage.isNotBlank())
        assertFalse(unavailable.userMessage.contains("SensorManager"))
        assertFalse(unavailable.userMessage.contains("getDefaultSensor"))
        assertFalse(unavailable.userMessage.contains("null"))
    }

    @Test
    fun `AC2_2 - an available sensor reports its profile for diagnostics`() {
        val availability: SensorAvailability = SensorAvailability.Available(profile)

        val available = availability as SensorAvailability.Available
        assertEquals(profile, available.profile)
        assertEquals("TMD3702", available.profile.name)
        assertEquals(40_000f, available.profile.maxRangeLux)
        assertEquals(1_000, available.profile.minDelayMicros)
    }

    @Test
    fun `AC2_3 - the startup-timeout outcome is the same Unavailable state as a missing sensor`() {
        val silentSensor: SensorAvailability =
            SensorAvailability.Unavailable(reason = SensorUnavailableReason.NotUsable)
        val noSensor: SensorAvailability =
            SensorAvailability.Unavailable(reason = SensorUnavailableReason.NoSensor)

        assertEquals(SensorUnavailableReason.NotUsable, (silentSensor as SensorAvailability.Unavailable).reason)
        assertTrue(silentSensor.userMessage.isNotBlank())
        assertNotEquals(
            (noSensor as SensorAvailability.Unavailable).reason,
            silentSensor.reason,
            "the two cases must stay distinguishable for diagnostics even though both are Unavailable",
        )
        assertNotEquals(noSensor.userMessage, silentSensor.userMessage)
    }

    @Test
    fun `Unavailable carries an explicit caller-supplied user-facing message when provided`() {
        val availability = SensorAvailability.Unavailable(
            reason = SensorUnavailableReason.NotUsable,
            userMessage = "Custom explanation",
        )

        assertEquals("Custom explanation", availability.userMessage)
    }

    @Test
    fun `availability states compare by value`() {
        assertEquals(SensorAvailability.Available(profile), SensorAvailability.Available(profile))
        assertNotEquals(
            SensorAvailability.Available(profile),
            SensorAvailability.Unavailable(reason = SensorUnavailableReason.NoSensor),
        )
    }
}
