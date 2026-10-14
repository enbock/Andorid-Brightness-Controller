package com.ambientbrightness.core.BrightnessOffsetService

import com.ambientbrightness.core.BrightnessOffset.BrightnessOffsetApplication

/**
 * The write-side port through which the offset transform publishes how far the offset was applied.
 *
 * `BrightnessTransform.apply(...)` only returns a `Float`, so the clamp indicator (AC2.3) travels
 * through this sink instead of widening that signature.
 */
interface OffsetApplicationSink {

    /** Publishes [application] as the latest offset-application outcome. */
    fun publish(application: BrightnessOffsetApplication)
}
