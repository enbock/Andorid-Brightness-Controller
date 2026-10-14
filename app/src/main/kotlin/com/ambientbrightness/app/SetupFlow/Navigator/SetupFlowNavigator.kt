package com.ambientbrightness.app.SetupFlow.Navigator

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.ambientbrightness.core.AppSetupService.SetupAction

/**
 * Decides which system screen a [SetupAction] must open, in which order, and opens it (US2).
 *
 * The decision is pure data ([SetupScreen]) and opening a screen is a single injected seam that
 * reports whether it succeeded, so the whole flow — including the `WRITE_SETTINGS` fallback chain —
 * is unit-testable without an Android runtime. [create] supplies the real Activity launcher, which is
 * the only place an `Intent` is built and the only place [ActivityNotFoundException] can occur, so
 * the platform-specific part of the setup flow stays confined to one lambda.
 *
 * `WRITE_SETTINGS` is a special permission with no request dialog, so the row opens the system
 * screen. The platform documents that a matching Activity may not exist for the per-app screen, so
 * the candidate list walks a fallback chain and never crashes if a device offers none of them. The
 * battery row uses the advisory settings screen, never the restricted
 * `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` dialog.
 *
 * [requestNotificationPermission] is a seam as well: only an Activity can request the runtime
 * permission, so F7 supplies `rememberLauncherForActivityResult(RequestPermission())`.
 */
class SetupFlowNavigator(
    private val packageName: String,
    private val requestNotificationPermission: () -> Unit = {},
    private val launchScreen: (SetupScreen) -> Boolean,
) {

    /** Performs the platform step for [action]; [SetupAction.None] does nothing. */
    fun perform(action: SetupAction) {
        when (action) {
            SetupAction.RequestNotificationPermission -> requestNotificationPermission()
            SetupAction.OpenWriteSettings -> openFirstAvailable(writeSettingsScreens())
            SetupAction.OpenBatteryOptimizationSettings -> launchScreen(
                SetupScreen(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )
            SetupAction.None -> Unit
        }
    }

    /**
     * The "Modify system settings" screens in fallback order: the per-app screen, the same action
     * without the package URI, then the app's details screen.
     */
    fun writeSettingsScreens(): List<SetupScreen> = listOf(
        SetupScreen(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:$packageName"),
        SetupScreen(Settings.ACTION_MANAGE_WRITE_SETTINGS),
        SetupScreen(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName"),
    )

    /** Opens the first screen the device can actually handle, ignoring the rest. */
    private fun openFirstAvailable(screens: List<SetupScreen>) {
        for (screen in screens) {
            if (launchScreen(screen)) return
        }
    }

    companion object {

        /** Builds the Activity-scoped production navigator that starts screens on [context]. */
        fun create(
            context: Context,
            requestNotificationPermission: () -> Unit = {},
        ): SetupFlowNavigator = SetupFlowNavigator(
            packageName = context.packageName,
            requestNotificationPermission = requestNotificationPermission,
            launchScreen = { screen ->
                try {
                    val intent = Intent(screen.action)
                    screen.dataUri?.let { intent.data = Uri.parse(it) }
                    context.startActivity(intent)
                    true
                } catch (_: ActivityNotFoundException) {
                    false
                }
            },
        )
    }
}
