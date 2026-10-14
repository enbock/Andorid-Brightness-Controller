package com.ambientbrightness.core.AppSetupService

/**
 * Whether a requirement gates the Start action or is only recommended (US3, US4).
 */
enum class RequirementSeverity {

    /** Missing means the controller cannot work; the Start action stays disabled (US3, AC3.1–AC3.2). */
    Blocking,

    /** Recommended for the best experience, but never blocks Start (US4, AC4.3). */
    Advisory,
}
