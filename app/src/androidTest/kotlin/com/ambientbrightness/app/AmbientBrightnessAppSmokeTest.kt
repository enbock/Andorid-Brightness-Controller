package com.ambientbrightness.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ambientbrightness.app.StatusDashboard.View.SubViews.TAG_CONTROLLER_CONTROL
import com.ambientbrightness.app.StatusDashboard.View.SubViews.TAG_SETTINGS_ENTRY
import com.ambientbrightness.app.StatusDashboard.View.TAG_DASHBOARD
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app must boot straight into F7's dashboard and render it through the whole real graph, including
 * the ON_RESUME reconciliation hook, without any emulator hardware being involved (US1).
 *
 * Needs an emulator or device; without one it is compile-verified only.
 */
@RunWith(AndroidJUnit4::class)
class AmbientBrightnessAppSmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun theAppBootsToTheDashboard() {
        compose.onNodeWithTag(TAG_DASHBOARD).assertIsDisplayed()
        compose.onNodeWithTag(TAG_CONTROLLER_CONTROL).assertIsDisplayed()
        compose.onNodeWithTag(TAG_SETTINGS_ENTRY).assertIsDisplayed()
    }
}
