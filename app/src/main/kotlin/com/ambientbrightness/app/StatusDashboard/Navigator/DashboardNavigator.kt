package com.ambientbrightness.app.StatusDashboard.Navigator

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ambientbrightness.app.StatusDashboard.View.DashboardRoute
import com.ambientbrightness.app.StatusDashboard.View.DashboardResumeEffect
import com.ambientbrightness.app.StatusDashboard.View.DashboardViewModel
import com.ambientbrightness.app.StatusDashboard.View.SettingsRoute
import com.ambientbrightness.app.StatusDashboard.View.SettingsViewModel
import com.ambientbrightness.core.AppSetupService.SetupAction
import kotlinx.serialization.Serializable

/**
 * The two screens, as type-safe navigation routes.
 *
 * Objects rather than route strings, so a typo cannot silently create a dead destination and the
 * arguments (none yet) would be typed. Requires the Kotlin serialization plugin.
 */
@Serializable
internal data object DashboardDestination

/** The settings screen route (US4). */
@Serializable
internal data object SettingsDestination

/**
 * Hosts the dashboard and the settings screen in one navigation graph (US4, AC4.1).
 *
 * The dashboard is the start destination; its settings entry point navigates to [SettingsDestination],
 * and back — the back affordance or the system gesture — pops the settings screen and returns to the
 * dashboard with its state intact (the dashboard ViewModel is retained, not recreated).
 *
 * Back is handled only by the `NavHost`'s own handling, so predictive back keeps working on API 36;
 * nothing overrides `onBackPressed()`.
 */
@Composable
internal fun DashboardNavHost(
    dashboardViewModel: DashboardViewModel,
    settingsViewModel: SettingsViewModel,
    onSetupAction: (SetupAction) -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    // Re-derives the live state when the app comes back, so a system stop or a grant changed in a
    // system screen is reflected instead of the state from before (US2 AC2.4, US3 AC3.3).
    DashboardResumeEffect(onResume = onResume)

    NavHost(
        navController = navController,
        startDestination = DashboardDestination,
        modifier = modifier,
    ) {
        composable<DashboardDestination> {
            DashboardRoute(
                viewModel = dashboardViewModel,
                onSetupAction = onSetupAction,
                onOpenSettings = { navController.navigate(SettingsDestination) },
            )
        }

        composable<SettingsDestination> {
            SettingsRoute(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
