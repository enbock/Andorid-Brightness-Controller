package com.ambientbrightness.app.StatusDashboard.View.Task

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample

/**
 * The sampling seam the dashboard collector depends on, so the collector can be tested without
 * waiting on real time while production always uses [LuxReadoutSampler].
 */
fun interface LuxSampler {

    /** Returns a display-rate version of [source]. */
    fun sample(source: Flow<Float?>): Flow<Float?>
}

/**
 * Caps the ambient-light readout to a display-friendly rate (US1, AC1.1).
 *
 * F1's stream is high-frequency, so the raw value must never reach Compose: `conflate` keeps only the
 * newest reading while the consumer is busy, `distinctUntilChanged` drops repeats (an unchanged value
 * must not recompose), and `sample` emits at most once per [DISPLAY_SAMPLE_INTERVAL_MS]. The result is
 * at most two repaints per second regardless of the sensor rate.
 */
class LuxReadoutSampler(
    private val intervalMillis: Long = DISPLAY_SAMPLE_INTERVAL_MS,
) : LuxSampler {

    @OptIn(FlowPreview::class)
    override fun sample(source: Flow<Float?>): Flow<Float?> =
        source.conflate().distinctUntilChanged().sample(intervalMillis)

    companion object {

        /** The display interval; one emission per interval, i.e. at most 2 Hz. */
        const val DISPLAY_SAMPLE_INTERVAL_MS: Long = 500L
    }
}
