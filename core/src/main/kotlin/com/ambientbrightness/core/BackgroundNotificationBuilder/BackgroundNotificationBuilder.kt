package com.ambientbrightness.core.BackgroundNotificationBuilder

import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.BackgroundService.BackgroundNotificationContent
import com.ambientbrightness.core.BackgroundService.BackgroundServiceState

/**
 * Pure, side-effect-free mapping of a [BackgroundServiceState] onto the
 * [BackgroundNotificationContent] the ongoing notification renders (US4).
 *
 * The class is deliberately stateless and Android-free so it can be built and asserted in plain JVM
 * tests (T2), and so F7/T4 can construct, inject or substitute it without a device. Every
 * [BackgroundServiceState] variant is handled exhaustively: the notification always says what is
 * going on and, while the controller can still be stopped, offers the single **Stop** action
 * (AC4.2).
 *
 * The applied brightness is derived from the state alone — the builder never reads the system
 * brightness setting, so the notification cannot disagree with the controller (design: "the
 * notification never re-reads the system setting").
 *
 * The [BackgroundNotificationContent.ongoing] flag is what makes the notification non-dismissible
 * while the service runs (AC4.3); the actual `setOngoing(true)` call is platform work owned by T4.
 */
class BackgroundNotificationBuilder {

    /**
     * Renders [state] into the title/text and the Stop/ongoing flags for the notification (US4).
     *
     * The mapping is exhaustive over [BackgroundServiceState]: `Starting`/`Running` are the states in
     * which the controller is alive and can be stopped (so they are ongoing and show the Stop
     * action), while `Stopped`, `StoppedBySystem` and `Failed` are terminal and show neither.
     */
    fun build(state: BackgroundServiceState): BackgroundNotificationContent = when (state) {
        is BackgroundServiceState.Starting -> BackgroundNotificationContent(
            title = "$CONTROLLER_NAME is starting",
            text = "Starting the brightness controller...",
            ongoing = true,
            showStopAction = true,
        )

        is BackgroundServiceState.Running -> BackgroundNotificationContent(
            title = "$CONTROLLER_NAME is running",
            text = brightnessText(state.appliedBrightness),
            ongoing = true,
            showStopAction = true,
        )

        is BackgroundServiceState.Stopped -> BackgroundNotificationContent(
            title = "$CONTROLLER_NAME is stopped",
            text = "The brightness controller is stopped.",
            ongoing = false,
            showStopAction = false,
        )

        is BackgroundServiceState.StoppedBySystem -> BackgroundNotificationContent(
            title = "$CONTROLLER_NAME is stopped",
            text = "Android stopped the brightness controller. Open the app to start it again.",
            ongoing = false,
            showStopAction = false,
        )

        is BackgroundServiceState.Failed -> BackgroundNotificationContent(
            title = "$CONTROLLER_NAME stopped",
            text = "The brightness controller stopped: ${state.reason}",
            ongoing = false,
            showStopAction = false,
        )
    }

    /**
     * Derives the running body text from the [BrightnessValue] the state carries (AC4.1).
     *
     * The value comes only from the state — F2's `BrightnessStatus.appliedBrightness` — never from a
     * fresh read of the system setting. When the controller has not applied a value yet, a neutral
     * waiting text is rendered so the notification stays valid and non-blank.
     */
    private fun brightnessText(appliedBrightness: BrightnessValue?): String =
        if (appliedBrightness == null) {
            "Waiting for the first light reading"
        } else {
            "Applied brightness: ${appliedBrightness.value}"
        }

    companion object {
        /** User-facing name of the controller the notification describes. */
        const val CONTROLLER_NAME: String = "Ambient brightness control"
    }
}
