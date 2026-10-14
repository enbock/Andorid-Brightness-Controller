package com.ambientbrightness.core.AppSetupService

/**
 * The aggregated setup state the checklist renders (US3).
 *
 * @property requirements every requirement with its own [RequirementState], so the UI lists each
 * missing one separately instead of a single "not set up yet" flag (US3, AC3.1).
 * @property isReadyToStart true only when every [RequirementSeverity.Blocking] requirement is
 * [RequirementState.Satisfied]; the Start action is enabled from this (US3, AC3.3).
 * @property blockers the ids of the [RequirementSeverity.Blocking] requirements that are not
 * [RequirementState.Satisfied], so a blocked Start can name the exact reason (US3, AC3.2).
 */
data class SetupStatus(
    val requirements: List<SetupRequirement>,
    val isReadyToStart: Boolean,
    val blockers: List<SetupRequirementId>,
)
