package com.ambientbrightness.infrastructure.AutomaticBrightnessControl.BuiltInAdaptiveBrightnessClient.Dummy

import com.ambientbrightness.core.AutomaticBrightnessControl.BuiltInAdaptiveBrightnessClient
import com.ambientbrightness.core.AutomaticBrightnessControl.BuiltInAdaptiveBrightnessState

/** Deterministic in-memory adaptive-brightness mode client for tests and development. */
class DummyBuiltInAdaptiveBrightnessClient(
    initiallyState: BuiltInAdaptiveBrightnessState = BuiltInAdaptiveBrightnessState.UNKNOWN,
) : BuiltInAdaptiveBrightnessClient {

    private var state: BuiltInAdaptiveBrightnessState = initiallyState

    override fun readState(): BuiltInAdaptiveBrightnessState = state

    fun setState(value: BuiltInAdaptiveBrightnessState) {
        state = value
    }
}
