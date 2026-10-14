package com.ambientbrightness.core.BrightnessOffsetService

import com.ambientbrightness.core.BrightnessOffset.BrightnessOffset

/**
 * The boundary through which the app reads and changes the manual brightness offset (F7).
 *
 * The service persists the offset and publishes it to every [offset] collector, so a change takes
 * effect without a restart (AC1.1, AC4.1, AC4.2). It extends [BrightnessOffsetSource]: consumers
 * that only read depend on the narrower type.
 */
interface BrightnessOffsetService : BrightnessOffsetSource {

    /** Persists [offset] and publishes it to every [offset] collector. */
    suspend fun setOffset(offset: BrightnessOffset)

    /**
     * Shifts the current offset by [deltaPercent], staying within
     * [com.ambientbrightness.core.BrightnessOffset.BrightnessOffsetRange]; the result is persisted
     * like [setOffset].
     */
    suspend fun adjustBy(deltaPercent: Int)

    /** Returns the offset to the neutral [BrightnessOffset.DEFAULT] (`0`) (AC3.3). */
    suspend fun reset()
}
