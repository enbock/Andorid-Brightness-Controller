package com.ambientbrightness.app.Composition

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.ambientbrightness.core.BackgroundService.BackgroundNotificationContent
import com.ambientbrightness.core.BackgroundService.ForegroundServiceHost
import com.ambientbrightness.infrastructure.BackgroundService.ForegroundServiceHost.ForegroundBrightnessService

/**
 * App-scoped [ForegroundServiceHost] that reaches the platform-owned [ForegroundBrightnessService].
 *
 * The `:core` lifecycle ([com.ambientbrightness.core.BackgroundServiceUseCase.BackgroundServiceUseCase])
 * drives the notification through the [ForegroundServiceHost] interface and must never see Android,
 * while the service instance itself is created by the platform and cannot be constructed here. This
 * bridge closes that gap in three steps:
 *
 * 1. [startForeground] brings the service up with [ContextCompat.startForegroundService], so the
 *    platform's short foreground window is respected even before a binding exists.
 * 2. It binds to the running service with [Context.bindService] and receives the instance through
 *    [ForegroundBrightnessService.LocalBinder]; binding is asynchronous, so the most recent
 *    [BackgroundNotificationContent] is queued until [ServiceConnection.onServiceConnected] fires.
 * 3. `startForeground`/`updateNotification`/`stopForeground` are delegated to the bound instance, so
 *    the notification and foreground state are owned by the platform service, not duplicated here.
 *
 * The notification's Stop action re-enters the service as `ACTION_STOP`; the service forwards it to
 * [stopRequestHandler], which the composition root wires to the use case's `stop()`, so a stop from
 * the notification travels the same path as a stop from F7 (F2 stop → snapshot restore → foreground
 * stop).
 *
 * Limitation: the JVM unit tests must not touch a real `Service` or `Context`, so they compose the
 * lifecycle with [com.ambientbrightness.infrastructure.BackgroundService.ForegroundServiceHost.DummyForegroundServiceHost]
 * and this bridge stays compile-verified there; the actual delegation (the service entering the
 * foreground and posting the notification) can only be observed on a device or emulator and is
 * covered by the `:infrastructure` instrumented test.
 */
class ForegroundServiceHostBridge(
    context: Context,
) : ForegroundServiceHost {

    private val applicationContext = context.applicationContext

    private var service: ForegroundBrightnessService? = null
    private var bound = false
    private var pendingContent: BackgroundNotificationContent? = null

    /** Invoked on the notification Stop action; the composition root wires it to the use case's stop. */
    var stopRequestHandler: (() -> Unit)? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val connectedService = (binder as? ForegroundBrightnessService.LocalBinder)?.service()
                ?: return
            service = connectedService
            connectedService.stopRequestListener = { stopRequestHandler?.invoke() }
            val queued = pendingContent
            pendingContent = null
            queued?.let(connectedService::startForeground)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    override fun startForeground(content: BackgroundNotificationContent) {
        ContextCompat.startForegroundService(applicationContext, serviceIntent())
        bindIfNeeded()
        val current = service
        if (current != null) {
            current.startForeground(content)
        } else {
            pendingContent = content
        }
    }

    override fun updateNotification(content: BackgroundNotificationContent) {
        val current = service
        if (current != null) {
            current.updateNotification(content)
        } else {
            pendingContent = content
        }
    }

    override fun stopForeground() {
        val current = service
        if (current != null) {
            current.stopForeground()
        } else {
            applicationContext.stopService(serviceIntent())
        }
        pendingContent = null
        unbindIfNeeded()
    }

    private fun bindIfNeeded() {
        if (bound) return
        bound = applicationContext.bindService(serviceIntent(), connection, Context.BIND_AUTO_CREATE)
    }

    private fun unbindIfNeeded() {
        if (!bound) return
        applicationContext.unbindService(connection)
        bound = false
        service = null
    }

    private fun serviceIntent(): Intent =
        Intent(applicationContext, ForegroundBrightnessService::class.java)
}
