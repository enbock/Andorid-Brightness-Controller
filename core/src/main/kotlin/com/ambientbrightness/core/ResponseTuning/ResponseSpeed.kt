package com.ambientbrightness.core.ResponseTuning

/**
 * How fast the screen follows a change in ambient light, expressed as the single user-facing
 * control.
 *
 * [percent] spans [FASTEST_PERCENT]..[SLOWEST_PERCENT], where the lowest value reacts fastest and
 * the highest reacts slowest. Any other input is clamped on the way in, so a corrupt stored value
 * can never leave that range. [DEFAULT] is the middle of the range and is used while no value has
 * been stored (AC4.2).
 */
@ConsistentCopyVisibility
data class ResponseSpeed private constructor(val percent: Int) {

    companion object {
        const val FASTEST_PERCENT: Int = 0
        const val SLOWEST_PERCENT: Int = 100
        const val DEFAULT: Int = 50

        fun of(percent: Int): ResponseSpeed =
            ResponseSpeed(percent.coerceIn(FASTEST_PERCENT, SLOWEST_PERCENT))
    }
}
