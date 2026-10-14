package com.ambientbrightness.core.StopBrightnessRestorer

import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessSnapshot
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessWriteResult
import com.ambientbrightness.core.AutomaticBrightnessControl.SystemBrightnessClient

/**
 * Task that puts the pre-controller brightness back when the controller is stopped (AC3.2).
 *
 * It writes exclusively through F2's [SystemBrightnessClient] — there is no parallel mechanism — so
 * the restored value and the value the controller writes can never travel different paths. The value
 * comes from F2's [BrightnessSnapshot], which captures what was in effect before the controller
 * started; this class neither reads nor decides it.
 *
 * A `null` snapshot means nothing was captured, so nothing may be overwritten: this is what makes a
 * redundant stop safe (a second `stop()` must never clobber a brightness the user has set since).
 * A failed write is reported to the caller, never thrown, so a stop can never crash the service.
 */
class StopBrightnessRestorer(
    private val brightnessClient: SystemBrightnessClient,
) {

    /**
     * Writes [snapshot]'s brightness back to the system and reports the outcome.
     *
     * Returns `null` when [snapshot] is `null` (nothing captured, so no write is attempted).
     */
    fun restore(snapshot: BrightnessSnapshot?): BrightnessWriteResult? {
        val brightness = snapshot?.brightness ?: return null
        return brightnessClient.write(brightness)
    }
}
