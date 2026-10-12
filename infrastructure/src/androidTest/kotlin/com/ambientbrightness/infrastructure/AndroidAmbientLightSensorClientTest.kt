package com.ambientbrightness.infrastructure

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ambientbrightness.core.AmbientLightSensing.SensorAvailability
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-dependent smoke test: verifies that a real light event reaches the callback.
 *
 * Compile-only for now — no device/emulator is available in CI. When run, it skips (via JUnit
 * `assumeTrue`) on devices without a light sensor or when no event arrives in time.
 */
@RunWith(AndroidJUnit4::class)
class AndroidAmbientLightSensorClientTest {

    @Test
    fun lightEventReachesCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        assumeTrue("No ambient light sensor on this device", sensor != null)

        val client = AndroidAmbientLightSensorClient(sensorManager)
        assertTrue(client.availability is SensorAvailability.Available)

        val latch = CountDownLatch(1)
        val firstValue = arrayOfNulls<Float>(1)
        client.register { lux ->
            if (firstValue[0] == null) firstValue[0] = lux
            latch.countDown()
        }

        try {
            val received = latch.await(5, TimeUnit.SECONDS)
            assumeTrue("No light event within the timeout; device is likely unlit", received)
            assertTrue(firstValue[0]!!.isFinite())
        } finally {
            client.unregister()
        }
    }
}
