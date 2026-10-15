package com.ambientbrightness.app

import com.ambientbrightness.app.StatusDashboard.Adapter.DashboardStateCollector
import com.ambientbrightness.app.StatusDashboard.Controller.ControllerCommandHandler
import com.ambientbrightness.app.StatusDashboard.View.ControllerUiState
import com.ambientbrightness.app.StatusDashboard.View.DashboardSnapshot
import com.ambientbrightness.app.StatusDashboard.View.DashboardUiState
import com.ambientbrightness.app.StatusDashboard.View.DashboardViewModel
import com.ambientbrightness.app.StatusDashboard.View.LightReadout
import com.ambientbrightness.app.StatusDashboard.View.Task.LuxSampler
import com.ambientbrightness.app.testdoubles.FakeAmbientLightService
import com.ambientbrightness.app.testdoubles.FakeAppSetupService
import com.ambientbrightness.app.testdoubles.FakeBrightnessBackgroundService
import com.ambientbrightness.app.testdoubles.FakeBrightnessOffsetSource
import com.ambientbrightness.app.testdoubles.FakeDashboardStateProvider
import com.ambientbrightness.app.testdoubles.FakeResponseTuningService
import com.ambientbrightness.core.AmbientLightSensing.LightLevel.LightLevel
import com.ambientbrightness.core.AmbientLightSensing.SensorAvailability
import com.ambientbrightness.core.AmbientLightSensing.SensorProfile
import com.ambientbrightness.core.AppSetupService.SetupStatus
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.BackgroundService.BackgroundServiceState
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffsetApplication
import com.ambientbrightness.core.ResponseTuning.ResponseSpeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** The dashboard ViewModel publishes the mapped state and drives Start/Stop (US2, US3). */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `US2 AC1 - start shows Starting at once and resolves to Running from the service`() =
        runTest(dispatcher) {
            val service = FakeBrightnessBackgroundService(BackgroundServiceState.Stopped)
            val viewModel = viewModel(service)
            val subscription = launch { viewModel.uiState.collect {} }
            runCurrent()

            viewModel.start()
            runCurrent()

            assertEquals("start must reach the service", 1, service.startCalls)
            assertEquals(
                "the user must see the start immediately",
                ControllerUiState.Starting,
                viewModel.uiState.value.controller,
            )

            service.setState(BackgroundServiceState.Running(BrightnessValue(77)))
            runCurrent()

            assertEquals(
                ControllerUiState.Running(BrightnessValue(77)),
                viewModel.uiState.value.controller,
            )
            subscription.cancel()
            settleSharing()
        }

    @Test
    fun `US2 AC2 - stop reaches the service`() = runTest(dispatcher) {
        val service = FakeBrightnessBackgroundService(BackgroundServiceState.Running(BrightnessValue(10)))
        val viewModel = viewModel(service)
        val subscription = launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.stop()
        runCurrent()

        assertEquals(1, service.stopCalls)
        assertEquals(ControllerUiState.Stopped, viewModel.uiState.value.controller)
        subscription.cancel()
        settleSharing()
    }

    @Test
    fun `US3 AC2 - a failed write flips the dashboard to the error state`() = runTest(dispatcher) {
        val service = FakeBrightnessBackgroundService(BackgroundServiceState.Running(BrightnessValue(10)))
        val viewModel = viewModel(service)
        val subscription = launch { viewModel.uiState.collect {} }
        runCurrent()

        service.setState(BackgroundServiceState.Failed("WRITE_SETTINGS revoked"))
        runCurrent()

        assertEquals(
            ControllerUiState.Failed("WRITE_SETTINGS revoked"),
            viewModel.uiState.value.controller,
        )
        subscription.cancel()
        settleSharing()
    }

    @Test
    fun `US1 AC4 - a reading is shown only while running`() = runTest(dispatcher) {
        val ambient = FakeAmbientLightService()
        val service = FakeBrightnessBackgroundService(BackgroundServiceState.Stopped)
        val viewModel = viewModel(service, ambient)
        val subscription = launch { viewModel.uiState.collect {} }
        runCurrent()

        ambient.emitLux(5_000f)
        runCurrent()
        assertEquals(LightReadout.Unavailable, viewModel.uiState.value.light)

        service.setState(BackgroundServiceState.Running(BrightnessValue(10)))
        runCurrent()
        ambient.emitLux(5_000f)
        runCurrent()
        assertEquals(
            LightReadout.Reading(lux = 5_000f, level = LightLevel.BRIGHT),
            viewModel.uiState.value.light,
        )
        subscription.cancel()
        settleSharing()
    }

    @Test
    fun `the published state is the mapped snapshot`() = runTest(dispatcher) {
        val provider = FakeDashboardStateProvider(
            snapshot(
                serviceState = BackgroundServiceState.Running(BrightnessValue(9)),
                offsetPercent = -20,
                offsetApplication = BrightnessOffsetApplication.ClampedToMinimum,
                responseSpeedPercent = 70,
            ),
        )
        val viewModel = DashboardViewModel(
            provider = provider,
            commandHandler = ControllerCommandHandler(FakeBrightnessBackgroundService()),
        )
        val subscription = launch { viewModel.uiState.collect {} }
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(ControllerUiState.Running(BrightnessValue(9)), state.controller)
        assertEquals(-20, state.offsetPercent)
        assertEquals(70, state.responseSpeedPercent)
        assertEquals(
            com.ambientbrightness.app.StatusDashboard.View.ClampHint.Limited(atMaximum = false),
            state.clampHint,
        )
        subscription.cancel()
        settleSharing()
    }

    @Test
    fun `no snapshot is collected until the screen subscribes and it stops again afterwards`() =
        runTest(dispatcher) {
            val provider = FakeDashboardStateProvider(snapshot())
            val viewModel = DashboardViewModel(
                provider = provider,
                commandHandler = ControllerCommandHandler(FakeBrightnessBackgroundService()),
            )

            assertEquals("nothing may be collected without a subscriber", 0, provider.activeSubscriptions)

            val subscription = launch { viewModel.uiState.collect {} }
            runCurrent()
            assertEquals(1, provider.activeSubscriptions)

            subscription.cancel()
            settleSharing()
            assertEquals(0, provider.activeSubscriptions)
        }

    @Test
    fun `the initial state claims nothing about the service`() = runTest(dispatcher) {
        val provider = FakeDashboardStateProvider(snapshot())
        val viewModel = DashboardViewModel(
            provider = provider,
            commandHandler = ControllerCommandHandler(FakeBrightnessBackgroundService()),
        )

        val initial: DashboardUiState = viewModel.uiState.value

        assertEquals(ControllerUiState.Stopped, initial.controller)
        assertEquals(LightReadout.Unavailable, initial.light)
        assertEquals(null, initial.setupSummary)
        assertEquals(0, initial.offsetPercent)
        assertEquals(ResponseSpeed.DEFAULT, initial.responseSpeedPercent)
    }

    /** Lets the `WhileSubscribed` sharing stop once the last subscriber is gone. */
    private suspend fun kotlinx.coroutines.test.TestScope.settleSharing() {
        advanceTimeBy(SHARING_STOP_TIMEOUT_MILLIS + 1L)
        runCurrent()
    }

    private fun viewModel(
        service: FakeBrightnessBackgroundService,
        ambient: FakeAmbientLightService = FakeAmbientLightService(),
    ): DashboardViewModel {
        val collector = DashboardStateCollector(
            ambientLightService = ambient,
            backgroundService = service,
            setupService = FakeAppSetupService(),
            brightnessOffsetSource = FakeBrightnessOffsetSource(),
            responseTuningService = FakeResponseTuningService(),
            sampler = LuxSampler { source -> source },
        )
        return DashboardViewModel(
            provider = collector,
            commandHandler = ControllerCommandHandler(service),
        )
    }

    private fun snapshot(
        serviceState: BackgroundServiceState = BackgroundServiceState.Stopped,
        offsetPercent: Int = 0,
        offsetApplication: BrightnessOffsetApplication = BrightnessOffsetApplication.NotEvaluated,
        responseSpeedPercent: Int = ResponseSpeed.DEFAULT,
    ) = DashboardSnapshot(
        serviceState = serviceState,
        availability = SensorAvailability.Available(
            SensorProfile(name = "fake sensor", maxRangeLux = 20_000f, minDelayMicros = 1_000),
        ),
        latestLux = 100f,
        setupStatus = SetupStatus(requirements = emptyList(), isReadyToStart = true, blockers = emptyList()),
        canStart = true,
        offsetPercent = offsetPercent,
        offsetApplication = offsetApplication,
        responseSpeedPercent = responseSpeedPercent,
    )

    private companion object {
        const val SHARING_STOP_TIMEOUT_MILLIS = 5_000L
    }
}
