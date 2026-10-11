package com.ambientbrightness.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAmbientLightServiceTest {

    private val startupTimeoutMillis = 2_000L
    private val samplingIntervalMillis = 500L

    private fun service(client: FakeAmbientLightSensorClient): DefaultAmbientLightService {
        val useCase = AmbientLightUseCase(
            sensorClient = client,
            stabiliser = FakeReadingStabiliser(),
            clock = FakeClock(),
            samplingIntervalMillis = samplingIntervalMillis,
        )
        return DefaultAmbientLightService(client, useCase, startupTimeoutMillis)
    }

    @Test
    fun `AC2_1 - no sensor is surfaced without registering or emitting`() = runTest {
        val client = FakeAmbientLightSensorClient(
            SensorAvailability.Unavailable(SensorUnavailableReason.NoSensor),
        )
        val service = service(client)
        val emissions = mutableListOf<LuxSample>()

        service.start().collect { emissions += it }

        assertEquals(0, client.registerCalls)
        assertTrue(emissions.isEmpty())
        assertEquals(
            SensorAvailability.Unavailable(SensorUnavailableReason.NoSensor),
            service.availability.value,
        )
    }

    @Test
    fun `AC2_2 - available is published only after the first reading and keeps exact profile`() =
        runTest {
            val profile = SensorProfile("Ambient Light Sensor", 1234.5f, 25000)
            val client = FakeAmbientLightSensorClient(SensorAvailability.Available(profile))
            val service = service(client)
            val emissions = mutableListOf<LuxSample>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) {
                service.start().collect { emissions += it }
            }
            runCurrent()

            assertTrue(service.availability.value is SensorAvailability.Unavailable)
            client.push(75f)
            advanceTimeBy(samplingIntervalMillis)
            runCurrent()

            assertEquals(SensorAvailability.Available(profile), service.availability.value)
            assertEquals(75f, emissions.single().lux)
            job.cancelAndJoin()
        }

    @Test
    fun `AC2_3 - silent sensor times out as not usable and unregisters`() = runTest {
        val client = FakeAmbientLightSensorClient(SensorAvailability.Available(
            SensorProfile("Silent sensor", 1000f, 0),
        ))
        val service = service(client)
        val emissions = mutableListOf<LuxSample>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            service.start().collect { emissions += it }
        }
        runCurrent()

        advanceTimeBy(startupTimeoutMillis)
        runCurrent()

        assertEquals(
            SensorAvailability.Unavailable(SensorUnavailableReason.NotUsable),
            service.availability.value,
        )
        assertEquals(SensorUnavailableReason.NotUsable.defaultUserMessage,
            (service.availability.value as SensorAvailability.Unavailable).userMessage)
        assertTrue(emissions.isEmpty())
        assertFalse(client.isRegistered)
        job.join()
    }

    @Test
    fun `zero lux is a valid first reading`() = runTest {
        val profile = SensorProfile("Darkness sensor", 500f, 0)
        val client = FakeAmbientLightSensorClient(SensorAvailability.Available(profile))
        val service = service(client)
        val emissions = mutableListOf<LuxSample>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            service.start().collect { emissions += it }
        }
        runCurrent()

        client.push(0f)
        advanceTimeBy(samplingIntervalMillis)
        runCurrent()

        assertEquals(SensorAvailability.Available(profile), service.availability.value)
        assertEquals(0f, emissions.single().lux)
        job.cancelAndJoin()
    }

    @Test
    fun `cancellation and stop unregister the active sensor`() = runTest {
        val client = FakeAmbientLightSensorClient(
            SensorAvailability.Available(SensorProfile("Sensor", 800f, 0)),
        )
        val service = service(client)
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { service.start().collect { } }
        runCurrent()

        service.stop()
        job.cancelAndJoin()

        assertFalse(client.isRegistered)
        assertTrue(client.unregisterCalls >= 1)
        service.stop()
        assertFalse(client.isRegistered)
    }
}
