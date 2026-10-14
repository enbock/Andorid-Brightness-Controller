package com.ambientbrightness.infrastructure.BackgroundService.ForegroundServiceHost

import com.ambientbrightness.core.BackgroundService.BackgroundNotificationContent
import com.ambientbrightness.core.BackgroundService.ForegroundServiceHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DummyForegroundServiceHost : ForegroundServiceHost {

    private val mutableContent = MutableStateFlow<BackgroundNotificationContent?>(null)

    val content: StateFlow<BackgroundNotificationContent?> = mutableContent.asStateFlow()

    var isInForeground: Boolean = false
        private set

    override fun startForeground(content: BackgroundNotificationContent) {
        mutableContent.value = content
        isInForeground = true
    }

    override fun updateNotification(content: BackgroundNotificationContent) {
        mutableContent.value = content
    }

    override fun stopForeground() {
        mutableContent.value = null
        isInForeground = false
    }
}
