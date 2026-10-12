package com.ambientbrightness.core.MinimumStepDeadBand

import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessWriteResult
import com.ambientbrightness.core.AutomaticBrightnessControl.MinimumStepDeadBand.MinimumStepDeadBand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MinimumStepDeadBandTest {

    @Test
    fun `AC1_2 request exactly one configured step from last applied is not suppressed`() {
        val deadBand = MinimumStepDeadBand()
        deadBand.recordApplied(BrightnessValue(10))

        assertNull(deadBand.suppressionFor(BrightnessValue(11)))
        assertEquals(1, MinimumStepDeadBand.DEFAULT_MINIMUM_STEP)
    }

    @Test
    fun `AC1_2 request below configured minimum step returns suppressed status`() {
        val deadBand = MinimumStepDeadBand(minimumStep = 2)
        deadBand.recordApplied(BrightnessValue(10))

        assertEquals(
            BrightnessWriteResult.Suppressed(BrightnessValue(11)),
            deadBand.suppressionFor(BrightnessValue(11)),
        )
    }

    @Test
    fun `AC1_2 no prior applied value accepts the first request`() {
        val deadBand = MinimumStepDeadBand()

        assertNull(deadBand.lastApplied)
        assertNull(deadBand.suppressionFor(BrightnessValue(10)))
    }

    @Test
    fun `AC1_2 suppressed request does not advance the remembered applied value`() {
        val deadBand = MinimumStepDeadBand(minimumStep = 3)
        deadBand.recordApplied(BrightnessValue(10))

        assertEquals(
            BrightnessWriteResult.Suppressed(BrightnessValue(12)),
            deadBand.suppressionFor(BrightnessValue(12)),
        )
        assertEquals(BrightnessValue(10), deadBand.lastApplied)
        assertNull(deadBand.suppressionFor(BrightnessValue(13)))
    }

    @Test
    fun `reset forgets the remembered value for a new controller run`() {
        val deadBand = MinimumStepDeadBand()
        deadBand.recordApplied(BrightnessValue(10))

        deadBand.reset()

        assertNull(deadBand.lastApplied)
        assertNull(deadBand.suppressionFor(BrightnessValue(10)))
    }
}
