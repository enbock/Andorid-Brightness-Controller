package com.ambientbrightness.core.AppSetupService

/**
 * The requirements the setup checklist can report.
 *
 * Fixed severity mapping: [AmbientLightSensor] and [WriteSettings] are
 * [RequirementSeverity.Blocking]; [Notifications] and [BatteryOptimization] are
 * [RequirementSeverity.Advisory]. Only blocking requirements gate Start; the advisory ones are
 * surfaced so the app stays honest about degraded situations (US4).
 */
enum class SetupRequirementId {

    /** An ambient light sensor must exist and be usable (US4, AC4.1); blocking. */
    AmbientLightSensor,

    /** `WRITE_SETTINGS` must be granted to change the system brightness (US2, AC2.1); blocking. */
    WriteSettings,

    /** `POST_NOTIFICATIONS` decides whether the ongoing notification is visible (US4, AC4.3); advisory. */
    Notifications,

    /** Exempting the app from battery optimization keeps the controller alive (US2, AC2.3); advisory. */
    BatteryOptimization,
}
