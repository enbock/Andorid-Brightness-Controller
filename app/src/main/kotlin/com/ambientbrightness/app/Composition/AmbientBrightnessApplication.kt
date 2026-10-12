package com.ambientbrightness.app.Composition

import android.app.Application
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.PowerManager
import com.ambientbrightness.infrastructure.AmbientLightSensing.AmbientLightSensorClient.Android.AndroidAmbientLightSensorClient
import com.ambientbrightness.infrastructure.AmbientLightSensing.ScreenStateClient.Android.AndroidScreenStateClient
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.BuiltInAdaptiveBrightnessClient.Android.AndroidBuiltInAdaptiveBrightnessClient
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.DeviceBrightnessRangeClient.Android.AndroidDeviceBrightnessRangeClient
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.SystemBrightnessClient.Android.AndroidSystemBrightnessClient
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.WriteSettingsPermissionChecker.Android.AndroidWriteSettingsPermissionChecker
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
        )
    }
}
