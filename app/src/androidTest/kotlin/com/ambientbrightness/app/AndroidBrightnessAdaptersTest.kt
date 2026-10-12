package com.ambientbrightness.app

import android.content.res.Resources
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessWriteResult
import com.ambientbrightness.core.AutomaticBrightnessControl.BuiltInAdaptiveBrightnessState
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.BuiltInAdaptiveBrightnessClient.Android.AndroidBuiltInAdaptiveBrightnessClient
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.DeviceBrightnessRangeClient.Android.AndroidDeviceBrightnessRangeClient
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.SystemBrightnessClient.Android.AndroidSystemBrightnessClient
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.WriteSettingsPermissionChecker.Android.AndroidWriteSettingsPermissionChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

@RunWith(AndroidJUnit4::class)
class AndroidBrightnessAdaptersTest {

    @Test
    fun deviceRangeIsValidWhenTheSystemResourceIsAvailable() {
        val resources = Resources.getSystem()
        val resourceId = resources.getIdentifier(
            "config_screenBrightnessSettingMaximum",
            "integer",
            "android",
        )
        assertTrue("API 36 emulator must expose the configured system maximum.", resourceId != 0)
        val discoveredMaximum = resources.getInteger(resourceId)
        assertTrue(discoveredMaximum >= BrightnessValue.MINIMUM)
        val range = AndroidDeviceBrightnessRangeClient().readRange()

        assertNotNull(range)
        assertEquals(discoveredMaximum, range!!.max.value)
        assertEquals(BrightnessValue.MINIMUM, range.min.value)
        assertTrue(range.max.value >= range.min.value)
    }

    @Test
    fun systemWriteReadsBackAndRestoresBrightness() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(
            "Grant WRITE_SETTINGS to com.ambientbrightness.app with adb appops before this test.",
            Settings.System.canWrite(context),
        )

        val client = AndroidSystemBrightnessClient(context)
        val originalValue = client.readCurrent()
        assertNotNull("Current screen brightness must be readable to restore safely.", originalValue)
        val maximum = AndroidDeviceBrightnessRangeClient().readRange()?.max?.value ?: 255
        assumeTrue("A nearby distinct positive brightness value must be available.", maximum > 1)

        val original = originalValue!!
        val requestedValue = if (original < maximum) {
            (original + 1).coerceAtLeast(BrightnessValue.MINIMUM)
        } else {
            maximum - 1
        }
        assumeTrue("Could not choose a distinct valid brightness value.", requestedValue != original)

        try {
            assertEquals(
                BrightnessWriteResult.Applied(BrightnessValue(requestedValue)),
                client.write(BrightnessValue(requestedValue)),
            )
            assertEquals(requestedValue, client.readCurrent())
        } finally {
            assertTrue(
                "Failed to restore original SCREEN_BRIGHTNESS value $original.",
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    original,
                ),
            )
            assertEquals(original, client.readCurrent())
        }
    }

    @Test
    fun permissionStateStartsWithTheLiveGrantAndObservationCanStop() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expected = Settings.System.canWrite(context)
        assertTrue("The test requires adb-granted WRITE_SETTINGS.", expected)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val checker = AndroidWriteSettingsPermissionChecker(context, scope)
        var collection: kotlinx.coroutines.Job? = null

        try {
            assertEquals(expected, withTimeout(5_000) { checker.hasWriteSettingsPermission.first() })
            collection = launch { checker.hasWriteSettingsPermission.collect { } }
            delay(100)
            setWriteSettingsAppOpMode("ignore")
            assertFalse(withTimeout(5_000) { checker.hasWriteSettingsPermission.first { !it } })
            setWriteSettingsAppOpMode("allow")
            assertTrue(withTimeout(5_000) { checker.hasWriteSettingsPermission.first { it } })
        } finally {
            setWriteSettingsAppOpMode("allow")
            collection?.cancelAndJoin()
            scope.cancel()
        }
    }

    @Test
    fun builtInAdaptiveBrightnessModeFollowsTheSystemSetting() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(
            "Grant WRITE_SETTINGS to com.ambientbrightness.app with adb appops before this test.",
            Settings.System.canWrite(context),
        )

        val client = AndroidBuiltInAdaptiveBrightnessClient(context)
        val resolver = context.contentResolver
        val originalMode = Settings.System.getInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
        assertEquals(expectedAdaptiveBrightnessState(originalMode), client.readState())

        try {
            assertTrue(
                Settings.System.putInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
                ),
            )
            assertEquals(BuiltInAdaptiveBrightnessState.ENABLED, client.readState())

            assertTrue(
                Settings.System.putInt(
                    resolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                ),
            )
            assertEquals(BuiltInAdaptiveBrightnessState.DISABLED, client.readState())
        } finally {
            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                originalMode,
            )
            assertEquals(expectedAdaptiveBrightnessState(originalMode), client.readState())
        }
    }

    private fun expectedAdaptiveBrightnessState(mode: Int): BuiltInAdaptiveBrightnessState =
        when (mode) {
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC ->
                BuiltInAdaptiveBrightnessState.ENABLED

            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL ->
                BuiltInAdaptiveBrightnessState.DISABLED

            else -> BuiltInAdaptiveBrightnessState.UNKNOWN
        }

    private fun setWriteSettingsAppOpMode(mode: String) {        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val descriptor = instrumentation.uiAutomation.executeShellCommand(
            "appops set com.ambientbrightness.app WRITE_SETTINGS $mode",
        )
        val output = descriptor.use { it.toCommandOutput() }
        assertFalse("appops set failed: $output", output.contains("Error:"))
    }

    private fun ParcelFileDescriptor.toCommandOutput(): String =
        FileInputStream(fileDescriptor).bufferedReader().use { it.readText() }
}
