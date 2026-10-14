package com.ambientbrightness.core.AppSetupService

/**
 * What is currently true for one requirement (US1–US4).
 *
 * Sealed on purpose: the checklist branches over it without an `else`, so every case the user can
 * run into must be modelled.
 */
sealed interface RequirementState {

    /** The requirement is met; a blocking requirement contributes to Start being enabled (US3, AC3.3). */
    data object Satisfied : RequirementState

    /**
     * The requirement is not met but the user can fix it; [reason] separates "not set up yet" from a
     * permission that was taken away again (US2, US4, AC4.2).
     */
    data class Missing(val reason: MissingReason) : RequirementState

    /**
     * The device or configuration cannot support the requirement, so no setup step can fix it and
     * the app must not keep troubleshooting (US4, AC4.1).
     */
    data class Unsupported(val reason: UnsupportedReason) : RequirementState

    /**
     * No source has reported a value yet; a blocking requirement stays not-ready so the app never
     * claims a false "ready" (US3).
     */
    data object Unknown : RequirementState
}
