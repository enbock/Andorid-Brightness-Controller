package com.ambientbrightness.core.testdoubles

import com.ambientbrightness.core.AmbientLightSensing.AmbientLightService.AmbientLightService
import com.ambientbrightness.core.AmbientLightSensing.LuxSample
import com.ambientbrightness.core.AmbientLightSensing.ScreenStateClient
import com.ambientbrightness.core.AmbientLightSensing.SensorAvailability
import com.ambientbrightness.core.AmbientLightSensing.SensorProfile
import com.ambientbrightness.core.AmbientLightSensing.SensorUnavailableReason
import com.ambientbrightness.core.AutomaticBrightnessControl.AutomaticBrightnessController
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessContext
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessMappingCurve.BrightnessMappingCurve
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessRange
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessSnapshot
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessStatus
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessTransform
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessWriteResult
import com.ambientbrightness.core.AutomaticBrightnessControl.BuiltInAdaptiveBrightnessClient
import com.ambientbrightness.core.AutomaticBrightnessControl.BuiltInAdaptiveBrightnessState
import com.ambientbrightness.core.AutomaticBrightnessControl.DeviceBrightnessRangeClient
import com.ambientbrightness.core.AutomaticBrightnessControl.HysteresisGate.HysteresisGate
import com.ambientbrightness.core.AutomaticBrightnessControl.SystemBrightnessClient
import com.ambientbrightness.core.AutomaticBrightnessControl.WriteSettingsPermissionChecker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

internal class FakeAutomaticBrightnessController : AutomaticBrightnessController {
    private val mutableStatus = MutableStateFlow(BrightnessStatus())
    private val mutableConflict =
        MutableStateFlow(BuiltInAdaptiveBrightnessState.UNKNOWN)

    override val status: StateFlow<BrightnessStatus> = mutableStatus
    override val adaptiveBrightnessConflict: StateFlow<BuiltInAdaptiveBrightnessState> =
        mutableConflict

    override var snapshot: BrightnessSnapshot? = null

    override fun start() {
        mutableStatus.value = mutableStatus.value.copy(isRunning = true)
    }

    override fun stop() {
        mutableStatus.value = mutableStatus.value.copy(isRunning = false)
    }
}

internal class FakeBrightnessMappingCurve : BrightnessMappingCurve {
    override fun toFraction(lux: Float): Float = lux / 100f
}

internal class FakeSystemBrightnessClient(
    var current: Int? = null,
) : SystemBrightnessClient {
    var lastWrite: BrightnessValue? = null
    var writeAttempts: Int = 0
        private set

    private var failureReason: String? = null

    override fun readCurrent(): Int? = current

    override fun write(value: BrightnessValue): BrightnessWriteResult {
        writeAttempts++
        val reason = failureReason
        if (reason != null) return BrightnessWriteResult.Failed(reason)

        lastWrite = value
        current = value.value
        return BrightnessWriteResult.Applied(value)
    }

    /** Makes every subsequent write fail with [reason]; `null` restores successful writes. */
    fun setFailure(reason: String?) {
        failureReason = reason
    }
}

internal class FakeDeviceBrightnessRangeClient(
    var range: BrightnessRange?,
) : DeviceBrightnessRangeClient {
    override fun readRange(): BrightnessRange? = range
}

internal class FakeWriteSettingsPermissionChecker(
    granted: Boolean,
) : WriteSettingsPermissionChecker {
    private val mutablePermission = MutableStateFlow(granted)
    override val hasWriteSettingsPermission: StateFlow<Boolean> = mutablePermission

    fun setGranted(granted: Boolean) {
        mutablePermission.value = granted
    }
}

internal class FakeHysteresisGate : HysteresisGate {
    override fun accept(lux: Float): Float = lux
}

internal class FakeBrightnessTransform : BrightnessTransform {
    override fun apply(fraction: Float, context: BrightnessContext): Float = fraction
}

/** Recording transform hook that shifts the mapped fraction by [offset]. */
internal class RecordingBrightnessTransform(
    var offset: Float = 0f,
) : BrightnessTransform {
    val contexts: MutableList<BrightnessContext> = mutableListOf()

    override fun apply(fraction: Float, context: BrightnessContext): Float {
        contexts += context
        return fraction + offset
    }
}

internal class FakeBuiltInAdaptiveBrightnessClient(
    var state: BuiltInAdaptiveBrightnessState = BuiltInAdaptiveBrightnessState.UNKNOWN,
) : BuiltInAdaptiveBrightnessClient {
    override fun readState(): BuiltInAdaptiveBrightnessState = state
}

internal class FakeScreenStateClient(
    isScreenOn: Boolean = true,
) : ScreenStateClient {
    private val mutableScreenState = MutableStateFlow(isScreenOn)
    override val isScreenOn: StateFlow<Boolean> = mutableScreenState

    fun setScreenOn(value: Boolean) {
        mutableScreenState.value = value
    }
}

/**
 * Controllable [AmbientLightService]: [publish] pushes a reading to whoever is collecting
 * [start], and the collection counters make start/stop behavior observable. An unavailable sensor
 * behaves like the real service and publishes nothing.
 */
internal class FakeAmbientLightService(
    availability: SensorAvailability = SensorAvailability.Available(DEFAULT_PROFILE),
) : AmbientLightService {

    private val mutableAvailability = MutableStateFlow(availability)
    override val availability: StateFlow<SensorAvailability> = mutableAvailability

    private val samples = MutableSharedFlow<LuxSample>(extraBufferCapacity = 64)

    var collectionCount: Int = 0
        private set

    var stopCalls: Int = 0
        private set

    override fun start(): Flow<LuxSample> = flow {
        collectionCount++
        // Mirrors the real service: an unavailable sensor publishes no reading and completes.
        if (mutableAvailability.value is SensorAvailability.Available) {
            emitAll(samples)
        }
    }

    override fun stop() {
        stopCalls++
    }

    /** Delivers [lux] to the active collection; readings published while nobody collects are lost. */
    fun publish(lux: Float, timestampMillis: Long = 0L) {
        samples.tryEmit(LuxSample(lux = lux, timestampMillis = timestampMillis))
    }

    private companion object {
        val DEFAULT_PROFILE = SensorProfile("Fake light sensor", maxRangeLux = 10_000f, minDelayMicros = 0)
    }
}
