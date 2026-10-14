package com.ambientbrightness.infrastructure

import android.content.Intent
import com.ambientbrightness.core.BackgroundService.ControllerStateStore
import com.ambientbrightness.core.BackgroundService.StartReason
import com.ambientbrightness.infrastructure.BackgroundService.BootCompletedReceiver.BootCompletedReceiver
import org.junit.Assert.assertEquals
import org.junit.Test

class BootCompletedReceiverTest {

    @Test
    fun `a controller that was running is started again on boot`() {
        val receiver = BootCompletedReceiver()
        var starts = 0

        receiver.handleBootCompleted(
            action = Intent.ACTION_BOOT_COMPLETED,
            store = { StubControllerStateStore(desiredRunning = true) },
            startService = { starts++ },
        )

        assertEquals(1, starts)
    }

    @Test
    fun `a controller that was stopped is not started on boot`() {
        val receiver = BootCompletedReceiver()
        var starts = 0

        receiver.handleBootCompleted(
            action = Intent.ACTION_BOOT_COMPLETED,
            store = { StubControllerStateStore(desiredRunning = false) },
            startService = { starts++ },
        )

        assertEquals(0, starts)
    }

    @Test
    fun `a broadcast other than boot completion is ignored`() {
        val receiver = BootCompletedReceiver()
        var starts = 0
        var storeRead = false

        receiver.handleBootCompleted(
            action = Intent.ACTION_LOCKED_BOOT_COMPLETED,
            store = {
                storeRead = true
                StubControllerStateStore(desiredRunning = true)
            },
            startService = { starts++ },
        )

        assertEquals(0, starts)
        assertEquals(false, storeRead)
    }
}

private class StubControllerStateStore(
    override var desiredRunning: Boolean = false,
    override var lastStartReason: StartReason? = null,
) : ControllerStateStore
