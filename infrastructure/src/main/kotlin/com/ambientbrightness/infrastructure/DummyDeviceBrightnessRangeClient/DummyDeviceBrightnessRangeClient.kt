package com.ambientbrightness.infrastructure

import com.ambientbrightness.core.BrightnessRange
import com.ambientbrightness.core.DeviceBrightnessRangeClient

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
