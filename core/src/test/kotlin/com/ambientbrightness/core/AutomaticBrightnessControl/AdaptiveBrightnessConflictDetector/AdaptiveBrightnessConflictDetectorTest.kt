package com.ambientbrightness.core.AutomaticBrightnessControl.AdaptiveBrightnessConflictDetector

import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessControllerUseCase.Task.AdaptiveBrightnessConflictDetector
import com.ambientbrightness.core.AutomaticBrightnessControl.BuiltInAdaptiveBrightnessClient
import com.ambientbrightness.core.AutomaticBrightnessControl.BuiltInAdaptiveBrightnessState
import com.ambientbrightness.core.testdoubles.FakeBuiltInAdaptiveBrightnessClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AdaptiveBrightnessConflictDetectorTest {

    @Test
    fun `reports the state the platform returns`() {
        BuiltInAdaptiveBrightnessState.entries.forEach { state ->
            val detector = AdaptiveBrightnessConflictDetector(
                FakeBuiltInAdaptiveBrightnessClient(state),
            )

            assertEquals(state, detector.detect())
        }
    }

    @Test
    fun `an unreadable platform mode is reported as unknown instead of failing`() {
        val throwingClient = BuiltInAdaptiveBrightnessClient {
            throw IllegalStateException("Setting is not readable.")
        }
        val detector = AdaptiveBrightnessConflictDetector(throwingClient)

        assertEquals(BuiltInAdaptiveBrightnessState.UNKNOWN, detector.detect())
    }

    @Test
    fun `a platform that reports no mode at all is unknown`() {
        val detector = AdaptiveBrightnessConflictDetector(FakeBuiltInAdaptiveBrightnessClient())

        assertEquals(BuiltInAdaptiveBrightnessState.UNKNOWN, detector.detect())
    }
}
