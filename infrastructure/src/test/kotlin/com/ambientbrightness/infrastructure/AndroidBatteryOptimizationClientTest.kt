package com.ambientbrightness.infrastructure

import android.provider.Settings
import com.ambientbrightness.infrastructure.BackgroundService.BatteryOptimizationClient.Android.AndroidBatteryOptimizationClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidBatteryOptimizationClientTest {

    @Test
    fun `the platform exemption is read at construction and exposed`() = runTest {
        assertTrue(client(exemptionReader = { true }).isExempt.first())
        assertFalse(client(exemptionReader = { false }).isExempt.first())
    }

    @Test
    fun `refresh re-reads the platform exemption`() = runTest {
        var exempt = false
        val client = client(exemptionReader = { exempt })

        assertFalse(client.isExempt.first())

        exempt = true
        client.refresh()

        assertTrue(client.isExempt.first())
    }

    @Test
    fun `request exemption opens the advisory settings screen`() {
        val openedActions = mutableListOf<String>()

        client(settingsScreenOpener = { openedActions += it }).requestExemption()

        assertEquals(listOf(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), openedActions)
        assertNotEquals(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            openedActions.single(),
        )
    }

    @Test
    fun `open settings opens the same advisory screen`() {
        val openedActions = mutableListOf<String>()

        client(settingsScreenOpener = { openedActions += it }).openSettings()

        assertEquals(listOf(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), openedActions)
    }

    @Test
    fun `opening the screen refreshes the exemption`() {
        var exempt = false
        val client = client(exemptionReader = { exempt })

        exempt = true
        client.openSettings()

        assertTrue(client.isExempt.value)
    }

    private fun client(
        exemptionReader: () -> Boolean = { false },
        settingsScreenOpener: (String) -> Unit = {},
    ) = AndroidBatteryOptimizationClient(exemptionReader, settingsScreenOpener)
}
