package com.ambientbrightness.app.StatusDashboard.View.SubViews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ambientbrightness.app.R
import com.ambientbrightness.app.StatusDashboard.View.Task.SetupTextResolver
import com.ambientbrightness.core.AppSetupService.SetupAction
import com.ambientbrightness.core.AppSetupService.SetupStatus

/**
 * The permanent "what is still missing" view (US3, AC3.1/AC3.2).
 *
 * Each requirement keeps its own state, and only a requirement with something the user can do offers
 * the action, which the dashboard forwards through `onSetupAction` so F6 owns the platform step.
 */
@Composable
internal fun SetupSummaryPanel(
    setupSummary: SetupStatus?,
    onSetupAction: (SetupAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().testTag(TAG_SETUP_SUMMARY)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.dashboard_setup_heading),
                style = MaterialTheme.typography.titleMedium,
            )

            if (setupSummary == null) {
                Text(
                    text = stringResource(R.string.dashboard_setup_unknown),
                    style = MaterialTheme.typography.bodyLarge,
                )
                return@Column
            }

            setupSummary.requirements.forEach { requirement ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(SetupTextResolver.requirementLabel(requirement.id)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(SetupTextResolver.stateLabel(requirement.state)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    if (requirement.action != SetupAction.None) {
                        TextButton(onClick = { onSetupAction(requirement.action) }) {
                            Text(stringResource(R.string.dashboard_setup_action))
                        }
                    }
                }
            }
        }
    }
}

internal const val TAG_SETUP_SUMMARY = "setup_summary"
