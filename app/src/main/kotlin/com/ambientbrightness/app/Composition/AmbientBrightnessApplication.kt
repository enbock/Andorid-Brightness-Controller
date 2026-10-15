package com.ambientbrightness.app.Composition

import android.app.Application
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.PowerManager
import com.ambientbrightness.infrastructure.AmbientLightSensing.AmbientLightSensorClient.Android.AndroidAmbientLightSensorClient
import com.ambientbrightness.infrastructure.AmbientLightSensing.ScreenStateClient.Android.AndroidScreenStateClient
import com.ambientbrightness.infrastructure.AppSetupService.NotificationPermissionChecker.Android.AndroidNotificationPermissionChecker
import com.ambientbrightness.infrastructure.AppSetupService.SetupStateStore.Android.AndroidSetupStateStore
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.BuiltInAdaptiveBrightnessClient.Android.AndroidBuiltInAdaptiveBrightnessClient
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.DeviceBrightnessRangeClient.Android.AndroidDeviceBrightnessRangeClient
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.SystemBrightnessClient.Android.AndroidSystemBrightnessClient
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.WriteSettingsPermissionChecker.Android.AndroidWriteSettingsPermissionChecker
import com.ambientbrightness.infrastructure.BackgroundService.BatteryOptimizationClient.Android.AndroidBatteryOptimizationClient
import com.ambientbrightness.infrastructure.BackgroundService.ControllerStateStore.Android.AndroidControllerStateStore
import com.ambientbrightness.infrastructure.SettingsStore.SettingsStore.Android.AndroidDataStoreSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Creates the app-scoped object graph without placing Android dependencies in :core. */
class AmbientBrightnessApplication : Application() {

    lateinit var component: AmbientBrightnessComponent
        private set

    override fun onCreate() {
        super.onCreate()

        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val sensorManager = getSystemService(SensorManager::class.java)
        val powerManager = getSystemService(PowerManager::class.java)
        val displayManager = getSystemService(DisplayManager::class.java)
        val foregroundServiceHost = ForegroundServiceHostBridge(this)

        component = AmbientBrightnessComponent(
            sensorClient = AndroidAmbientLightSensorClient(sensorManager),
            screenStateClient = AndroidScreenStateClient(
                powerManager = powerManager,
                displayManager = displayManager,
                scope = appScope,
            ),
            controllerScope = appScope,
            brightnessClient = AndroidSystemBrightnessClient(this),
            deviceRangeClient = AndroidDeviceBrightnessRangeClient(),
            writeSettingsPermissionChecker = AndroidWriteSettingsPermissionChecker(this, appScope),
            builtInAdaptiveBrightnessClient = AndroidBuiltInAdaptiveBrightnessClient(this),
            settingsStore = AndroidDataStoreSettingsStore.create(context = this, scope = appScope),
            foregroundServiceHost = foregroundServiceHost,
            controllerStateStore = AndroidControllerStateStore(this),
            batteryOptimizationClient = AndroidBatteryOptimizationClient.create(this),
            notificationPermissionChecker = AndroidNotificationPermissionChecker.create(this),
            setupStateStore = AndroidSetupStateStore(this),
        )

        // The notification's Stop action reaches the service; forward it to the use case so it
        // travels the same path as a stop requested from the UI (F2 stop → restore → foreground stop).
        foregroundServiceHost.stopRequestHandler = { component.backgroundService.stop() }

        // A service the platform killed must be reported as StoppedBySystem, not as a user stop, so the
        // dashboard shows the real reason instead of a stale "running" state (US3, AC3.3).
        foregroundServiceHost.systemStopHandler = { component.recordSystemStop() }
    }
}
