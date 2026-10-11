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

/**
 * Tests for the throttled collection use case (AC1.1 - AC1.3). Time is virtual, so the tests are
 * deterministic and never sleep for real.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AmbientLightUseCaseTest {

    private val samplingIntervalMillis = 500L

    private fun useCase(
        sensorClient: FakeAmbientLightSensorClient,
        stabiliser: FakeReadingStabiliser,
    ) = AmbientLightUseCase(
        sensorClient = sensorClient,
        stabiliser = stabiliser,
        clock = FakeClock(),
        samplingIntervalMillis = samplingIntervalMillis,
    )

    @Test
    fun `AC1_2 - at most one value is processed and emitted per sampling interval when the sensor floods`() =
        runTest {
            val sensorClient = FakeAmbientLightSensorClient()
            val stabiliser = FakeReadingStabiliser()
            val emissions = mutableListOf<LuxSample>()

            val job = launch(UnconfinedTestDispatcher(testScheduler)) {
                useCase(sensorClient, stabiliser).samples().collect { emissions += it }
            }
            runCurrent()

            sensorClient.push(10f)
            sensorClient.push(20f)
            sensorClient.push(30f)
            advanceTimeBy(samplingIntervalMillis)
            runCurrent()

            assertEquals(1, emissions.size)
            assertEquals(1, stabiliser.received.size)

            sensorClient.push(40f)
            advanceTimeBy(samplingIntervalMillis)
            runCurrent()

            assertEquals(2, emissions.size)
            assertEquals(2, stabiliser.received.size)

            job.cancelAndJoin()
        }

    @Test
    fun `AC1_1 - a single reading is emitted within one sampling interval`() = runTest {
        val sensorClient = FakeAmbientLightSensorClient()
        val stabiliser = FakeReadingStabiliser()
        val emissions = mutableListOf<LuxSample>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase(sensorClient, stabiliser).samples().collect { emissions += it }
        }
        runCurrent()

        sensorClient.push(42f)
        advanceTimeBy(samplingIntervalMillis)
        runCurrent()

        assertEquals(1, emissions.size)
        assertEquals(42f, emissions.single().lux)

        job.cancelAndJoin()
    }

    @Test
    fun `a rejected stabiliser result emits nothing`() = runTest {
        val sensorClient = FakeAmbientLightSensorClient()
        val stabiliser = FakeReadingStabiliser(resultProvider = { null })
        val emissions = mutableListOf<LuxSample>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase(sensorClient, stabiliser).samples().collect { emissions += it }
        }
        runCurrent()

        sensorClient.push(50f)
        advanceTimeBy(samplingIntervalMillis * 3)
        runCurrent()

        assertTrue(emissions.isEmpty())
        assertEquals(1, stabiliser.received.size)

        job.cancelAndJoin()
    }

    @Test
    fun `AC1_3 - cancelling the collection unregisters the sensor exactly once`() = runTest {
        val sensorClient = FakeAmbientLightSensorClient()
        val stabiliser = FakeReadingStabiliser()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase(sensorClient, stabiliser).samples().collect { }
        }
        runCurrent()

        assertEquals(1, sensorClient.registerCalls)
        assertEquals(0, sensorClient.unregisterCalls)

        job.cancelAndJoin()

        assertEquals(1, sensorClient.unregisterCalls)
        assertFalse(sensorClient.isRegistered)
    }
}
