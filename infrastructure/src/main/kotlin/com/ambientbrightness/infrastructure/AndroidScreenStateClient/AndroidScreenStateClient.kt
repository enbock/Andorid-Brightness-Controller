package com.ambientbrightness.infrastructure

import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.ambientbrightness.core.ScreenStateClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

/**
 * [ScreenStateClient] backed by [PowerManager.isInteractive] and display add/remove/change events.
 *
 * The stream is event-driven — it emits the current value immediately and again whenever a display
 * changes, and never polls. The display listener is only registered while there is a subscriber
 * and is unregistered in [awaitClose].
 */
class AndroidScreenStateClient(
    private val powerManager: PowerManager,
    private val displayManager: DisplayManager,
    scope: CoroutineScope,
    private val displayHandler: Handler = Handler(Looper.getMainLooper()),
) : ScreenStateClient {

    override val isScreenOn: StateFlow<Boolean> = callbackFlow {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit

            override fun onDisplayRemoved(displayId: Int) = Unit

            override fun onDisplayChanged(displayId: Int) {
                trySend(powerManager.isInteractive)
            }
        }

        displayManager.registerDisplayListener(listener, displayHandler)
        trySend(powerManager.isInteractive)

        awaitClose { displayManager.unregisterDisplayListener(listener) }
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = powerManager.isInteractive,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
