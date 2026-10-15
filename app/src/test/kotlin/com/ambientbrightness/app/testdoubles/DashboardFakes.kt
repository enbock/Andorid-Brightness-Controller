package com.ambientbrightness.app.testdoubles

import com.ambientbrightness.app.StatusDashboard.View.DashboardSnapshot
import com.ambientbrightness.app.StatusDashboard.View.DashboardStateProvider
import com.ambientbrightness.core.AmbientLightSensing.AmbientLightService.AmbientLightService
import com.ambientbrightness.core.AmbientLightSensing.LuxSample
import com.ambientbrightness.core.AmbientLightSensing.SensorAvailability
import com.ambientbrightness.core.AmbientLightSensing.SensorProfile
import com.ambientbrightness.core.AppSetupService.AppSetupService
import com.ambientbrightness.core.AppSetupService.SetupStatus
import com.ambientbrightness.core.BackgroundService.BackgroundServiceState
import com.ambientbrightness.core.BackgroundService.StartOutcome
import com.ambientbrightness.core.BackgroundService.StartReason
import com.ambientbrightness.core.BrightnessBackgroundService.BrightnessBackgroundService
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffset
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffsetApplication
import com.ambientbrightness.core.BrightnessOffsetService.BrightnessOffsetSource
import com.ambientbrightness.core.ResponseTuning.ResponseSpeed
import com.ambientbrightness.core.ResponseTuning.ResponseTuningService.ResponseTuningService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/** F1 double: an [AmbientLightService] a test can feed readings and that counts live listeners. */
internal class FakeAmbientLightService(
    availability: SensorAvailability = SensorAvailability.Available(
        SensorProfile(name = "fake sensor", maxRangeLux = 20_000f, minDelayMicros = 1_000),
    ),
) : AmbientLightService {

    private val mutableAvailability = MutableStateFlow(availability)

    override val availability: StateFlow<SensorAvailability> = mutableAvailability

    private val readings = MutableSharedFlow<LuxSample>(extraBufferCapacity = 64)

    /** Number of live [start] collections; F1 unregisters its listener when a collection stops. */
    var activeCollections: Int = 0
        private set

    override fun start(): Flow<LuxSample> = flow {
        activeCollections++
        try {
            readings.collect { emit(it) }
        } finally {
            activeCollections--
        }
    }

    override fun stop() = Unit

    /** Publishes one reading to the collectors, as the sensor callback would. */
    fun emitLux(lux: Float) {
        readings.tryEmit(LuxSample(lux = lux, timestampMillis = 0L))
    }

    fun setAvailability(value: SensorAvailability) {
        mutableAvailability.value = value
    }
}

/** F5 double: settable lifecycle state plus start/stop call counters. */
internal class FakeBrightnessBackgroundService(
    initial: BackgroundServiceState = BackgroundServiceState.Stopped,
) : BrightnessBackgroundService {

    private val mutableState = MutableStateFlow(initial)

    override val state: StateFlow<BackgroundServiceState> = mutableState

    override val batteryOptimizationExempt: Flow<Boolean> = flowOf(false)

    var startCalls: Int = 0
        private set

    var stopCalls: Int = 0
        private set

    var lastStartReason: StartReason? = null
        private set

    /** Mimics the real service: a start publishes the optimistic `Starting` state synchronously. */
    override fun start(reason: StartReason): StartOutcome {
        startCalls++
        lastStartReason = reason
        mutableState.value = BackgroundServiceState.Starting
        return StartOutcome.Started
    }

    override fun stop() {
        stopCalls++
        mutableState.value = BackgroundServiceState.Stopped
    }

    fun setState(value: BackgroundServiceState) {
        mutableState.value = value
    }
}

/** F6 double: settable gate/status, so the mapper's gating rules can be driven directly. */
internal class FakeAppSetupService(
    readyToStart: Boolean = true,
) : AppSetupService {

    private val mutableStatus = MutableStateFlow(
        SetupStatus(requirements = emptyList(), isReadyToStart = readyToStart, blockers = emptyList()),
    )
    private val mutableReadyToStart = MutableStateFlow(readyToStart)
    private val mutableAcknowledged = MutableStateFlow(false)
    private val mutableNotificationWarning = MutableStateFlow(false)

    override val status: StateFlow<SetupStatus> = mutableStatus
    override val isReadyToStart: StateFlow<Boolean> = mutableReadyToStart
    override val setupAcknowledged: StateFlow<Boolean> = mutableAcknowledged
    override val notificationWarning: StateFlow<Boolean> = mutableNotificationWarning

    var refreshCalls: Int = 0
        private set

    override fun refresh() {
        refreshCalls++
    }

    override fun completeSetup() {
        mutableAcknowledged.value = true
    }

    override fun skipSetup() {
        mutableAcknowledged.value = true
    }

    override fun acknowledgeSetup() {
        mutableAcknowledged.value = true
    }

    fun setStatus(value: SetupStatus) {
        mutableStatus.value = value
        mutableReadyToStart.value = value.isReadyToStart
    }
}

/** F3 double: settable offset and clamp state. */
internal class FakeBrightnessOffsetSource(
    offsetPercent: Int = 0,
    application: BrightnessOffsetApplication = BrightnessOffsetApplication.NotEvaluated,
) : BrightnessOffsetSource {

    private val mutableOffset = MutableStateFlow(BrightnessOffset.of(offsetPercent))
    private val mutableApplication = MutableStateFlow(application)

    override val offset: StateFlow<BrightnessOffset> = mutableOffset
    override val application: StateFlow<BrightnessOffsetApplication> = mutableApplication

    fun setOffsetPercent(percent: Int) {
        mutableOffset.value = BrightnessOffset.of(percent)
    }

    fun setApplication(value: BrightnessOffsetApplication) {
        mutableApplication.value = value
    }
}

/** F4 double: settable response speed. */
internal class FakeResponseTuningService(
    speedPercent: Int = ResponseSpeed.DEFAULT,
) : ResponseTuningService {

    private val mutableSpeed = MutableStateFlow(ResponseSpeed.of(speedPercent))

    override val responseSpeed: StateFlow<ResponseSpeed> = mutableSpeed

    var setCalls: Int = 0
        private set

    override suspend fun setResponseSpeed(speed: ResponseSpeed) {
        setCalls++
        mutableSpeed.value = speed
    }

    fun setSpeedPercent(percent: Int) {
        mutableSpeed.value = ResponseSpeed.of(percent)
    }
}

/** Dashboard-state double: a settable snapshot stream that counts its active subscriptions. */
internal class FakeDashboardStateProvider(
    initial: DashboardSnapshot,
) : DashboardStateProvider {

    private val mutableSnapshots = MutableStateFlow(initial)

    var activeSubscriptions: Int = 0
        private set

    override fun snapshots(): Flow<DashboardSnapshot> = flow {
        activeSubscriptions++
        try {
            mutableSnapshots.collect { emit(it) }
        } finally {
            activeSubscriptions--
        }
    }

    fun set(snapshot: DashboardSnapshot) {
        mutableSnapshots.value = snapshot
    }
}
