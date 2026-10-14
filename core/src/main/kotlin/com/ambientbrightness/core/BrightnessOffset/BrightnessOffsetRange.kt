package com.ambientbrightness.core.BrightnessOffset

/**
 * The fixed bounds, granularity and neutral value of a [BrightnessOffset].
 *
 * The range is symmetric around [DEFAULT], so a positive and a negative offset of the same
 * magnitude are equally strong (AC3.1). These are the only place the numeric limits are defined;
 * [BrightnessOffset] and every consumer read them from here.
 */
object BrightnessOffsetRange {

    /** The most negative offset, in percent of the brightness span. */
    const val MIN: Int = -50

    /** The most positive offset, in percent of the brightness span. */
    const val MAX: Int = 50

    /** The granularity of the offset control, in percent. */
    const val STEP: Int = 1

    /** The neutral offset: the default and the identity (AC2.4). */
    const val DEFAULT: Int = 0
}
