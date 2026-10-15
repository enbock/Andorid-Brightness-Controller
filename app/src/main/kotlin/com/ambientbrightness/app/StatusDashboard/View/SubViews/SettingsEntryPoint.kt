package com.ambientbrightness.app.StatusDashboard.View.SubViews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ambientbrightness.app.R

/**
 * The way to the settings screen without losing sight of the state (US4, AC1.1).
 *
 * It repeats the active offset and response speed so the dashboard stays informative even though the
 * controls live on their own screen.
 */
@Composable
internal fun SettingsEntryPoint(
    offsetPercent: Int,
    responseSpeedPercent: Int,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().testTag(TAG_SETTINGS_ENTRY)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.dashboard_settings_heading),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.dashboard_offset_value, offsetPercent),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.dashboard_response_speed_value, responseSpeedPercent),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag(TAG_OPEN_SETTINGS_BUTTON),
            ) {
                Text(stringResource(R.string.dashboard_open_settings))
            }
        }
    }
}

internal const val TAG_SETTINGS_ENTRY = "settings_entry"
internal const val TAG_OPEN_SETTINGS_BUTTON = "open_settings_button"
