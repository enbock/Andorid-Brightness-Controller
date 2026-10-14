package com.ambientbrightness.infrastructure.AppSetupService.NotificationPermissionChecker.Android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ambientbrightness.core.AppSetupService.NotificationPermissionChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * `POST_NOTIFICATIONS` grant state backed by [NotificationManagerCompat.areNotificationsEnabled] and
 * [ContextCompat.checkSelfPermission].
 *
 * Both platform reads are combined, because an app can have the runtime permission while the user
 * has switched its notifications off in the system settings, and only the combination reflects what
 * the user actually sees (US2, AC2.2; US4, AC4.3). Android offers no change callback for either
 * read, so the state is a [MutableStateFlow] that is re-read from the platform in [refresh]: on
 * construction and whenever the composition root asks after the user returns from the system
 * settings, exactly like F5's `AndroidBatteryOptimizationClient`.
 */
class AndroidNotificationPermissionChecker(
    private val notificationsEnabledReader: () -> Boolean,
    private val permissionGrantedReader: () -> Boolean,
) : NotificationPermissionChecker {

    private val granted = MutableStateFlow(readGranted())

    override val isGranted: StateFlow<Boolean> = granted.asStateFlow()

    fun refresh() {
        granted.value = readGranted()
    }

    private fun readGranted(): Boolean = notificationsEnabledReader() && permissionGrantedReader()

    companion object {
        fun create(context: Context): AndroidNotificationPermissionChecker {
            val applicationContext = context.applicationContext
            return AndroidNotificationPermissionChecker(
                notificationsEnabledReader = {
                    NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
                },
                permissionGrantedReader = {
                    ContextCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                },
            )
        }
    }
}
