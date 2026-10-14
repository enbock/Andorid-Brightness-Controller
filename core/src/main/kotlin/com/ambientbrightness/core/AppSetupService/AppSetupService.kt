package com.ambientbrightness.core.AppSetupService

import kotlinx.coroutines.flow.StateFlow

/**
 * The single setup surface the checklist UI (F7) consumes: the aggregated [status] of every setup
 * requirement plus the [isReadyToStart] gate derived from it.
 *
 * The implementation composes the live permission and support sources instead of caching them, so
 * [refresh] re-derives the status on every resume and no restart is needed (US2, AC2.4). Only
 * [RequirementSeverity.Blocking] requirements gate Start (US3); advisory ones are reported but
 * never block (US4, AC4.3).
 */
interface AppSetupService {

    /** The aggregated state of all setup requirements, re-derived live (US1–US4). */
    val status: StateFlow<SetupStatus>

    /**
     * True only while every [RequirementSeverity.Blocking] requirement is
     * [RequirementState.Satisfied]; the Start action is driven from this (US3, AC3.1–AC3.3).
     */
    val isReadyToStart: StateFlow<Boolean>

    /** Re-derives the status from the live sources; called when the app resumes (US2, AC2.4). */
    fun refresh()

    /** Persists that the user completed or skipped the setup, so it is not repeated (US1, AC1.3). */
    fun acknowledgeSetup()
}
