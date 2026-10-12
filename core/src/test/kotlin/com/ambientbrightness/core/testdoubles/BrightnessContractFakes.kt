package com.ambientbrightness.core.testdoubles

import com.ambientbrightness.core.AutomaticBrightnessControl.AutomaticBrightnessController
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessContext
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessMappingCurve.BrightnessMappingCurve
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessRange
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessStatus
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessTransform
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessWriteResult
import com.ambientbrightness.core.AutomaticBrightnessControl.BuiltInAdaptiveBrightnessState
import com.ambientbrightness.core.AutomaticBrightnessControl.DeviceBrightnessRangeClient
import com.ambientbrightness.core.AutomaticBrightnessControl.HysteresisGate.HysteresisGate
import com.ambientbrightness.core.AutomaticBrightnessControl.SystemBrightnessClient
import com.ambientbrightness.core.AutomaticBrightnessControl.WriteSettingsPermissionChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class FakeAutomaticBrightnessController : AutomaticBrightnessController {
    private val mutableStatus = MutableStateFlow(BrightnessStatus())
    private val mutableConflict =
        MutableStateFlow(BuiltInAdaptiveBrightnessState.UNKNOWN)

    override val status: StateFlow<BrightnessStatus> = mutableStatus
    override val adaptiveBrightnessConflict: StateFlow<BuiltInAdaptiveBrightnessState> =
        mutableConflict

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

    override fun readCurrent(): Int? = current

    override fun write(value: BrightnessValue): BrightnessWriteResult {
        lastWrite = value
        current = value.value
        return BrightnessWriteResult.Applied(value)
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
