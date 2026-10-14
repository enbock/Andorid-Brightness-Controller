package com.ambientbrightness.app

import android.provider.Settings
import com.ambientbrightness.app.SetupFlow.Navigator.SetupFlowNavigator
import com.ambientbrightness.app.SetupFlow.Navigator.SetupScreen
import com.ambientbrightness.core.AppSetupService.SetupAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests of the setup navigation: the WriteSettings fallback chain and the other actions. */
class SetupFlowNavigatorTest {

    @Test
    fun `the per-app write-settings screen is used when the device can handle it`() {
        val launched = mutableListOf<SetupScreen>()

        navigator(launched).perform(SetupAction.OpenWriteSettings)

        assertEquals(
            listOf(SetupScreen(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:$PACKAGE_NAME")),
            launched,
        )
    }

    @Test
    fun `a missing per-app screen falls back to the plain write-settings action`() {
        val launched = mutableListOf<SetupScreen>()
        val navigator = navigator(launched) { screen -> screen.dataUri == null }

        navigator.perform(SetupAction.OpenWriteSettings)

        assertEquals(
            listOf(
                SetupScreen(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:$PACKAGE_NAME"),
                SetupScreen(Settings.ACTION_MANAGE_WRITE_SETTINGS),
            ),
            launched,
        )
    }

    @Test
    fun `a missing write-settings screen falls back to the app details screen`() {
        val launched = mutableListOf<SetupScreen>()
        val navigator = navigator(launched) { screen ->
            screen.action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        }

        navigator.perform(SetupAction.OpenWriteSettings)

        assertEquals(
            SetupScreen(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$PACKAGE_NAME"),
            launched.last(),
        )
    }

    @Test
    fun `no screen at all is tolerated without crashing`() {
        val launched = mutableListOf<SetupScreen>()

        navigator(launched) { false }.perform(SetupAction.OpenWriteSettings)

        assertEquals("every candidate was tried and none crashed", 3, launched.size)
    }

    @Test
    fun `the battery row opens the advisory settings screen`() {
        val launched = mutableListOf<SetupScreen>()

        navigator(launched).perform(SetupAction.OpenBatteryOptimizationSettings)

        assertEquals(
            listOf(SetupScreen(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)),
            launched,
        )
    }

    @Test
    fun `the notification row requests the runtime permission instead of a screen`() {
        val launched = mutableListOf<SetupScreen>()
        var requested = 0

        val navigator = SetupFlowNavigator(
            packageName = PACKAGE_NAME,
            requestNotificationPermission = { requested++ },
            launchScreen = { screen ->
                launched += screen
                true
            },
        )
        navigator.perform(SetupAction.RequestNotificationPermission)

        assertEquals(1, requested)
        assertTrue("no screen may be opened for the runtime permission", launched.isEmpty())
    }

    @Test
    fun `an action with nothing to do starts nothing`() {
        val launched = mutableListOf<SetupScreen>()

        navigator(launched).perform(SetupAction.None)

        assertTrue(launched.isEmpty())
    }

    /** Records every attempted screen and reports it as handled, unless [handled] says otherwise. */
    private fun navigator(
        launched: MutableList<SetupScreen>,
        handled: (SetupScreen) -> Boolean = { true },
    ): SetupFlowNavigator = SetupFlowNavigator(
        packageName = PACKAGE_NAME,
        launchScreen = { screen ->
            launched += screen
            handled(screen)
        },
    )

    private companion object {
        const val PACKAGE_NAME = "com.example.app"
    }
}
