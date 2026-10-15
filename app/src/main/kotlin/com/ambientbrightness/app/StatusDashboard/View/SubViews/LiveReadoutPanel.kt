package com.ambientbrightness.app.StatusDashboard.View.SubViews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ambientbrightness.app.R
import com.ambientbrightness.app.StatusDashboard.View.ClampHint
import com.ambientbrightness.app.StatusDashboard.View.LightReadout
import com.ambientbrightness.app.StatusDashboard.View.Task.LightLevelLabelResolver

/**
 * Shows what the sensor is measuring right now (US1, AC1.1/AC1.2).
 *
 * There is deliberately no fallback to a previous value: when [light] is [LightReadout.Unavailable]
 * the panel says so, so a stopped controller or a missing sensor never looks like a live reading
 * (US1, AC1.4).
 */
@Composable
internal fun LiveReadoutPanel(
    light: LightReadout,
    clampHint: ClampHint,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().testTag(TAG_LIVE_READOUT)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.dashboard_light_heading),
                style = MaterialTheme.typography.titleMedium,
            )

            when (light) {
                is LightReadout.Unavailable -> Text(
                    text = stringResource(R.string.dashboard_light_unavailable),
                    style = MaterialTheme.typography.bodyLarge,
                )

                is LightReadout.Reading -> {
                    Text(
                        text = stringResource(R.string.dashboard_lux_value, light.lux),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = stringResource(LightLevelLabelResolver.resolve(light.level)),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            if (clampHint is ClampHint.Limited) {
                Text(
                    text = stringResource(
                        if (clampHint.atMaximum) {
                            R.string.dashboard_clamp_maximum
                        } else {
                            R.string.dashboard_clamp_minimum
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

internal const val TAG_LIVE_READOUT = "live_readout"
