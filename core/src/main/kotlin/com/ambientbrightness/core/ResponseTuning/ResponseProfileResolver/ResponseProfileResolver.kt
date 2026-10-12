package com.ambientbrightness.core.ResponseTuning.ResponseProfileResolver

import com.ambientbrightness.core.ResponseTuning.ResponseProfile
import com.ambientbrightness.core.ResponseTuning.ResponseSpeed

/** Pure, side-effect-free mapping of a [ResponseSpeed] onto its [ResponseProfile]. */
class ResponseProfileResolver {

    fun resolve(speed: ResponseSpeed): ResponseProfile = ResponseProfile(
        reactionWindowMillis = linear(
            from = FASTEST_REACTION_WINDOW_MILLIS,
            to = SLOWEST_REACTION_WINDOW_MILLIS,
            percent = speed.percent,
        ),
        debounceWindowMillis = linear(
            from = FASTEST_DEBOUNCE_WINDOW_MILLIS,
            to = SLOWEST_DEBOUNCE_WINDOW_MILLIS,
            percent = speed.percent,
        ),
        maxStepFraction = MAX_STEP_FRACTION,
    )

    private fun linear(from: Long, to: Long, percent: Int): Long =
        from + (to - from) * percent / ResponseSpeed.SLOWEST_PERCENT

    companion object {
        const val FASTEST_REACTION_WINDOW_MILLIS: Long = 300L
        const val SLOWEST_REACTION_WINDOW_MILLIS: Long = 3_000L
        const val FASTEST_DEBOUNCE_WINDOW_MILLIS: Long = 150L
        const val SLOWEST_DEBOUNCE_WINDOW_MILLIS: Long = 600L
        const val MAX_STEP_FRACTION: Float = 0.15f
    }
}
