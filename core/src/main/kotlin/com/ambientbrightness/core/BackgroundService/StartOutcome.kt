package com.ambientbrightness.core.BackgroundService

/**
 * Result of a [com.ambientbrightness.core.BrightnessBackgroundService.BrightnessBackgroundService.start]
 * request.
 *
 * An explicit outcome replaces a bare boolean so a refused start can explain itself to the caller
 * (F6/F7) instead of silently failing (US1, US2).
 */
sealed interface StartOutcome {

    /** The controller is (or already was) running for the requested reason. */
    data object Started : StartOutcome

    /**
     * The start was refused and nothing changed.
     *
     * @property reason a non-technical, caller-supplied explanation for the refusal.
     */
    data class Blocked(val reason: String) : StartOutcome
}
