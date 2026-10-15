package com.ambientbrightness.app

import com.ambientbrightness.app.StatusDashboard.View.Task.LuxReadoutSampler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The readout must be capped to a display-friendly rate regardless of the sensor rate (US1, AC1.1). */
@OptIn(ExperimentalCoroutinesApi::class)
class LuxReadoutSamplerTest {

    private val sampler = LuxReadoutSampler()

    @Test
    fun `a fast sensor produces at most one readout per display interval`() = runTest {
        val emissions = mutableListOf<Float?>()
        val job = launch {
            sampler.sample(
                flow {
                    var index = 0
                    while (true) {
                        emit(index.toFloat())
                        index++
                        delay(50L)
                    }
                },
            ).collect { emissions += it }
        }

        advanceTimeBy(WINDOW_MILLIS)
        runCurrent()
        job.cancel()

        val maximum = (WINDOW_MILLIS / LuxReadoutSampler.DISPLAY_SAMPLE_INTERVAL_MS).toInt() + 1
        assertTrue(
            "expected at most $maximum emissions in ${WINDOW_MILLIS}ms but got $emissions",
            emissions.size <= maximum,
        )
        assertTrue("the sampler must not drop every reading: $emissions", emissions.isNotEmpty())
    }

    @Test
    fun `an unchanged reading is emitted only once`() = runTest {
        val emissions = mutableListOf<Float?>()
        val job = launch {
            sampler.sample(
                flow {
                    while (true) {
                        emit(SAME_LUX)
                        delay(50L)
                    }
                },
            ).collect { emissions += it }
        }

        advanceTimeBy(2_000L)
        runCurrent()
        job.cancel()

        assertEquals(
            "an unchanged value must not keep repainting the readout",
            listOf(SAME_LUX),
            emissions,
        )
    }

    @Test
    fun `the display interval matches the two-per-second budget`() {
        assertEquals(500L, LuxReadoutSampler.DISPLAY_SAMPLE_INTERVAL_MS)
    }

    private companion object {
        const val WINDOW_MILLIS = 2_000L
        const val SAME_LUX = 42f
    }
}
