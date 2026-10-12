package com.ambientbrightness.app.Composition

import com.ambientbrightness.app.AmbientLightSensing.Collector.AmbientLightCollector
import com.ambientbrightness.app.AutomaticBrightnessControl.Collector.BrightnessStatusCollector
import com.ambientbrightness.core.AmbientLightSensing.AmbientLightSensorClient
import com.ambientbrightness.core.AmbientLightSensing.AmbientLightService.DefaultAmbientLightService
import com.ambientbrightness.core.AmbientLightSensing.AmbientLightUseCase.AmbientLightUseCase
import com.ambientbrightness.core.AmbientLightSensing.ReadingStabiliser.OutlierClampingStabiliser
import com.ambientbrightness.core.AmbientLightSensing.ScreenStateClient
import com.ambientbrightness.core.AmbientLightSensing.SensorAvailability
import com.ambientbrightness.core.AutomaticBrightnessControl.AutomaticBrightnessController
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessControllerUseCase.BrightnessControllerUseCase
import com.ambientbrightness.core.AutomaticBrightnessControl.BuiltInAdaptiveBrightnessClient
import com.ambientbrightness.core.AutomaticBrightnessControl.DeviceBrightnessRangeClient
import com.ambientbrightness.core.AutomaticBrightnessControl.SystemBrightnessClient
import com.ambientbrightness.core.AutomaticBrightnessControl.WriteSettingsPermissionChecker
import com.ambientbrightness.core.SettingsStore.SettingsStore
import com.ambientbrightness.core.Shared.SystemClock
import com.ambientbrightness.infrastructure.SettingsStore.SettingsStore.InMemory.InMemorySettingsStore
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.BuiltInAdaptiveBrightnessClient.Dummy.DummyBuiltInAdaptiveBrightnessClient
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.DeviceBrightnessRangeClient.Dummy.DummyDeviceBrightnessRangeClient
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.SystemBrightnessClient.Dummy.DummySystemBrightnessClient
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.WriteSettingsPermissionChecker.Dummy.DummyWriteSettingsPermissionChecker
import kotlinx.coroutines.CoroutineScope

/**
 * App-scoped manual composition root for ambient sensing and automatic brightness control. Its
 * client parameters also let tests exercise the same controller boundaries with deterministic
 * clients instead of device sensors and settings. The brightness clients default to the
 * deterministic Dummy adapters, and [settingsStore] to the in-memory store, so the app stays
 * runnable without a device; the Application passes the persistent DataStore-backed store instead.
 */
class AmbientBrightnessComponent(
    sensorClient: AmbientLightSensorClient,
    screenStateClient: ScreenStateClient,
    controllerScope: CoroutineScope,
    brightnessClient: SystemBrightnessClient = DummySystemBrightnessClient(),
    deviceRangeClient: DeviceBrightnessRangeClient = DummyDeviceBrightnessRangeClient(),
    writeSettingsPermissionChecker: WriteSettingsPermissionChecker =
        DummyWriteSettingsPermissionChecker(),
    builtInAdaptiveBrightnessClient: BuiltInAdaptiveBrightnessClient =
        DummyBuiltInAdaptiveBrightnessClient(),
    val settingsStore: SettingsStore = InMemorySettingsStore(),
) {

    private val sensorRangeLux = (sensorClient.availability as? SensorAvailability.Available)
        ?.profile
        ?.maxRangeLux
        ?.takeIf { it.isFinite() && it > 0f }
        ?: DEFAULT_FALLBACK_MAX_RANGE_LUX

    private val useCase = AmbientLightUseCase(
        sensorClient = sensorClient,
        stabiliser = OutlierClampingStabiliser(maxRangeLux = sensorRangeLux),
        clock = SystemClock,
    )

    private val service = DefaultAmbientLightService(
        sensorClient = sensorClient,
        useCase = useCase,
    )

    val collector = AmbientLightCollector(
        service = service,
        screenStateClient = screenStateClient,
    )

    /**
     * Automatic brightness controller (F2). It is started by whoever owns the control loop's
     * lifetime (the F5 background service); the activity never starts it, because F1's collector
     * above is the only other consumer of the single ambient-light stream.
     */
    val controller: AutomaticBrightnessController = BrightnessControllerUseCase(
        ambientLightService = service,
        screenStateClient = screenStateClient,
        brightnessClient = brightnessClient,
        deviceRangeClient = deviceRangeClient,
        permissionChecker = writeSettingsPermissionChecker,
        builtInAdaptiveBrightnessClient = builtInAdaptiveBrightnessClient,
        scope = controllerScope,
    )

    val statusCollector = BrightnessStatusCollector(controller)

    /** Immediately cancels service collection and unregisters any active sensor listener. */
    fun stop() {
        service.stop()
    }

    private companion object {
        const val DEFAULT_FALLBACK_MAX_RANGE_LUX = 1_000f
    }
}
