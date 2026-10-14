package com.ambientbrightness.infrastructure.BackgroundService.BootCompletedReceiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.ambientbrightness.core.BackgroundService.ControllerStateStore
import com.ambientbrightness.infrastructure.BackgroundService.ControllerStateStore.Android.AndroidControllerStateStore
import com.ambientbrightness.infrastructure.BackgroundService.ForegroundServiceHost.ForegroundBrightnessService

/**
 * Starts the controller again after a reboot only when it was running before (AC2.3, AC2.4).
 *
 * Registered for [Intent.ACTION_BOOT_COMPLETED] only, never `ACTION_LOCKED_BOOT_COMPLETED`, because
 * the store lives on credential-protected storage that is unavailable before the first unlock. The
 * receiver returns immediately: it reads the synchronous [ControllerStateStore.desiredRunning] and,
 * only when it is true, asks the platform to start [ForegroundBrightnessService]. When it is false it
 * does nothing at all, so a controller the user stopped stays stopped and no brightness is written.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    internal var storeFactory: (Context) -> ControllerStateStore = { AndroidControllerStateStore(it) }

    internal var serviceStarter: (Context) -> Unit = { context ->
        ContextCompat.startForegroundService(
            context,
            Intent(context, ForegroundBrightnessService::class.java),
        )
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        handleBootCompleted(intent?.action, { storeFactory(context) }, { serviceStarter(context) })
    }

    internal fun handleBootCompleted(
        action: String?,
        store: () -> ControllerStateStore,
        startService: () -> Unit,
    ) {
        if (action != ACTION_BOOT_COMPLETED) return
        if (store().desiredRunning) startService()
    }

    companion object {
        const val ACTION_BOOT_COMPLETED = Intent.ACTION_BOOT_COMPLETED
    }
}
