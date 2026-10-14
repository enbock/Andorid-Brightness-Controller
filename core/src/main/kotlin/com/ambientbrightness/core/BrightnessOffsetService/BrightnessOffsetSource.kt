package com.ambientbrightness.core.BrightnessOffsetService

import com.ambientbrightness.core.BrightnessOffset.BrightnessOffset
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffsetApplication
import kotlinx.coroutines.flow.StateFlow

/**
 * The read-only side of the manual brightness offset.
 *
 * The running brightness controller (F2) and the settings/dashboard UI (F7) depend on this
 * interface only: they observe the current [offset] and how far it could be applied
 * ([application]) and never change it.
 */
interface BrightnessOffsetSource {

    /** The effective offset, always within [com.ambientbrightness.core.BrightnessOffset.BrightnessOffsetRange]. */
    val offset: StateFlow<BrightnessOffset>

    /**
     * How much of the last evaluated sample the offset could be applied to
     * (AC2.1–AC2.3); [BrightnessOffsetApplication.NotEvaluated] until the first sample.
     */
    val application: StateFlow<BrightnessOffsetApplication>
}
