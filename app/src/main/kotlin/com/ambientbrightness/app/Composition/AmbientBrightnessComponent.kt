package com.ambientbrightness.app.Composition

import com.ambientbrightness.app.AmbientLightSensing.Collector.AmbientLightCollector
import com.ambientbrightness.app.AutomaticBrightnessControl.Collector.BrightnessStatusCollector
import com.ambientbrightness.app.BackgroundService.Collector.BrightnessServiceCollector
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
import com.ambientbrightness.core.BackgroundNotificationBuilder.BackgroundNotificationBuilder
import com.ambientbrightness.core.BackgroundService.BatteryOptimizationClient
import com.ambientbrightness.core.BackgroundService.ControllerStateStore
import com.ambientbrightness.core.BackgroundService.ForegroundServiceHost
import com.ambientbrightness.core.BackgroundServiceUseCase.BackgroundServiceUseCase
import com.ambientbrightness.core.BrightnessBackgroundService.BrightnessBackgroundService
import com.ambientbrightness.core.BrightnessOffsetService.BrightnessOffsetService
import com.ambientbrightness.core.BrightnessOffsetStateHolder.BrightnessOffsetStateHolder
import com.ambientbrightness.core.BrightnessOffsetTransform.BrightnessOffsetTransform
import com.ambientbrightness.core.ResponseTuning.ResponseTuningService.DefaultResponseTuningService
import com.ambientbrightness.core.ResponseTuning.ResponseTuningService.ResponseTuningService
import com.ambientbrightness.core.ResponsiveBrightnessRamp.ResponsiveBrightnessRamp
import com.ambientbrightness.core.SettingsStore.SettingsStore
import com.ambientbrightness.core.Shared.Clock
import com.ambientbrightness.core.Shared.SystemClock
import com.ambientbrightness.core.StopBrightnessRestorer.StopBrightnessRestorer
import com.ambientbrightness.core.UserConfigurableStabiliser.UserConfigurableStabiliser
import com.ambientbrightness.infrastructure.SettingsStore.SettingsStore.InMemory.InMemorySettingsStore
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.BuiltInAdaptiveBrightnessClient.Dummy.DummyBuiltInAdaptiveBrightnessClient
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.DeviceBrightnessRangeClient.Dummy.DummyDeviceBrightnessRangeClient
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.SystemBrightnessClient.Dummy.DummySystemBrightnessClient
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.WriteSettingsPermissionChecker.Dummy.DummyWriteSettingsPermissionChecker
import com.ambientbrightness.infrastructure.BackgroundService.BatteryOptimizationClient.Dummy.DummyBatteryOptimizationClient
import com.ambientbrightness.infrastructure.BackgroundService.ControllerStateStore.Dummy.DummyControllerStateStore
import com.ambientbrightness.infrastructure.BackgroundService.ForegroundServiceHost.DummyForegroundServiceHost
import kotlinx.coroutines.CoroutineScope

/**
 * App-scoped manual composition root for ambient sensing, automatic brightness control and the
 * background service. Its client parameters also let tests exercise the same controller boundaries
 * with deterministic clients instead of device sensors and settings. The brightness clients, the
 * background-service host, the [ControllerStateStore] and the [BatteryOptimizationClient] default to
 * the deterministic Dummy adapters, [settingsStore] to the in-memory store and [clock] to the system
 * clock, so the app stays runnable without a device; the Application passes the persistent Android
 * implementations instead.
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
    clock: Clock = SystemClock,
    foregroundServiceHost: ForegroundServiceHost = DummyForegroundServiceHost(),
    controllerStateStore: ControllerStateStore = DummyControllerStateStore(),
    batteryOptimizationClient: BatteryOptimizationClient = DummyBatteryOptimizationClient(),
) {

    private val sensorRangeLux = (sensorClient.availability as? SensorAvailability.Available)
        ?.profile
        ?.maxRangeLux
        ?.takeIf { it.isFinite() && it > 0f }
        ?: DEFAULT_FALLBACK_MAX_RANGE_LUX

    /** Response speed (F4): the setting the settings UI and F7 read and write. */
    val responseTuningService: ResponseTuningService = DefaultResponseTuningService(
        store = settingsStore,
        scope = controllerScope,
    )

    private val ramp = ResponsiveBrightnessRamp(
        responseSpeed = responseTuningService.responseSpeed,
        clock = clock,
    )

    /** Manual brightness offset (F3): the setting the settings UI and F7 read and write. */
    private val brightnessOffsetHolder = BrightnessOffsetStateHolder(
        store = settingsStore,
        scope = controllerScope,
    )

    /** Offset boundary (F7): the current offset and how far it could be applied. */
    val brightnessOffsetService: BrightnessOffsetService = brightnessOffsetHolder

    /**
     * Offset transform (F3). The holder is both the read side and the clamp-indicator sink, so the
     * running pipeline and the UI share the one offset state.
     */
    private val offsetTransform = BrightnessOffsetTransform(
        offsetSource = brightnessOffsetHolder,
        applicationSink = brightnessOffsetHolder,
    )

    private val useCase = AmbientLightUseCase(
        sensorClient = sensorClient,
        stabiliser = UserConfigurableStabiliser(
            delegate = OutlierClampingStabiliser(maxRangeLux = sensorRangeLux),
            responseSpeed = responseTuningService.responseSpeed,
            clock = clock,
        ),
        clock = clock,
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
     * above is the only other consumer of the single ambient-light stream. The transforms are
     * registered in fold order: F3's offset shifts the mapped fraction first, then F4's ramp governs
     * how fast that shift is applied (F3 leaves the speed to F4). The offset source is registered so
     * a change re-applies within 1 s without a new sample (AC1.1).
     */
    private val brightnessController = BrightnessControllerUseCase(
        ambientLightService = service,
        screenStateClient = screenStateClient,
        brightnessClient = brightnessClient,
        deviceRangeClient = deviceRangeClient,
        permissionChecker = writeSettingsPermissionChecker,
        builtInAdaptiveBrightnessClient = builtInAdaptiveBrightnessClient,
        scope = controllerScope,
    ).also { controller ->
        controller.registerTransform(offsetTransform)
        controller.registerTransform(ramp)
        controller.registerOffsetSource(brightnessOffsetHolder)
    }

    val controller: AutomaticBrightnessController = brightnessController

    val statusCollector = BrightnessStatusCollector(controller)

    /**
     * Background service (F5): owns the control loop's lifetime and the foreground notification. It
     * drives the same [brightnessController] the status collector above observes, so there is one
     * control loop, and it exposes the lifecycle through [BrightnessBackgroundService.state] for the
     * notification and F6/F7. The restore path writes through the same [brightnessClient], so there
     * is no parallel brightness mechanism (AC3.2).
     */
    val backgroundService: BrightnessBackgroundService = BackgroundServiceUseCase(
        controller = brightnessController,
        host = foregroundServiceHost,
        controllerStateStore = controllerStateStore,
        batteryOptimizationClient = batteryOptimizationClient,
        restorer = StopBrightnessRestorer(brightnessClient),
        notificationBuilder = BackgroundNotificationBuilder(),
        scope = controllerScope,
    )

    /** Thin forwarder of F5's state and battery-optimization exemption for F6/F7. */
    val backgroundServiceCollector = BrightnessServiceCollector(backgroundService)

    /** Immediately cancels service collection and unregisters any active sensor listener. */
    fun stop() {
        service.stop()
    }

    private companion object {
        const val DEFAULT_FALLBACK_MAX_RANGE_LUX = 1_000f
    }
}
