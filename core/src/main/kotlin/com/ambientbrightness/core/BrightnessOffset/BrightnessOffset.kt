package com.ambientbrightness.core.BrightnessOffset

/**
 * The manual correction the user adds on top of the computed brightness, expressed as a whole
 * percent of the brightness span.
 *
 * [percent] is symmetric over [BrightnessOffsetRange]: [BrightnessOffsetRange.MIN] to
 * [BrightnessOffsetRange.MAX] in [BrightnessOffsetRange.STEP] steps. Out-of-range and non-aligned
 * values are rejected on the way in, so an invalid offset can never reach the pipeline. The
 * neutral value is [DEFAULT] (`0`), which is the identity: applying it leaves the computed
 * brightness unchanged (AC2.4).
 */
@ConsistentCopyVisibility
data class BrightnessOffset private constructor(val percent: Int) {

    companion object {

        /** The neutral offset (`0`), used when nothing is stored and by a reset (AC2.4, AC3.3). */
        val DEFAULT: BrightnessOffset = BrightnessOffset(BrightnessOffsetRange.DEFAULT)

        /**
         * Returns the offset for [percent].
         *
         * @throws IllegalArgumentException if [percent] is outside
         * [BrightnessOffsetRange.MIN]..[BrightnessOffsetRange.MAX] or is not a whole
         * [BrightnessOffsetRange.STEP].
         */
        fun of(percent: Int): BrightnessOffset {
            require(percent in BrightnessOffsetRange.MIN..BrightnessOffsetRange.MAX) {
                "Brightness offset must be within ${BrightnessOffsetRange.MIN}.." +
                    "${BrightnessOffsetRange.MAX} percent but was $percent."
            }
            require((percent - BrightnessOffsetRange.DEFAULT) % BrightnessOffsetRange.STEP == 0) {
                "Brightness offset must be a whole ${BrightnessOffsetRange.STEP} percent but was " +
                    "$percent."
            }
            return BrightnessOffset(percent)
        }
    }
}
