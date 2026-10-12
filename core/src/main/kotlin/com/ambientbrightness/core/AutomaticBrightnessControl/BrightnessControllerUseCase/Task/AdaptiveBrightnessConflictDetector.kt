package com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessControllerUseCase.Task

import com.ambientbrightness.core.AutomaticBrightnessControl.BuiltInAdaptiveBrightnessClient
import com.ambientbrightness.core.AutomaticBrightnessControl.BuiltInAdaptiveBrightnessState

/**
 * Task collaborator that reports whether the device's own adaptive brightness competes with the
 * controller (US4). A platform that cannot report the mode is treated as
 * [BuiltInAdaptiveBrightnessState.UNKNOWN] instead of failing the controller.
 */
internal class AdaptiveBrightnessConflictDetector(
    private val client: BuiltInAdaptiveBrightnessClient,
) {

    fun detect(): BuiltInAdaptiveBrightnessState =
        try {
            client.readState()
        } catch (_: RuntimeException) {
            BuiltInAdaptiveBrightnessState.UNKNOWN
        }
}
