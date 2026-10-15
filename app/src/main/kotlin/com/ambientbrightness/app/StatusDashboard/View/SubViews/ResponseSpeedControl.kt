package com.ambientbrightness.app.StatusDashboard.View.SubViews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ambientbrightness.app.R
import com.ambientbrightness.core.ResponseTuning.ResponseSpeed

/**
 * The response-speed control (US1, AC1.3): one slider from fastest to slowest with a reset affordance,
 * so the user tunes how quickly the screen follows a light change without a second damping control.
 *
 * The emitted value is [snapResponseSpeedPercent]d first, so the service always receives a whole percent
 * inside F4's range.
 */
@Composable
internal fun ResponseSpeedControl(
    responseSpeedPercent: Int,
    canReset: Boolean,
    onResponseSpeedChange: (Int) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().testTag(TAG_RESPONSE_SPEED_CONTROL)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_response_speed_heading),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.dashboard_response_speed_value, responseSpeedPercent),
                style = MaterialTheme.typography.bodyLarge,
            )
            val description = stringResource(R.string.settings_response_speed_slider_description)
            Slider(
                value = responseSpeedPercent.toFloat(),
                onValueChange = { raw -> onResponseSpeedChange(snapResponseSpeedPercent(raw)) },
                valueRange = RESPONSE_SPEED_RANGE,
                steps = RESPONSE_SPEED_STEPS,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_RESPONSE_SPEED_SLIDER)
                    .semantics { contentDescription = description },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.settings_response_speed_fastest),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.settings_response_speed_slowest),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(
                onClick = onReset,
                enabled = canReset,
                modifier = Modifier.testTag(TAG_RESET_RESPONSE_SPEED_BUTTON),
            ) {
                Text(stringResource(R.string.settings_reset_response_speed))
            }
        }
    }
}

private val RESPONSE_SPEED_RANGE =
    ResponseSpeed.FASTEST_PERCENT.toFloat()..ResponseSpeed.SLOWEST_PERCENT.toFloat()

/** 101 discrete percent values between the two endpoints leave 99 steps in between. */
private const val RESPONSE_SPEED_STEPS = 99

internal const val TAG_RESPONSE_SPEED_CONTROL = "response_speed_control"
internal const val TAG_RESPONSE_SPEED_SLIDER = "response_speed_slider"
internal const val TAG_RESET_RESPONSE_SPEED_BUTTON = "reset_response_speed_button"
