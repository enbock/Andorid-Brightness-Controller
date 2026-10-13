package com.ambientbrightness.core.UserConfigurableStabiliser

import com.ambientbrightness.core.AmbientLightSensing.LuxSample
import com.ambientbrightness.core.AmbientLightSensing.ReadingStabiliser.OutlierClampingStabiliser
import com.ambientbrightness.core.FakeClock
import com.ambientbrightness.core.FakeReadingStabiliser
import com.ambientbrightness.core.ResponseTuning.ResponseSpeed
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UserConfigurableStabiliserTest {

    private val clock = FakeClock()
    private val responseSpeed = MutableStateFlow(ResponseSpeed.of(50))

    /** Feeds [lux] after advancing the clock by [afterMillis] and returns the emitted lux value. */
    private fun stabiliser(
        delegate: FakeReadingStabiliser = FakeReadingStabiliser(),
    ): UserConfigurableStabiliser = UserConfigurableStabiliser(
        delegate = delegate,
        responseSpeed = responseSpeed,
        clock = clock,
    )

    private fun UserConfigurableStabiliser.feed(
        lux: Float,
        afterMillis: Long = SAMPLING_INTERVAL_MILLIS,
    ): Float? {
        clock.advanceBy(afterMillis)
        return stabilise(LuxSample(lux = lux, timestampMillis = clock.nowMillis()))?.lux
    }

    @Test
    fun `AC1_1 - the fast setting reaches the new target in one step, the slow one only gradually`() {
        responseSpeed.value = ResponseSpeed.of(0)
        val fast = stabiliser()
        assertEquals(100f, fast.feed(100f, afterMillis = 0L))
        assertEquals(100f, fast.feed(300f))

        assertEquals(300f, fast.feed(300f))

        responseSpeed.value = ResponseSpeed.of(100)
        val slow = stabiliser()
        assertEquals(100f, slow.feed(100f, afterMillis = 0L))
        assertEquals(100f, slow.feed(300f))
        assertEquals(100f, slow.feed(300f))

        val afterWindow = slow.feed(300f)

        assertTrue(afterWindow!! > 100f, "the slow setting must have started moving: $afterWindow")
        assertTrue(afterWindow < 300f, "the slow setting must not jump to the target: $afterWindow")
    }

    @Test
    fun `AC3_1 - a change shorter than the debounce window is not applied at all`() {
        val subject = stabiliser()

        val emissions = listOf(
            subject.feed(100f, afterMillis = 0L),
            subject.feed(300f),
            subject.feed(101f),
            subject.feed(100f),
        )

        assertEquals(listOf(100f, 100f, 100f, 100f), emissions)
    }

    @Test
    fun `AC3_2 - a sustained change starts the adjustment when the debounce window elapses`() {
        val subject = stabiliser()
        assertEquals(100f, subject.feed(100f, afterMillis = 0L))
        assertEquals(100f, subject.feed(300f))

        val afterWindow = subject.feed(300f)

        // 500 ms elapsed of the 1650 ms reaction window: a fifth of the distance, so it moves slowly.
        assertEquals(160.6f, afterWindow!!, 0.5f)
    }

    @Test
    fun `AC3_2 - a sustained change with sensor jitter is still applied`() {
        val subject = stabiliser()
        assertEquals(100f, subject.feed(100f, afterMillis = 0L))
        assertEquals(100f, subject.feed(100f))

        val armed = subject.feed(20_010f)
        val afterWindow = subject.feed(20_000f)

        assertEquals(100f, armed!!, "the first sighting must be held")
        // 10 lx of jitter on a 20 000 lx reading is far below the band of the old 100 lx level, so the
        // window must keep running instead of being restarted by the jitter.
        assertTrue(afterWindow!! > 100f, "the sustained change must start: $afterWindow")
    }

    @Test
    fun `AC3_2 - a monotone moving change is applied once its window elapsed`() {
        val subject = stabiliser()
        assertEquals(100f, subject.feed(100f, afterMillis = 0L))
        assertEquals(100f, subject.feed(100f))

        val firstSighting = subject.feed(120f)!!

        assertEquals(100f, firstSighting, "the window must start on the first sighting")

        val moving = (1..4).map { subject.feed(120f + it * 20f)!! }

        assertEquals(moving.sorted(), moving, "the value must keep following the moving light: $moving")
        assertTrue(moving.last() > 100f, "the light must be followed, not frozen: $moving")
    }

    @Test
    fun `a sustained change shorter than the window is still dropped when the level returns`() {
        val subject = stabiliser()
        subject.feed(100f, afterMillis = 0L)
        subject.feed(20_000f)

        assertEquals(100f, subject.feed(100f))
    }

    @Test
    fun `AC3_3 - small alternating readings around a stable average are no change`() {
        val subject = stabiliser()

        val emissions = listOf(100f, 102f, 98f, 101f, 99f, 103f, 97f)
            .map { lux -> subject.feed(lux) }

        assertEquals(List(emissions.size) { 100f }, emissions)
    }

    @Test
    fun `AC1_3 - a steady level settles on its target and does not oscillate`() {
        responseSpeed.value = ResponseSpeed.of(0)
        val subject = stabiliser()
        subject.feed(100f, afterMillis = 0L)
        subject.feed(300f)
        subject.feed(300f)

        val settled = listOf(subject.feed(300f), subject.feed(300f), subject.feed(300f))

        assertEquals(listOf(300f, 300f, 300f), settled)
    }

    @Test
    fun `AC1_1 - a sustained change approaches its target monotonically`() {
        val subject = stabiliser()
        subject.feed(100f, afterMillis = 0L)
        subject.feed(300f)

        val emissions = (1..20).map { subject.feed(300f)!! }

        assertEquals(emissions.sorted(), emissions, "the approach must not backtrack: $emissions")
        assertEquals(300f, emissions.last())
    }

    @Test
    fun `AC1_4 - a changed response speed applies on the next call without re-injection`() {
        responseSpeed.value = ResponseSpeed.of(100)
        val subject = stabiliser()
        assertEquals(100f, subject.feed(100f, afterMillis = 0L))
        assertEquals(100f, subject.feed(300f))
        assertEquals(100f, subject.feed(300f))

        responseSpeed.value = ResponseSpeed.of(0)

        assertEquals(300f, subject.feed(300f))
    }

    @Test
    fun `F1 outlier rejection is delegated and stays in effect`() {
        val subject = UserConfigurableStabiliser(
            delegate = OutlierClampingStabiliser(maxRangeLux = 10_000f),
            responseSpeed = responseSpeed,
            clock = clock,
        )
        assertEquals(100f, subject.feed(100f, afterMillis = 0L))

        assertNull(subject.feed(9_000f))
        assertNull(subject.feed(9_000f))

        assertEquals(100f, subject.feed(100f))
    }

    @Test
    fun `a reading rejected by the delegate stays rejected`() {
        val subject = stabiliser(FakeReadingStabiliser(resultProvider = { null }))

        assertNull(subject.feed(500f, afterMillis = 0L))
        assertNull(subject.feed(500f))
    }

    @Test
    fun `a non-finite reading is never emitted`() {
        val subject = stabiliser()
        subject.feed(100f, afterMillis = 0L)

        assertNull(subject.feed(Float.NaN))
    }

    private companion object {
        const val SAMPLING_INTERVAL_MILLIS = 500L
    }
}
