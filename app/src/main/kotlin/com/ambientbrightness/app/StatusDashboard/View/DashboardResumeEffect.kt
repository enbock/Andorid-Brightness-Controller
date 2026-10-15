package com.ambientbrightness.app.StatusDashboard.View

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Re-reads the live state whenever the dashboard comes back to the foreground (US2 AC2.4, US3 AC3.3).
 *
 * Two things make the returned-to state honest:
 * - F5's lifecycle state and the F3/F4 settings are `StateFlow`s that the dashboard observes directly,
 *   so their current value is read again by construction — nothing is cached and no polling is started.
 * - F6's requirements are *derived* on demand, so [onResume] calls `AppSetupService.refresh()` to
 *   re-check the grants a user may have changed in a system screen while the app was away.
 */
@Composable
internal fun DashboardResumeEffect(onResume: () -> Unit) {
    LifecycleResumeEffect(Unit) {
        onResume()
        onPauseOrDispose { }
    }
}
