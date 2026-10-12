package com.ambientbrightness.infrastructure.AmbientLightSensing.ScreenStateClient.Dummy

import com.ambientbrightness.core.AmbientLightSensing.ScreenStateClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [ScreenStateClient] for tests and development; the state is driven manually by
 * [setScreenOn] instead of by the platform.
 */
class DummyScreenStateClient(
    initiallyScreenOn: Boolean = true,
) : ScreenStateClient {

    private val screenOn = MutableStateFlow(initiallyScreenOn)

    override val isScreenOn: StateFlow<Boolean> = screenOn.asStateFlow()

    fun setScreenOn(on: Boolean) {
        screenOn.value = on
    }
}
