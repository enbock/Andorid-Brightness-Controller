package com.ambientbrightness.infrastructure.AutomaticBrightnessControl.DeviceBrightnessRangeClient.Dummy

import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessRange
import com.ambientbrightness.core.AutomaticBrightnessControl.DeviceBrightnessRangeClient

/** Deterministic in-memory device-range client for tests and development. */
class DummyDeviceBrightnessRangeClient(
    initiallyRange: BrightnessRange? = null,
) : DeviceBrightnessRangeClient {

    private var range: BrightnessRange? = initiallyRange

    override fun readRange(): BrightnessRange? = range

    fun setRange(value: BrightnessRange?) {
        range = value
    }
}
