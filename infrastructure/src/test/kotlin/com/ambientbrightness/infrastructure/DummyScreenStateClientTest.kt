package com.ambientbrightness.infrastructure

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DummyScreenStateClientTest {

    @Test
    fun `initial value defaults to screen on`() {
        assertTrue(DummyScreenStateClient().isScreenOn.value)
    }

    @Test
    fun `initial value honours the constructor argument`() {
        assertFalse(DummyScreenStateClient(initiallyScreenOn = false).isScreenOn.value)
    }

    @Test
    fun `setScreenOn updates the emitted value`() {
        val client = DummyScreenStateClient()

        client.setScreenOn(false)
        assertFalse(client.isScreenOn.value)

        client.setScreenOn(true)
        assertTrue(client.isScreenOn.value)
    }
}
