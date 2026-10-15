package com.ambientbrightness.app.StatusDashboard.View.SubViews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ambientbrightness.app.R
import com.ambientbrightness.app.StatusDashboard.View.ControllerUiState
import com.ambientbrightness.app.StatusDashboard.View.Task.SetupTextResolver
import com.ambientbrightness.core.AppSetupService.SetupRequirementId

/**
 * The one control that starts and stops the controller (US2).
 *
 * The button is disabled exactly when F6 says Start is not possible, and the reason is shown next to
 * it (`US2, AC2.3`). An error is never hidden behind a "running" label (`US3, AC2`) and a system stop
 * is shown as such (`US3, AC3`).
 */
@Composable
internal fun ControllerControlPanel(
    controller: ControllerUiState,
    canStart: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().testTag(TAG_CONTROLLER_CONTROL)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.dashboard_controller_heading),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = statusText(controller),
                style = MaterialTheme.typography.bodyLarge,
            )

            if (controller is ControllerUiState.Failed) {
                Text(
                    text = stringResource(R.string.dashboard_controller_error_reason, controller.reason),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (controller is ControllerUiState.Blocked) {
                Text(
                    text = stringResource(
                        R.string.dashboard_start_blocked_reason,
                        blockersText(controller.blockers),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (controller.isActive) {
                val description = stringResource(R.string.dashboard_stop_content_description)
                Button(
                    onClick = onStop,
                    modifier = Modifier
                        .testTag(TAG_STOP_BUTTON)
                        .semantics { contentDescription = description },
                ) {
                    Text(stringResource(R.string.dashboard_stop))
                }
            } else {
                val description = stringResource(R.string.dashboard_start_content_description)
                Button(
                    onClick = onStart,
                    enabled = canStart,
                    modifier = Modifier
                        .testTag(TAG_START_BUTTON)
                        .semantics { contentDescription = description },
                ) {
                    Text(stringResource(R.string.dashboard_start))
                }
            }
        }
    }
}

@Composable
private fun statusText(controller: ControllerUiState): String = when (controller) {
    is ControllerUiState.Stopped -> stringResource(R.string.dashboard_controller_stopped)
    is ControllerUiState.Starting -> stringResource(R.string.dashboard_controller_starting)
    is ControllerUiState.Running -> controller.appliedBrightness?.let { brightness ->
        stringResource(R.string.dashboard_controller_running_brightness, brightness.value)
    } ?: stringResource(R.string.dashboard_controller_running)
    is ControllerUiState.Blocked -> stringResource(R.string.dashboard_controller_blocked)
    is ControllerUiState.Failed -> stringResource(R.string.dashboard_controller_failed)
    is ControllerUiState.StoppedBySystem -> stringResource(R.string.dashboard_controller_stopped_by_system)
}

@Composable
private fun blockersText(blockers: List<SetupRequirementId>): String =
    // `map` is inline, so the resource lookups still run in this composable's context.
    blockers.map { blocker -> stringResource(SetupTextResolver.requirementLabel(blocker)) }
        .joinToString(separator = ", ")

/** True while the controller owns the screen's brightness, i.e. Start must not be offered again. */
private val ControllerUiState.isActive: Boolean
    get() = this is ControllerUiState.Running || this is ControllerUiState.Starting

internal const val TAG_CONTROLLER_CONTROL = "controller_control"
internal const val TAG_START_BUTTON = "start_button"
internal const val TAG_STOP_BUTTON = "stop_button"
