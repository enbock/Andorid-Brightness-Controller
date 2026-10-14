package com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessControllerUseCase

import com.ambientbrightness.core.AmbientLightSensing.AmbientLightService.AmbientLightService
import com.ambientbrightness.core.AmbientLightSensing.LuxSample
import com.ambientbrightness.core.AmbientLightSensing.ScreenStateClient
import com.ambientbrightness.core.AutomaticBrightnessControl.AutomaticBrightnessController
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessControllerUseCase.Task.AdaptiveBrightnessConflictDetector
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessControllerUseCase.Task.BrightnessComputation
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessControllerUseCase.Task.BrightnessPipeline
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessMappingCurve.BrightnessMappingCurve
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessMappingCurve.DefaultBrightnessMappingCurve
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessRange
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessRangeResolver.BrightnessRangeResolver
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessSnapshot
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessStatus
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessTransform
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessWriteResult
import com.ambientbrightness.core.AutomaticBrightnessControl.BuiltInAdaptiveBrightnessClient
import com.ambientbrightness.core.AutomaticBrightnessControl.BuiltInAdaptiveBrightnessState
import com.ambientbrightness.core.AutomaticBrightnessControl.DeviceBrightnessRangeClient
import com.ambientbrightness.core.AutomaticBrightnessControl.HysteresisGate.DefaultHysteresisGate
import com.ambientbrightness.core.AutomaticBrightnessControl.HysteresisGate.HysteresisGate
import com.ambientbrightness.core.AutomaticBrightnessControl.MinimumStepDeadBand.MinimumStepDeadBand
import com.ambientbrightness.core.AutomaticBrightnessControl.SystemBrightnessClient
import com.ambientbrightness.core.AutomaticBrightnessControl.WriteSettingsPermissionChecker
import com.ambientbrightness.core.BrightnessOffsetService.BrightnessOffsetSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Default [AutomaticBrightnessController] (F2): turns the F1 sample stream into system brightness
 * writes and publishes the resulting status and built-in adaptive-brightness conflict state.
 *
 * The loop is only active between [start] and [stop]; it follows the screen state so it does not
 * run while the display is off, and it stops writing after a failed write until the controller is
 * restarted or the permission is granted again (AC2.4). Runtime dependencies are supplied by the
 * composition root; this class owns no Android API.
 */
