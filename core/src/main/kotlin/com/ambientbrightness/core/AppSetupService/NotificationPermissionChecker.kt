package com.ambientbrightness.core.AppSetupService

import kotlinx.coroutines.flow.Flow

/**
 * Boundary for the `POST_NOTIFICATIONS` runtime permission.
 *
 * Kept in `:core` so [AppSetupService] can treat the notification requirement like any other, while
 * the Android implementation lives in `:infrastructure`. The permission is advisory: denying it
 * hides the ongoing notification but never blocks Start (US4, AC4.3).
 */
interface NotificationPermissionChecker {

    /** Emits whether the notification permission is granted, and re-emits when the user changes it. */
    val isGranted: Flow<Boolean>
}
