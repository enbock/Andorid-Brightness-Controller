package com.ambientbrightness.infrastructure

import com.ambientbrightness.core.SensorAvailability
import com.ambientbrightness.core.SensorUnavailableReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DummyAmbientLightSensorClientTest {

    @Test
    fun `emit before register is not delivered`() {
        val client = DummyAmbientLightSensorClient()
        val delivered = mutableListOf<Float>()

        client.emit(10f)
        client.register { delivered += it }

        assertEquals(emptyList<Float>(), delivered)
    }

    @Test
    fun `emit after register is delivered`() {
        val client = DummyAmbientLightSensorClient()
        val delivered = mutableListOf<Float>()

        client.register { delivered += it }
        client.emit(42f)

        assertEquals(listOf(42f), delivered)
    }

    @Test
    fun `emit after unregister is not delivered`() {
        val client = DummyAmbientLightSensorClient()
        val delivered = mutableListOf<Float>()

        client.register { delivered += it }
        client.emit(1f)
        client.unregister()
        client.emit(2f)

        assertEquals(listOf(1f), delivered)
    }

    @Test
    fun `unregister is idempotent`() {
        val client = DummyAmbientLightSensorClient()
        val delivered = mutableListOf<Float>()

        client.unregister()
        client.register { delivered += it }
        client.unregister()
        client.unregister()
        client.emit(7f)

        assertEquals(emptyList<Float>(), delivered)
    }

    @Test
    fun `forced unavailable client reports NoSensor and delivers nothing`() {
        val client = DummyAmbientLightSensorClient()
        client.available = false
        val delivered = mutableListOf<Float>()

        client.register { delivered += it }
        client.emit(99f)

        assertEquals(
            SensorAvailability.Unavailable(SensorUnavailableReason.NoSensor),
            client.availability,
        )
        assertEquals(emptyList<Float>(), delivered)
    }

    @Test
    fun `available client exposes the configured profile`() {
        val client = DummyAmbientLightSensorClient()

        val availability = client.availability
        assertTrue(availability is SensorAvailability.Available)
        assertEquals("dummy-light-sensor", (availability as SensorAvailability.Available).profile.name)
    }
}
