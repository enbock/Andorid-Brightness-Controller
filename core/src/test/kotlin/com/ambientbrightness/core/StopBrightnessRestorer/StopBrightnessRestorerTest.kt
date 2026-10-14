package com.ambientbrightness.core.StopBrightnessRestorer

import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessSnapshot
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessWriteResult
import com.ambientbrightness.core.testdoubles.RecordingSystemBrightnessClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StopBrightnessRestorerTest {

    @Test
    fun `a missing snapshot writes nothing`() {
        val client = RecordingSystemBrightnessClient()
        val restorer = StopBrightnessRestorer(client)

        val result = restorer.restore(null)

        assertNull(result)
        assertEquals(0, client.writeAttempts, "nothing was captured, so nothing may be overwritten")
    }

    @Test
    fun `an existing snapshot is written back through the system brightness client`() {
        val client = RecordingSystemBrightnessClient()
        val restorer = StopBrightnessRestorer(client)

        val result = restorer.restore(BrightnessSnapshot(BrightnessValue(77)))

        assertEquals(BrightnessWriteResult.Applied(BrightnessValue(77)), result)
        assertEquals(BrightnessValue(77), client.lastWrite)
    }

    @Test
    fun `a failing client is reported and never thrown`() {
        val client = RecordingSystemBrightnessClient().apply { failureReason = "WRITE_SETTINGS revoked" }
        val restorer = StopBrightnessRestorer(client)

        val result = restorer.restore(BrightnessSnapshot(BrightnessValue(77)))

        assertInstanceOf(BrightnessWriteResult.Failed::class.java, result)
        assertEquals("WRITE_SETTINGS revoked", (result as BrightnessWriteResult.Failed).reason)
    }
}
