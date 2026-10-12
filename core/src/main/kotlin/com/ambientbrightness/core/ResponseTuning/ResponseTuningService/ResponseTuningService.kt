package com.ambientbrightness.core.ResponseTuning.ResponseTuningService

import com.ambientbrightness.core.ResponseTuning.ResponseSpeed
import kotlinx.coroutines.flow.StateFlow

/**
 * The boundary through which the app reads and changes the response speed.
 *
 * F7 (and the settings UI) depend on this interface only and never on `SettingsStore`: the current
 * value is always available, changes are persisted, and [setResponseSpeed] takes effect for the
 * running controller without a restart (AC1.4).
 */
interface ResponseTuningService {

    /** The persisted response speed, defaulting to the middle of the range while nothing is stored (AC4.2). */
    val responseSpeed: StateFlow<ResponseSpeed>

    /** Persists [speed] and publishes it to every [responseSpeed] collector. */
    suspend fun setResponseSpeed(speed: ResponseSpeed)
}
