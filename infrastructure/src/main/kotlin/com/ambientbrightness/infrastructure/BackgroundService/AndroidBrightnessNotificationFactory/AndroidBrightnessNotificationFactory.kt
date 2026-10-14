package com.ambientbrightness.infrastructure.BackgroundService.AndroidBrightnessNotificationFactory

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ambientbrightness.core.BackgroundService.BackgroundNotificationContent
import com.ambientbrightness.infrastructure.BackgroundService.ForegroundServiceHost.ForegroundBrightnessService

class AndroidBrightnessNotificationFactory(context: Context) {

    private val applicationContext = context.applicationContext

    fun create(content: BackgroundNotificationContent): Notification {
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(ICON_RESOURCE_ID)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setOngoing(content.ongoing)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent())
        if (content.showStopAction) {
            builder.addAction(0, STOP_ACTION_TITLE, stopActionIntent())
        }
        return builder.build()
    }

    private fun openAppIntent(): PendingIntent? {
        val launchIntent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?: return null
        return PendingIntent.getActivity(
            applicationContext,
            OPEN_APP_REQUEST_CODE,
            launchIntent,
            PENDING_INTENT_FLAGS,
        )
    }

    private fun stopActionIntent(): PendingIntent {
        val intent = Intent(applicationContext, ForegroundBrightnessService::class.java)
            .setAction(ForegroundBrightnessService.ACTION_STOP)
        return PendingIntent.getService(
            applicationContext,
            STOP_ACTION_REQUEST_CODE,
            intent,
            PENDING_INTENT_FLAGS,
        )
    }

    companion object {
        const val CHANNEL_ID = "ambient_brightness_controller"
        const val CHANNEL_NAME = "Ambient brightness control"
        const val NOTIFICATION_ID = 1
        const val STOP_ACTION_TITLE = "Stop"

        private const val ICON_RESOURCE_ID = android.R.drawable.ic_menu_view
        private const val OPEN_APP_REQUEST_CODE = 1
        private const val STOP_ACTION_REQUEST_CODE = 2
        private const val PENDING_INTENT_FLAGS =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
