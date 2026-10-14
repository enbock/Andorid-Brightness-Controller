package com.ambientbrightness.core.AppSetupService

/**
 * What activating a requirement row must trigger on the platform.
 *
 * `:core` only names the step; the Activity-scoped `SetupFlowNavigator` in `:app` turns it into the
 * matching system screen or permission request (US2).
 */
sealed interface SetupAction {

    /** Shows the runtime dialog for `POST_NOTIFICATIONS` (US2, AC2.2). */
    data object RequestNotificationPermission : SetupAction

    /** Opens the "Modify system settings" screen for this app (US2, AC2.1). */
    data object OpenWriteSettings : SetupAction

    /** Opens the system screen that exempts the app from battery optimization (US2, AC2.3). */
    data object OpenBatteryOptimizationSettings : SetupAction

    /** Nothing the user can do from here, e.g. the requirement is satisfied or unsupported. */
    data object None : SetupAction
}
