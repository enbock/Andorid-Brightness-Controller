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

    /**
     * The persisted acknowledgement, seeded from [SetupStateStore.setupAcknowledged] and turned true
     * as soon as the setup is completed or skipped. The presentation uses it to decide between the
     * first-run view and the permanent "what is missing" view (US1, AC1.3).
     */
    val setupAcknowledged: StateFlow<Boolean>

    /**
     * Whether the ongoing-service notification may be invisible to the user: true while the
     * [SetupRequirementId.Notifications] requirement is missing and the controller has been started.
     * It is advisory and never gates Start (US4, AC4.3).
     */
    val notificationWarning: StateFlow<Boolean>

    /** Re-derives the status from the live sources; called when the app resumes (US2, AC2.4). */
    fun refresh()

    /**
     * Ends the setup because the user granted everything; persists the acknowledgement so the flow
     * is not repeated (US1, AC1.3).
     */
    fun completeSetup()

    /**
     * Ends the setup because the user chose to look around first; persists the same acknowledgement
     * as [completeSetup], so the checklist stays the permanent "what is missing" view (US1, AC1.3).
     */
    fun skipSetup()

    /** Persists that the user completed or skipped the setup, so it is not repeated (US1, AC1.3). */
    fun acknowledgeSetup()
}
