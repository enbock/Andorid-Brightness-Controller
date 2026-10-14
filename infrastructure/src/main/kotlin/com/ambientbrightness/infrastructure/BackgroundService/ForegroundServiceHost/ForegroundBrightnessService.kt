package com.ambientbrightness.infrastructure.BackgroundService.ForegroundServiceHost

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.ambientbrightness.core.BackgroundNotificationBuilder.BackgroundNotificationBuilder
import com.ambientbrightness.core.BackgroundService.BackgroundNotificationContent
import com.ambientbrightness.core.BackgroundService.BackgroundServiceState
import com.ambientbrightness.core.BackgroundService.ForegroundServiceHost
import com.ambientbrightness.infrastructure.BackgroundService.AndroidBrightnessNotificationFactory.AndroidBrightnessNotificationFactory

class ForegroundBrightnessService : Service(), ForegroundServiceHost {

    private val binder = LocalBinder()
    private val notificationFactory by lazy { AndroidBrightnessNotificationFactory(applicationContext) }
    private val notificationBuilder = BackgroundNotificationBuilder()

    private var currentContent: BackgroundNotificationContent? = null
    private var runningInForeground = false

    var stopRequestListener: (() -> Unit)? = null

    val isInForeground: Boolean
        get() = runningInForeground

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            requestStop()
        } else {
            startForeground(
                currentContent ?: notificationBuilder.build(BackgroundServiceState.Starting),
            )
        }
        return START_STICKY
    }

    override fun startForeground(content: BackgroundNotificationContent) {
        currentContent = content
        ServiceCompat.startForeground(
            this,
            AndroidBrightnessNotificationFactory.NOTIFICATION_ID,
            notificationFactory.create(content),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        runningInForeground = true
    }

    override fun updateNotification(content: BackgroundNotificationContent) {
        currentContent = content
        if (runningInForeground) {
            postNotification(content)
        }
    }

    override fun stopForeground() {
        runningInForeground = false
        currentContent = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun postNotification(content: BackgroundNotificationContent) {
        val notificationManager = NotificationManagerCompat.from(this)
        if (!notificationManager.areNotificationsEnabled()) return
        try {
            notificationManager.notify(
                AndroidBrightnessNotificationFactory.NOTIFICATION_ID,
                notificationFactory.create(content),
            )
        } catch (_: SecurityException) {
        }
    }

    private fun requestStop() {
        val listener = stopRequestListener
        if (listener != null) {
            listener()
        } else {
            stopForeground()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            AndroidBrightnessNotificationFactory.CHANNEL_ID,
            AndroidBrightnessNotificationFactory.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    inner class LocalBinder : Binder() {
        fun service(): ForegroundBrightnessService = this@ForegroundBrightnessService
    }

    companion object {
        const val ACTION_STOP = "com.ambientbrightness.intent.action.STOP_BRIGHTNESS_CONTROLLER"
    }
}
