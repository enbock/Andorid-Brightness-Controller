package com.ambientbrightness.core.BackgroundService

/**
 * Thin host abstraction over the platform foreground service.
 *
 * Keeping the notification lifecycle behind this interface keeps `:core` Android-free and lets the
 * lifecycle tests (T3) assert the exact start/update/stop calls with a fake host (US1, US4).
 */
interface ForegroundServiceHost {

    /** Promotes the service to the foreground and shows [content] as its persistent notification (US1, US4). */
    fun startForeground(content: BackgroundNotificationContent)

    /** Refreshes the visible notification with [content] without restarting the service (US4). */
    fun updateNotification(content: BackgroundNotificationContent)

    /** Removes the notification and takes the service out of the foreground state (US3). */
    fun stopForeground()
}
