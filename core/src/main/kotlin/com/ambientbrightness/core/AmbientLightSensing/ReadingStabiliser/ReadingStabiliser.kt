package com.ambientbrightness.core.AmbientLightSensing.ReadingStabiliser

import com.ambientbrightness.core.AmbientLightSensing.AmbientLightUseCase.AmbientLightUseCase
import com.ambientbrightness.core.AmbientLightSensing.LuxSample

/**
 * Turns a raw reading into the value the brightness pipeline is allowed to use (User Story 3).
 *
 * Implementations clamp to the sensor range, keep `0 lux` valid, and reject single samples that
 * lie far outside the recent trend (AC3.1, AC3.3, AC3.4). This is the extension point F4 replaces;
 * `AmbientLightUseCase` receives it as a constructor parameter and F1 ships only the default
 * implementation.
 */
interface ReadingStabiliser {

    /**
     * Synchronous and non-suspending. Returns the accepted (possibly clamped) sample, or `null`
     * when the sample must be rejected and nothing should be emitted this round.
     *
     * Implementations are stateful across calls: they retain the last accepted value so a single
     * spurious reading cannot move the emitted value on its own (AC3.1).
     */
    fun stabilise(sample: LuxSample): LuxSample?
}
