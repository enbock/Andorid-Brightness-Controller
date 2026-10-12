package com.ambientbrightness.core.ResponseTuning

/**
 * The timing one [ResponseSpeed] stands for.
 *
 * [reactionWindowMillis] is how long a full-range brightness change may take, [debounceWindowMillis]
 * is how long a light change must be sustained before it is acted upon, and [maxStepFraction] caps a
 * single applied step as a fraction of the brightness span so no change is seen as a flash.
 */
data class ResponseProfile(
    val reactionWindowMillis: Long,
    val debounceWindowMillis: Long,
    val maxStepFraction: Float,
)
