package com.ambientbrightness.app

import com.ambientbrightness.app.StatusDashboard.Adapter.DashboardStateCollector
import com.ambientbrightness.app.StatusDashboard.View.DashboardSnapshot
import com.ambientbrightness.app.StatusDashboard.View.Task.LuxSampler
import com.ambientbrightness.app.testdoubles.FakeAmbientLightService
import com.ambientbrightness.app.testdoubles.FakeAppSetupService
import com.ambientbrightness.app.testdoubles.FakeBrightnessBackgroundService
import com.ambientbrightness.app.testdoubles.FakeBrightnessOffsetSource
import com.ambientbrightness.app.testdoubles.FakeResponseTuningService
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.BackgroundService.BackgroundServiceState
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffsetApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The collector must read the five services into one snapshot and must collect ambient light only
 * while the controller is active, so a stopped controller never shows a stale reading (US1, AC1.4).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardStateCollectorTest {

    @Test
    fun `a stopped controller collects no light and blanks the readout`() = runTest {
        val ambient = FakeAmbientLightService()
        val service = FakeBrightnessBackgroundService(BackgroundServiceState.Stopped)
        val snapshots = mutableListOf<DashboardSnapshot>()
        val job = launch { collector(ambient, service).snapshots().collect { snapshots += it } }
        runCurrent()

        ambient.emitLux(500f)
        runCurrent()

        assertEquals("the sensor must not be listened to while stopped", 0, ambient.activeCollections)
        assertTrue("the collector must have produced a snapshot", snapshots.isNotEmpty())
        assertEquals(null, snapshots.last().latestLux)
        job.cancel()
    }

    @Test
    fun `light is collected only while running and released again on stop`() = runTest {
        val ambient = FakeAmbientLightService()
        val service = FakeBrightnessBackgroundService(BackgroundServiceState.Stopped)
        val snapshots = mutableListOf<DashboardSnapshot>()
        val job = launch { collector(ambient, service).snapshots().collect { snapshots += it } }
        runCurrent()

        service.setState(BackgroundServiceState.Running(BrightnessValue(10)))
        runCurrent()
        assertEquals(1, ambient.activeCollections)

        ambient.emitLux(750f)
        runCurrent()
        assertEquals(750f, snapshots.last().latestLux)

        service.setState(BackgroundServiceState.Stopped)
        runCurrent()
        assertEquals("the listener must be released when the controller stops", 0, ambient.activeCollections)
        assertEquals("a stopped controller must not show a stale reading", null, snapshots.last().latestLux)
        job.cancel()
    }

    @Test
    fun `the snapshot carries the values of every service`() = runTest {
        val ambient = FakeAmbientLightService()
        val service = FakeBrightnessBackgroundService(BackgroundServiceState.Running(BrightnessValue(120)))
        val setup = FakeAppSetupService(readyToStart = false)
        val offset = FakeBrightnessOffsetSource(
            offsetPercent = -17,
            application = BrightnessOffsetApplication.ClampedToMinimum,
        )
        val tuning = FakeResponseTuningService(speedPercent = 80)

        val snapshots = mutableListOf<DashboardSnapshot>()
        val job = launch {
            DashboardStateCollector(
                ambientLightService = ambient,
                backgroundService = service,
                setupService = setup,
                brightnessOffsetSource = offset,
                responseTuningService = tuning,
                sampler = PASS_THROUGH,
            ).snapshots().collect { snapshots += it }
        }
        runCurrent()
        ambient.emitLux(123f)
        runCurrent()

        val snapshot = snapshots.last()
        assertEquals(BackgroundServiceState.Running(BrightnessValue(120)), snapshot.serviceState)
        assertEquals(123f, snapshot.latestLux)
        assertEquals(false, snapshot.canStart)
        assertEquals(-17, snapshot.offsetPercent)
        assertEquals(BrightnessOffsetApplication.ClampedToMinimum, snapshot.offsetApplication)
        assertEquals(80, snapshot.responseSpeedPercent)
        job.cancel()
    }

    private fun collector(
        ambient: FakeAmbientLightService,
        service: FakeBrightnessBackgroundService,
    ) = DashboardStateCollector(
        ambientLightService = ambient,
        backgroundService = service,
        setupService = FakeAppSetupService(),
        brightnessOffsetSource = FakeBrightnessOffsetSource(),
        responseTuningService = FakeResponseTuningService(),
        sampler = PASS_THROUGH,
    )

    private companion object {

        /** Skips the display-rate sampling so the tests need no wall-clock waiting. */
        val PASS_THROUGH = LuxSampler { source -> source }
    }
}
