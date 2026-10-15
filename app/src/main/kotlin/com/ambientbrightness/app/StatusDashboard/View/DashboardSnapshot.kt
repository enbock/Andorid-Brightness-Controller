package com.ambientbrightness.app.StatusDashboard.View

import com.ambientbrightness.core.AmbientLightSensing.SensorAvailability
import com.ambientbrightness.core.AppSetupService.SetupStatus
import com.ambientbrightness.core.BackgroundService.BackgroundServiceState
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffsetApplication

/**
 * One immutable read of every service value the dashboard needs, produced by F7's state collector.
 *
 * It is the raw input of `DashboardUiStateMapper`: plain service values, no Android and no
 * presentation decision, so the mapping stays a pure function that is testable on the JVM over the
 * full matrix of service states (US1–US4).
 *
 * @property serviceState F5's lifecycle state; the only source of "running" and of write failures.
 * @property availability F1's sensor availability, so "no sensor" is distinguishable from "stopped".
 * @property latestLux the most recent sampled reading, or null while stopped / before the first sample.
 * @property setupStatus F6's aggregated requirements, or null before the first emission.
 * @property canStart F6's Start gate; only blocking requirements make this false (US2, AC2.3).
 * @property offsetPercent F3's active offset in percent of the brightness span.
 * @property offsetApplication F3's clamp outcome, which becomes the "offset limited" hint (US3, AC1.1).
 * @property responseSpeedPercent F4's active response speed.
 */
data class DashboardSnapshot(
    val serviceState: BackgroundServiceState,
    val availability: SensorAvailability,
    val latestLux: Float?,
    val setupStatus: SetupStatus?,
    val canStart: Boolean,
    val offsetPercent: Int,
    val offsetApplication: BrightnessOffsetApplication,
    val responseSpeedPercent: Int,
)
