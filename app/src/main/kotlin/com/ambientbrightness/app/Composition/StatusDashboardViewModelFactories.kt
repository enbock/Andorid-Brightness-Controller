package com.ambientbrightness.app.Composition

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ambientbrightness.app.StatusDashboard.Adapter.DashboardStateCollector
import com.ambientbrightness.app.StatusDashboard.Controller.ControllerCommandHandler
import com.ambientbrightness.app.StatusDashboard.View.DashboardViewModel
import com.ambientbrightness.app.StatusDashboard.View.SettingsViewModel

/**
 * Builds the dashboard ViewModel from the app-scoped object graph.
 *
 * The factory lives in `Composition`, so it is the only place that may see the concrete adapters: the
 * screens receive a ViewModel and never a service, and the collector is created here from the
 * component's boundaries so there is exactly one dashboard pipeline (US1–US3).
 */
fun dashboardViewModelFactory(
    component: AmbientBrightnessComponent,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val provider = DashboardStateCollector(
            ambientLightService = component.ambientLightService,
            backgroundService = component.backgroundService,
            setupService = component.setupService,
            brightnessOffsetSource = component.brightnessOffsetService,
            responseTuningService = component.responseTuningService,
        )
        DashboardViewModel(
            provider = provider,
            commandHandler = ControllerCommandHandler(component.backgroundService),
        )
    }
}

/**
 * Builds the settings ViewModel from the same graph, so both screens read and write the one
 * app-scoped offset and response-speed state (US4).
 */
fun settingsViewModelFactory(
    component: AmbientBrightnessComponent,
): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        SettingsViewModel(
            offsetService = component.brightnessOffsetService,
            responseTuningService = component.responseTuningService,
        )
    }
}
