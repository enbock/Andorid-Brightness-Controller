package com.ambientbrightness.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

/** Default cadence of the throttled collection loop. */
const val DEFAULT_SAMPLING_INTERVAL_MILLIS: Long = 500L

/**
 * Throttled ambient light use case (User Story 1): turns the high-frequency sensor stream into at
 * most one stabilised reading per sampling interval, so the downstream brightness chain is not
 * flooded (AC1.2).
 *
 * Collecting [samples] registers the sensor, and cancelling the collection unregisters it exactly
 * once (AC1.3).
 */
class AmbientLightUseCase(
    private val sensorClient: AmbientLightSensorClient,
    private val stabiliser: ReadingStabiliser,
    private val clock: Clock,
    private val samplingIntervalMillis: Long = DEFAULT_SAMPLING_INTERVAL_MILLIS,
) {

    /**
     * Cold flow emitting one stabilised [LuxSample] per [samplingIntervalMillis] tick, but only when
     * a raw reading arrived since the previous tick. Values rejected by [stabiliser] are not emitted.
     */
    fun samples(): Flow<LuxSample> = channelFlow {
        val latestReading = LatestReading()

        sensorClient.register { lux -> latestReading.record(lux, clock.nowMillis()) }

        try {
            while (true) {
                delay(samplingIntervalMillis)
                val sample = latestReading.takeIfNew() ?: continue
                val stabilisedSample = stabiliser.stabilise(sample) ?: continue
                send(stabilisedSample)
            }
        } finally {
            sensorClient.unregister()
        }
    }
}

/**
 * Holds only the newest raw reading reported by the sensor. A [Volatile] field is enough because
 * intermediate readings are irrelevant: the loop samples the latest value once per tick (AC1.2).
 */
private class LatestReading {

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
