package com.ambientbrightness.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.lifecycleScope
import com.ambientbrightness.app.Composition.AmbientBrightnessApplication
import com.ambientbrightness.app.Composition.dashboardViewModelFactory
import com.ambientbrightness.app.Composition.settingsViewModelFactory
import com.ambientbrightness.app.SetupFlow.Navigator.SetupFlowNavigator
import com.ambientbrightness.app.StatusDashboard.Navigator.DashboardNavHost
import com.ambientbrightness.app.StatusDashboard.View.DashboardViewModel
import com.ambientbrightness.app.StatusDashboard.View.SettingsViewModel
import kotlinx.coroutines.Job

/**
 * Entry point: hosts F7's navigation graph (dashboard ⇄ settings) and keeps F1's ambient-light
 * collection tied to this activity's started/stopped lifecycle.
 *
 * The ViewModels are resolved from the app-scoped object graph through the factories in `Composition`,
 * so the activity never touches an adapter itself. Back navigation is left entirely to the `NavHost`,
 * which keeps predictive back working on API 36 — nothing here overrides `onBackPressed()`.
 */
class MainActivity : ComponentActivity() {

    private val component
        get() = (application as AmbientBrightnessApplication).component

    private val dashboardViewModel: DashboardViewModel by viewModels {
        dashboardViewModelFactory(component)
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        settingsViewModelFactory(component)
    }

    private val setupFlowNavigator: SetupFlowNavigator by lazy { SetupFlowNavigator.create(this) }

    private var collectionJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                DashboardNavHost(
                    dashboardViewModel = dashboardViewModel,
                    settingsViewModel = settingsViewModel,
                    onSetupAction = setupFlowNavigator::perform,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        collectionJob = component.collector.collect(
            scope = lifecycleScope,
            onSample = {},
            onAvailability = {},
        )
    }

    override fun onStop() {
        stopAmbientLightCollection()
        super.onStop()
    }

    override fun onDestroy() {
        stopAmbientLightCollection()
        super.onDestroy()
    }

    private fun stopAmbientLightCollection() {
        collectionJob?.cancel()
        collectionJob = null
        component.stop()
    }
}
