package com.ambientbrightness.app.StatusDashboard.View.SubViews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffsetRange

/**
 * The offset control (US3, AC3.1–AC3.3): a symmetric slider from −50 % to +50 % in 1 % steps plus a
 * reset affordance that is only offered when it would change something.
 *
 * The slider is continuous, so the value is [snapOffsetPercent]d before [onOffsetChange] is called and
 * the domain can never see an invalid offset.
 */
@Composable
internal fun OffsetControl(
    offsetPercent: Int,
    canReset: Boolean,
    onOffsetChange: (Int) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().testTag(TAG_OFFSET_CONTROL)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_offset_heading),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.dashboard_offset_value, offsetPercent),
                style = MaterialTheme.typography.bodyLarge,
            )
            val description = stringResource(R.string.settings_offset_slider_description)
            Slider(
                value = offsetPercent.toFloat(),
                onValueChange = { raw -> onOffsetChange(snapOffsetPercent(raw)) },
                valueRange = OFFSET_RANGE,
                steps = OFFSET_STEPS,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_OFFSET_SLIDER)
                    .semantics { contentDescription = description },
            )
            TextButton(
                onClick = onReset,
                enabled = canReset,
                modifier = Modifier.testTag(TAG_RESET_OFFSET_BUTTON),
            ) {
                Text(stringResource(R.string.settings_reset_offset))
            }
        }
    }
}

private val OFFSET_RANGE =
    BrightnessOffsetRange.MIN.toFloat()..BrightnessOffsetRange.MAX.toFloat()

/** 101 discrete percent values between the two endpoints leave 99 steps in between. */
private const val OFFSET_STEPS = 99

internal const val TAG_OFFSET_CONTROL = "offset_control"
internal const val TAG_OFFSET_SLIDER = "offset_slider"
internal const val TAG_RESET_OFFSET_BUTTON = "reset_offset_button"
