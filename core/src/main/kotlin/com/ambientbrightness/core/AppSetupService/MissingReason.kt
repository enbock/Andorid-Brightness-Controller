package com.ambientbrightness.core.AppSetupService

/**
 * Why a requirement is [RequirementState.Missing].
 */
enum class MissingReason {

    /** Never granted, so the setup step can ask for it for the first time (US2). */
    NotGranted,

    /**
     * Granted before but taken away again; the next write fails and the user must re-grant it
     * (US4, AC4.2).
     */
    Revoked,
}
