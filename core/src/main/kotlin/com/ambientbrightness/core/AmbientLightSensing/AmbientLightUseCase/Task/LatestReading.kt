package com.ambientbrightness.core.AmbientLightSensing.AmbientLightUseCase.Task

import com.ambientbrightness.core.AmbientLightSensing.LuxSample

/**
 * Holds only the newest raw reading reported by the sensor. A [Volatile] field is enough because
 * intermediate readings are irrelevant: the loop samples the latest value once per tick (AC1.2).
 */
internal class LatestReading {

    @Volatile
    private var reading: LuxSample? = null

    @Volatile
    private var version: Long = 0L

    private var consumedVersion: Long = 0L

    fun record(lux: Float, timestampMillis: Long) {
        reading = LuxSample(lux = lux, timestampMillis = timestampMillis)
        version += 1L
    }

    /** Returns the newest reading if it arrived after the previous call, otherwise `null`. */
    fun takeIfNew(): LuxSample? {
        val currentVersion = version
        if (currentVersion == consumedVersion) return null
        consumedVersion = currentVersion
        return reading
    }
}
