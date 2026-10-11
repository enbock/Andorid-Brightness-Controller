package com.ambientbrightness.core

import kotlinx.coroutines.flow.StateFlow

/** Public F2 controller boundary; platform and orchestration implementations live outside this contract. */
interface AutomaticBrightnessController {
    fun start()
    fun stop()

    val status: StateFlow<BrightnessStatus>
    val adaptiveBrightnessConflict: StateFlow<BuiltInAdaptiveBrightnessState>
}
