package com.ambientbrightness.core.BackgroundNotificationBuilder

import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.BackgroundService.BackgroundNotificationContent
import com.ambientbrightness.core.BackgroundService.BackgroundServiceState
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards the pure state-to-notification mapping (US4): every [BackgroundServiceState] renders a
 * non-blank title/text with the right ongoing/stop flags, the applied brightness appears (AC4.1) and
 * the Stop action is offered exactly while the controller can still be stopped (AC4.2).
 */
class BackgroundNotificationBuilderTest {

    private val builder = BackgroundNotificationBuilder()

    @Test
    fun `Stopped - stopped text without a stop action`() {
        val content = builder.build(BackgroundServiceState.Stopped)

        assertNonBlank(content)
        assertFalse(content.ongoing)
        assertFalse(content.showStopAction)
    }

    @Test
    fun `Starting - starting text is ongoing and can be stopped`() {
        val content = builder.build(BackgroundServiceState.Starting)

        assertNonBlank(content)
        assertTrue(content.ongoing)
        assertTrue(content.showStopAction)
    }

    @Test
    fun `Running - applied brightness text is ongoing and can be stopped`() {
        val content = builder.build(BackgroundServiceState.Running(BrightnessValue(123)))

        assertNonBlank(content)
        assertTrue(content.ongoing)
        assertTrue(content.showStopAction)
    }

    @Test
    fun `StoppedBySystem - system-stop text without a stop action`() {
        val content = builder.build(BackgroundServiceState.StoppedBySystem)

        assertNonBlank(content)
        assertFalse(content.ongoing)
        assertFalse(content.showStopAction)
    }

    @Test
    fun `Failed - failure text includes the reason and offers no stop action`() {
        val content = builder.build(BackgroundServiceState.Failed("WRITE_SETTINGS revoked"))

        assertNonBlank(content)
        assertTrue(content.text.contains("WRITE_SETTINGS revoked"))
        assertFalse(content.ongoing)
        assertFalse(content.showStopAction)
    }

    @Test
    fun `AC4_1 - Running carries the applied brightness value in the text`() {
        val content = builder.build(BackgroundServiceState.Running(BrightnessValue(123)))

        assertTrue(content.text.contains("123"))
    }

    @Test
    fun `AC4_1 - Running without an applied brightness still renders a valid text`() {
        val content = builder.build(BackgroundServiceState.Running(appliedBrightness = null))

        assertNonBlank(content)
        assertFalse(content.text.contains("null"))
        assertTrue(content.ongoing)
        assertTrue(content.showStopAction)
    }

    @Test
    fun `AC4_2 - the stop action is offered exactly for Starting and Running`() {
        val offering = listOf(BackgroundServiceState.Starting, BackgroundServiceState.Running())
        val refusing = listOf(
            BackgroundServiceState.Stopped,
            BackgroundServiceState.StoppedBySystem,
            BackgroundServiceState.Failed("reason"),
        )

        assertTrue(offering.all { builder.build(it).showStopAction })
        assertFalse(refusing.any { builder.build(it).showStopAction })
    }

    @Test
    fun `AC4_3 - ongoing is true exactly for Starting and Running`() {
        val ongoing = listOf(BackgroundServiceState.Starting, BackgroundServiceState.Running())
        val dismissible = listOf(
            BackgroundServiceState.Stopped,
            BackgroundServiceState.StoppedBySystem,
            BackgroundServiceState.Failed("reason"),
        )

        assertTrue(ongoing.all { builder.build(it).ongoing })
        assertFalse(dismissible.any { builder.build(it).ongoing })
    }

    private fun assertNonBlank(content: BackgroundNotificationContent) {
        assertTrue(content.title.isNotBlank(), "title must not be blank")
        assertTrue(content.text.isNotBlank(), "text must not be blank")
    }
}
