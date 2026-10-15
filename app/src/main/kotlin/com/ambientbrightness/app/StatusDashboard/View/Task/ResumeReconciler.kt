package com.ambientbrightness.app.StatusDashboard.View.Task

import com.ambientbrightness.core.AppSetupService.AppSetupService

/**
 * Reconciles the dashboard with reality when the app comes back to the foreground (US2 AC2.4, US3 AC3.3).
 *
 * F5's lifecycle state and the F3/F4 settings are `StateFlow`s the dashboard observes directly, so their
 * current value is already re-read by construction — nothing is cached and no polling is needed. F6's
 * requirements are the one part that is *derived* rather than observed with a platform callback, so a
 * resume has to ask for a re-check: the user may have granted or revoked something in a system screen
 * while the app was away.
 *
 * It is a named unit rather than a bare lambda so that "a resume re-derives the setup state, and every
 * resume re-derives it again" is unit-testable.
 */
class ResumeReconciler(
    private val setupService: AppSetupService,
) {

    /** Re-derives the setup state; called from the dashboard's ON_RESUME hook. */
    fun onResume() {
        setupService.refresh()
    }
}
