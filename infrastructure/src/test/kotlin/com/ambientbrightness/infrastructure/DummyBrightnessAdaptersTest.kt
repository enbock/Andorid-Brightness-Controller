package com.ambientbrightness.infrastructure

import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessRange
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessWriteResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DummyBrightnessAdaptersTest {

    @Test
    fun `dummy system client reads current value and applies successful writes`() {
        val client = DummySystemBrightnessClient(initiallyCurrentValue = 40)

        assertEquals(40, client.readCurrent())
        val result = client.write(BrightnessValue(72))

        assertEquals(BrightnessWriteResult.Applied(BrightnessValue(72)), result)
        assertEquals(72, client.readCurrent())
    }

    @Test
    fun `dummy system client returns injected failure without changing current value`() {
        val client = DummySystemBrightnessClient(
            initiallyCurrentValue = 40,
            writeFailure = { "simulated denial for ${it.value}" },
        )

        assertEquals(
            BrightnessWriteResult.Failed("simulated denial for 72"),
            client.write(BrightnessValue(72)),
        )
        assertEquals(40, client.readCurrent())
    }

    @Test
    fun `dummy system client allows current value and failure to be changed`() {
        val client = DummySystemBrightnessClient()
        assertNull(client.readCurrent())

        client.setCurrentValue(16)
        client.setWriteFailure { "" }
        assertEquals(
            BrightnessWriteResult.Failed("Injected system brightness write failure."),
            client.write(BrightnessValue(25)),
        )
        assertEquals(16, client.readCurrent())

        client.setWriteFailure(null)
        assertEquals(BrightnessWriteResult.Applied(BrightnessValue(25)), client.write(BrightnessValue(25)))
    }

    @Test
    fun `dummy range client exposes and updates nullable range`() {
        val range = BrightnessRange(BrightnessValue(1), BrightnessValue(300))
        val client = DummyDeviceBrightnessRangeClient(initiallyRange = range)

        assertEquals(range, client.readRange())
        client.setRange(null)
        assertNull(client.readRange())
    }

    @Test
    fun `dummy permission checker starts at configured grant and can change`() {
        val client = DummyWriteSettingsPermissionChecker(initiallyGranted = false)
        assertFalse(client.hasWriteSettingsPermission.value)

        client.setPermissionGranted(true)
        assertTrue(client.hasWriteSettingsPermission.value)
    }
}
