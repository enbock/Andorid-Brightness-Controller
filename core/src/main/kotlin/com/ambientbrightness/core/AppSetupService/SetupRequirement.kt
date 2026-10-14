package com.ambientbrightness.core.AppSetupService

/**
 * One setup requirement with everything a checklist row needs (US1–US4).
 *
 * @property id which requirement this row describes.
 * @property severity whether it gates Start ([RequirementSeverity.Blocking]) or is only recommended
 * ([RequirementSeverity.Advisory]).
 * @property state what is currently true for the requirement, including whether it is missing,
 * unsupported or still unknown.
 * @property action what activating the row must trigger on the platform; [SetupAction.None] when
 * there is nothing the user can do from here.
 */
data class SetupRequirement(
    val id: SetupRequirementId,
    val severity: RequirementSeverity,
    val state: RequirementState,
    val action: SetupAction,
)
