package com.ambientbrightness.core

/**
 * Maps a lux value to a [LightLevel] (Decision D10, User Story 3).
 *
 * Stateless and total: every `Float` maps to a level. The levels are contiguous and disjoint over
 * the non-negative finite lux range, each interval being lower-inclusive and upper-exclusive:
 *
 * | Level      | Lux range     |
 * |------------|---------------|
 * | `DARK`     | `[0, 10)`     |
 * | `DIM`      | `[10, 100)`   |
 * | `INDOOR`   | `[100, 1000)` |
 * | `BRIGHT`   | `[1000, 10000)` |
 * | `FULL_SUN` | `[10000, ∞)`  |
 *
 * `0 lux` is a valid very-low-light reading and therefore `DARK` (AC3.3). Negative lux and
 * non-finite values (NaN, ±Infinity) are not meaningful readings and map to [LightLevel.UNKNOWN];
 * the pipeline clamps negatives to `0` before classifying, so real darkness is never `UNKNOWN`
 * (AC3.4).
 */
object LightLevelClassifier {

    /** Upper bound (exclusive) of [LightLevel.DARK]. */
    const val DARK_UPPER_BOUND_LUX: Float = 10f

    /** Upper bound (exclusive) of [LightLevel.DIM]. */
    const val DIM_UPPER_BOUND_LUX: Float = 100f

    /** Upper bound (exclusive) of [LightLevel.INDOOR]. */
    const val INDOOR_UPPER_BOUND_LUX: Float = 1_000f

    /** Upper bound (exclusive) of [LightLevel.BRIGHT]; `FULL_SUN` starts here. */
    const val BRIGHT_UPPER_BOUND_LUX: Float = 10_000f

    /** Classifies a raw or stabilised lux value. */
    fun classify(lux: Float): LightLevel = when {
        !lux.isFinite() -> LightLevel.UNKNOWN
        lux < 0f -> LightLevel.UNKNOWN
        lux < DARK_UPPER_BOUND_LUX -> LightLevel.DARK
        lux < DIM_UPPER_BOUND_LUX -> LightLevel.DIM
        lux < INDOOR_UPPER_BOUND_LUX -> LightLevel.INDOOR
        lux < BRIGHT_UPPER_BOUND_LUX -> LightLevel.BRIGHT
        else -> LightLevel.FULL_SUN
    }
}
