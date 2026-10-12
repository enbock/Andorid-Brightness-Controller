package com.ambientbrightness.core

import com.ambientbrightness.core.AmbientLightSensing.AmbientLightSensorClient
import com.ambientbrightness.core.AmbientLightSensing.AmbientLightService.AmbientLightService
import com.ambientbrightness.core.AmbientLightSensing.LuxSample
import com.ambientbrightness.core.AmbientLightSensing.ScreenStateClient
import com.ambientbrightness.core.AmbientLightSensing.SensorAvailability
import com.ambientbrightness.core.AmbientLightSensing.SensorProfile
import com.ambientbrightness.core.AmbientLightSensing.SensorUnavailableReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Locks the T1 boundary shapes that F2/F4/F5/F7 are written against: these fakes are the smallest
 * possible implementations of the four `:core` contracts, so the test compiles only while the
 * shapes stay compatible.
 */
class AmbientLightServiceContractTest {

    private val profile = SensorProfile(name = "TMD3702", maxRangeLux = 40_000f, minDelayMicros = 1_000)

    private class TestSensorClient(
        override val availability: SensorAvailability,
    ) : AmbientLightSensorClient {
        private var listener: ((Float) -> Unit)? = null

        val isRegistered: Boolean get() = listener != null

        override fun register(onLux: (Float) -> Unit) {
            listener = onLux
        }

        override fun unregister() {
            listener = null
        }

        fun emit(lux: Float) {
            listener?.invoke(lux)
        }
    }

    private class TestScreenStateClient(
        override val isScreenOn: StateFlow<Boolean>,
    ) : ScreenStateClient

    private class TestService(
        override val availability: MutableStateFlow<SensorAvailability>,
    ) : AmbientLightService {
        override fun start(): Flow<LuxSample> = flowOf(LuxSample(lux = 12f, timestampMillis = 7L))

        override fun stop() = Unit
    }

    @Test
    fun `AC2_2 - availability is a StateFlow carrying the current state before start`() {
        val availability: StateFlow<SensorAvailability> =
            MutableStateFlow(SensorAvailability.Available(profile))
        val service = TestService(availability as MutableStateFlow<SensorAvailability>)

        assertEquals(SensorAvailability.Available(profile), service.availability.value)
    }

    @Test
    fun `start is a cold flow whose collection yields the stabilised samples`() = runBlocking {
        val service = TestService(MutableStateFlow(SensorAvailability.Unavailable(SensorUnavailableReason.NoSensor)))

        assertEquals(listOf(LuxSample(lux = 12f, timestampMillis = 7L)), service.start().toList())
    }

    @Test
    fun `AC1_3 - unregister stops raw lux callbacks`() {
        val client = TestSensorClient(SensorAvailability.Available(profile))
        val received = mutableListOf<Float>()

        client.register { received += it }
        client.emit(5f)
        client.unregister()
        client.emit(50f)

        assertEquals(listOf(5f), received)
    }

    @Test
    fun `AC1_4 - screen state is observable as a hot boolean StateFlow`() {
        val screenState = MutableStateFlow(true)
        val client: ScreenStateClient = TestScreenStateClient(screenState)

        assertTrue(client.isScreenOn.value)

        screenState.value = false
        assertFalse(client.isScreenOn.value)
    }
}
