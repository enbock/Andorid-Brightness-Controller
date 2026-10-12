package com.ambientbrightness.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ambientbrightness.app.Composition.AmbientBrightnessComponent
import com.ambientbrightness.core.AmbientLightSensing.AmbientLightSensorClient
import com.ambientbrightness.core.AmbientLightSensing.LuxSample
import com.ambientbrightness.core.AmbientLightSensing.ScreenStateClient
import com.ambientbrightness.core.AmbientLightSensing.SensorAvailability
import com.ambientbrightness.core.AmbientLightSensing.SensorProfile
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessStatus
import com.ambientbrightness.core.AutomaticBrightnessControl.BuiltInAdaptiveBrightnessState
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the app composition/controller boundary without relying on emulator sensor hardware. */
@RunWith(AndroidJUnit4::class)
class AmbientBrightnessComponentTest {

    @Test
    fun stopUnregistersSensorAndPreventsFurtherSamples() = runBlocking {
        val sensor = ControllableAmbientLightSensorClient()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val component = AmbientBrightnessComponent(sensor, MutableScreenStateClient(true), scope)
        val receivedSamples = CopyOnWriteArrayList<LuxSample>()
        val collection = component.collector.collect(
            scope = scope,
            onSample = {
                receivedSamples += it
            },
            onAvailability = {},
        )

        try {
            sendUntilSample(sensor, receivedSamples, 42f)

            component.stop()
            collection.cancel()
            assertFalse("stop unregisters the sensor callback", sensor.isRegistered)
            val countAtStop = receivedSamples.size
            sensor.emit(84f)
            delay(SAMPLE_SETTLE_MILLIS)
            assertTrue("the active sensor produced a sample", countAtStop > 0)
            assertEquals("no sample is forwarded after stop", countAtStop, receivedSamples.size)
        } finally {
            component.stop()
            collection.cancel()
            scope.cancel()
        }
    }

    @Test
    fun screenOffSuppressesSamplesAndScreenOnRestartsCollection() = runBlocking {
        val sensor = ControllableAmbientLightSensorClient()
        val screenState = MutableScreenStateClient(true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val component = AmbientBrightnessComponent(sensor, screenState, scope)
        val receivedSamples = CopyOnWriteArrayList<LuxSample>()
        val collection = component.collector.collect(
            scope = scope,
            onSample = {
                receivedSamples += it
            },
            onAvailability = {},
        )

        try {
            sendUntilSample(sensor, receivedSamples, 30f)
            screenState.setScreenOn(false)
            delay(STATE_CHANGE_SETTLE_MILLIS)

            val countWhileOff = receivedSamples.size
            assertFalse("screen-off cancels sensor registration", sensor.isRegistered)
            sensor.emit(60f)
            delay(SAMPLE_SETTLE_MILLIS)
            assertEquals("screen-off readings are not forwarded", countWhileOff, receivedSamples.size)

            screenState.setScreenOn(true)
            delay(STATE_CHANGE_SETTLE_MILLIS)
            val previousCount = receivedSamples.size
            sendUntilSample(sensor, receivedSamples, 90f, previousCount)
            assertTrue("collection resumes when screen turns on", receivedSamples.size > previousCount)
        } finally {
            component.stop()
            collection.cancel()
            scope.cancel()
        }
    }

    /** Starts the controller through the composition root and confirms the collector forwards both streams. */
    @Test
    fun controllerStatusAndAdaptiveBrightnessConflictAreCollectable() = runBlocking {
        val sensor = ControllableAmbientLightSensorClient()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val component = AmbientBrightnessComponent(sensor, MutableScreenStateClient(true), scope)
        val statuses = CopyOnWriteArrayList<BrightnessStatus>()
        val conflicts = CopyOnWriteArrayList<BuiltInAdaptiveBrightnessState>()
        val collection = component.statusCollector.collect(
            scope = scope,
            onStatus = { statuses += it },
            onAdaptiveBrightnessConflict = { conflicts += it },
        )

        try {
            component.controller.start()
            withTimeout(TEST_TIMEOUT_MILLIS) {
                while (statuses.none { it.isRunning }) {
                    delay(20L)
                }
            }

            assertTrue("the running status is forwarded", statuses.any { it.isRunning })
            assertTrue("the built-in mode is forwarded", conflicts.isNotEmpty())
        } finally {
            component.controller.stop()
            collection.cancel()
            scope.cancel()
        }
    }

    private suspend fun sendUntilSample(
        sensor: ControllableAmbientLightSensorClient,
        receivedSamples: List<LuxSample>,
        lux: Float,
        previousCount: Int = receivedSamples.size,
    ) {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            while (receivedSamples.size == previousCount) {
                sensor.emit(lux)
                delay(20L)
            }
        }
    }

    private class ControllableAmbientLightSensorClient : AmbientLightSensorClient {
        override val availability = SensorAvailability.Available(
            SensorProfile(
                name = "test-light-sensor",
                maxRangeLux = 10_000f,
                minDelayMicros = 0,
            ),
        )

        private var callback: ((Float) -> Unit)? = null
        val isRegistered: Boolean
            get() = callback != null

        override fun register(onLux: (Float) -> Unit) {
            callback = onLux
        }

        override fun unregister() {
            callback = null
        }

        fun emit(lux: Float) {
            callback?.invoke(lux)
        }
    }

    private class MutableScreenStateClient(initiallyOn: Boolean) : ScreenStateClient {
        private val state = MutableStateFlow(initiallyOn)
        override val isScreenOn = state

        fun setScreenOn(isOn: Boolean) {
            state.value = isOn
        }
    }

    private companion object {
        const val TEST_TIMEOUT_MILLIS = 5_000L
        const val SAMPLE_SETTLE_MILLIS = 650L
        const val STATE_CHANGE_SETTLE_MILLIS = 100L
    }
}