class BrightnessControllerUseCase(
    private val ambientLightService: AmbientLightService,
    private val screenStateClient: ScreenStateClient,
    private val brightnessClient: SystemBrightnessClient,
    private val deviceRangeClient: DeviceBrightnessRangeClient,
    private val permissionChecker: WriteSettingsPermissionChecker,
    builtInAdaptiveBrightnessClient: BuiltInAdaptiveBrightnessClient,
    private val scope: CoroutineScope,
    curve: BrightnessMappingCurve = DefaultBrightnessMappingCurve(),
    hysteresisGate: HysteresisGate = DefaultHysteresisGate(),
    private val rangeResolver: BrightnessRangeResolver = BrightnessRangeResolver(),
    private val deadBand: MinimumStepDeadBand = MinimumStepDeadBand(),
) : AutomaticBrightnessController {

    private val pipeline = BrightnessPipeline(curve = curve, hysteresisGate = hysteresisGate)
    private val conflictDetector = AdaptiveBrightnessConflictDetector(builtInAdaptiveBrightnessClient)

    private val mutableStatus = MutableStateFlow(BrightnessStatus())
    override val status: StateFlow<BrightnessStatus> = mutableStatus.asStateFlow()

    private val mutableConflict = MutableStateFlow(BuiltInAdaptiveBrightnessState.UNKNOWN)
    override val adaptiveBrightnessConflict: StateFlow<BuiltInAdaptiveBrightnessState> =
        mutableConflict.asStateFlow()

    override var snapshot: BrightnessSnapshot? = null
        private set

    private val lifecycleLock = Any()

    @Volatile
    private var collectionJob: Job? = null

    /** Observation of the manual offset (F3); owned by this controller for its running lifetime. */
    @Volatile
    private var offsetCollectionJob: Job? = null

    /** Source of manual-offset changes; set by the composition root, observed between start/stop. */
    @Volatile
    private var offsetSource: BrightnessOffsetSource? = null

    @Volatile
    private var writesSuspended = false

    @Volatile
    private var lastComputation: BrightnessComputation? = null

    private var range: BrightnessRange = BrightnessRangeResolver.DEFAULT_RANGE

    /**
     * Idempotent. Captures the brightness in effect as the restoration snapshot (D3), then starts
     * consuming the ambient-light stream until [stop].
     */
    override fun start() {
        synchronized(lifecycleLock) {
            if (collectionJob != null) return

            val currentBrightness = currentSystemBrightness()
            snapshot = currentBrightness?.let(::BrightnessSnapshot)
            range = rangeResolver.resolve(deviceRangeClient.readRange())
            deadBand.reset()
            currentBrightness?.let(deadBand::recordApplied)
            writesSuspended = false
            lastComputation = null
            mutableStatus.value = BrightnessStatus(
                isRunning = true,
                appliedBrightness = currentBrightness,
            )
            mutableConflict.value = conflictDetector.detect()
            collectionJob = scope.launch { controlLoop() }
            offsetCollectionJob = offsetSource?.let { source ->
                scope.launch { source.offset.drop(1).collect { reapply() } }
            }
        }
    }

    /** Idempotent. Stops writing immediately and releases the ambient-light stream. */
    override fun stop() {
        val runningJobs = synchronized(lifecycleLock) {
            val job = collectionJob ?: return
            val offsetJob = offsetCollectionJob
            collectionJob = null
            offsetCollectionJob = null
            job to offsetJob
        }

        runningJobs.first.cancel()
        runningJobs.second?.cancel()
        ambientLightService.stop()
        mutableStatus.update { it.copy(isRunning = false) }
    }

    /**
     * Adds a post-mapping transform hook (F3 offset, F4 ramp). Hooks registered after [start] only
     * affect later reads until [reapply] recomputes the current one.
     */
    fun registerTransform(transform: BrightnessTransform) {
        pipeline.register(transform)
    }

    /**
     * Registers the manual-offset source (F3) to re-apply against. A change to
     * [BrightnessOffsetSource.offset] calls [reapply], so it is applied within 1 s using the last
     * accepted reading, without waiting for a new ambient sample (AC1.1); before any reading has
     * been accepted [reapply] writes nothing and the stored offset applies on the first sample
     * (AC1.2). The source is pushed in by the composition root, which owns it; this controller owns
     * only the observation, launched in [start] and cancelled in [stop].
     *
     * Register the offset's [BrightnessTransform] before F4's ramp so the ramp governs how fast the
     * offset is applied (F3 leaves the speed to F4). Register this source before [start] to have it
     * observed from the first run.
     */
    fun registerOffsetSource(source: BrightnessOffsetSource) {
        offsetSource = source
    }

    /**
     * Recomputes and applies the last accepted reading, so configuration changes made while running
     * take effect immediately. Does nothing while stopped or before the first accepted reading.
     */
    fun reapply() {
        val computation = lastComputation ?: return
        if (collectionJob == null) return
        write(pipeline.recompute(computation.lux, range, deadBand.lastApplied))
    }

    private suspend fun controlLoop() {
        coroutineScope {
            launch {
                permissionChecker.hasWriteSettingsPermission.collect { granted ->
                    if (granted) writesSuspended = false
                }
            }
            screenStateClient.isScreenOn.collectLatest { isScreenOn ->
                if (isScreenOn) ambientLightService.start().collect(::onSample)
            }
        }
    }

    private fun onSample(sample: LuxSample) {
        mutableStatus.update { it.copy(lastLux = sample.lux) }

        val computation = pipeline.compute(
            lux = sample.lux,
            range = range,
            currentBrightness = deadBand.lastApplied,
        ) ?: return

        lastComputation = computation
        write(computation.brightness)
    }

    private fun write(brightness: BrightnessValue) {
        if (writesSuspended) return
        if (!permissionChecker.hasWriteSettingsPermission.value) return

        val suppressed = deadBand.suppressionFor(brightness)
        if (suppressed != null) {
            mutableStatus.update { it.copy(writeState = suppressed) }
            return
        }

        when (val result = brightnessClient.write(brightness)) {
            is BrightnessWriteResult.Applied -> {
                deadBand.recordApplied(result.value)
                mutableStatus.update {
                    it.copy(
                        appliedBrightness = result.value,
                        writeState = result,
                        writeError = null,
                    )
                }
            }

            is BrightnessWriteResult.Failed -> {
                writesSuspended = true
                mutableStatus.update { it.copy(writeState = result, writeError = result.reason) }
            }

            is BrightnessWriteResult.Suppressed -> {
                mutableStatus.update { it.copy(writeState = result) }
            }
        }
    }

    /** Reads the system value, discarding values that cannot be a valid brightness (AC2.1). */
    private fun currentSystemBrightness(): BrightnessValue? =
        brightnessClient.readCurrent()
            ?.takeIf { it >= BrightnessValue.MINIMUM }
            ?.let(::BrightnessValue)
}
