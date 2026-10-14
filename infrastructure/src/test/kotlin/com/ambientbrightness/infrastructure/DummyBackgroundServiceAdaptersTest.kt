package com.ambientbrightness.infrastructure

import com.ambientbrightness.core.BackgroundService.StartReason
import com.ambientbrightness.infrastructure.BackgroundService.BatteryOptimizationClient.Dummy.DummyBatteryOptimizationClient
import com.ambientbrightness.infrastructure.BackgroundService.ControllerStateStore.Dummy.DummyControllerStateStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DummyBackgroundServiceAdaptersTest {

    @Test
    fun `the dummy store keeps the desired state and reason in memory`() {
        val store = DummyControllerStateStore()

        assertFalse(store.desiredRunning)
        assertNull(store.lastStartReason)

        store.desiredRunning = true
        store.lastStartReason = StartReason.Boot

        assertTrue(store.desiredRunning)
        assertEquals(StartReason.Boot, store.lastStartReason)
    }

    @Test
    fun `the dummy battery client exposes and toggles the exemption`() = runTest {
        val client = DummyBatteryOptimizationClient()

        assertFalse(client.isExempt.first())

        client.setExempt(true)

        assertTrue(client.isExempt.first())
    }

    @Test
    fun `the dummy battery client records the two launch calls separately`() {
        val client = DummyBatteryOptimizationClient()

        client.requestExemption()
        client.openSettings()
        client.openSettings()

        assertEquals(1, client.exemptionRequests)
        assertEquals(2, client.settingsOpened)
    }
}
