package com.ambientbrightness.infrastructure.AutomaticBrightnessControl.WriteSettingsPermissionChecker.Android

import android.app.AppOpsManager
import android.content.Context
import android.provider.Settings
import com.ambientbrightness.core.AutomaticBrightnessControl.WriteSettingsPermissionChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Observes WRITE_SETTINGS AppOps changes only while the permission state has subscribers.
 *
 * The injected app-scoped [scope] owns the shared flow; stopping collection unregisters the
 * platform watcher so it cannot outlive the controller's active observation.
 */
class AndroidWriteSettingsPermissionChecker(
    context: Context,
    scope: CoroutineScope,
    private val appOpsManager: AppOpsManager =
        context.applicationContext.getSystemService(AppOpsManager::class.java),
) : WriteSettingsPermissionChecker {

    private val applicationContext = context.applicationContext

    override val hasWriteSettingsPermission: StateFlow<Boolean> = callbackFlow {
        val listener = AppOpsManager.OnOpChangedListener { operation, packageName ->
            if (operation == AppOpsManager.OPSTR_WRITE_SETTINGS &&
                packageName == applicationContext.packageName
            ) {
                trySend(readPermission())
            }
        }

        appOpsManager.startWatchingMode(
            AppOpsManager.OPSTR_WRITE_SETTINGS,
            applicationContext.packageName,
            listener,
        )
        trySend(readPermission())
        awaitClose { appOpsManager.stopWatchingMode(listener) }
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
        initialValue = readPermission(),
    )

    private fun readPermission(): Boolean = Settings.System.canWrite(applicationContext)
}
