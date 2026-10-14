package com.ambientbrightness.core.BackgroundService

/**
 * Persists the two facts the lifecycle needs to survive process death and reboot (US2).
 *
 * `:core` owns the contract only; the persistent implementation is an Android DataStore adapter in
 * `:infrastructure`. The boot receiver reads [desiredRunning] to decide whether to start the
 * controller again after [StartReason.Boot] (AC2.3, AC2.4).
 */
interface ControllerStateStore {

    /** True while the user wants the controller running; survives process death and reboot. */
    var desiredRunning: Boolean

    /** The reason of the last start attempt, or null when none was recorded yet. */
    var lastStartReason: StartReason?
}
