package com.ambientbrightness.core.BrightnessOffsetTransform

import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessContext
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessRange
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessRangeScaler.BrightnessRangeScaler
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffset
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffsetApplication
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffsetRange
import com.ambientbrightness.core.BrightnessOffsetService.BrightnessOffsetSource
import com.ambientbrightness.core.BrightnessOffsetService.OffsetApplicationSink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BrightnessOffsetTransformTest {

    private val range = BrightnessRange(min = BrightnessValue(10), max = BrightnessValue(110))
    private val offsetSource = FakeOffsetSource(BrightnessOffset.DEFAULT)
    private val sink = RecordingApplicationSink()
    private val transform = BrightnessOffsetTransform(offsetSource, sink)

    @Test
    fun `AC2_4 - a zero offset returns the fraction unchanged and reports fully applied`() {
        val result = transform.apply(0.5f, contextOf(range))

        assertEquals(0.5f, result)
        assertEquals(listOf(BrightnessOffsetApplication.FullyApplied), sink.published)
    }

    @Test
    fun `AC3_1 - a fifty percent offset shifts the fraction by half the span in either direction`() {
        offsetSource.emit(BrightnessOffset.of(BrightnessOffsetRange.MAX))
        val shiftedUp = transform.apply(0.25f, contextOf(range))

        offsetSource.emit(BrightnessOffset.of(BrightnessOffsetRange.MIN))
        val shiftedDown = transform.apply(0.75f, contextOf(range))

        assertEquals(0.75f, shiftedUp, EPSILON)
        assertEquals(0.25f, shiftedDown, EPSILON)
        assertEquals(HALF_SPAN, shiftedUp - 0.25f, EPSILON)
        assertEquals(-HALF_SPAN, shiftedDown - 0.75f, EPSILON)
        assertEquals(
            listOf(BrightnessOffsetApplication.FullyApplied, BrightnessOffsetApplication.FullyApplied),
            sink.published,
        )
    }

    @Test
    fun `AC2_1 AC2_3 - a most-negative offset at a low fraction clamps to the range minimum`() {
        offsetSource.emit(BrightnessOffset.of(BrightnessOffsetRange.MIN))

        val result = transform.apply(0.1f, contextOf(range))

        assertEquals(-0.4f, result, EPSILON)
        assertEquals(listOf(BrightnessOffsetApplication.ClampedToMinimum), sink.published)
        assertEquals(range.min, BrightnessRangeScaler.scale(result, range))
    }

    @Test
    fun `AC2_1 - the clamped result is the range floor, never zero`() {
        val floorRange = BrightnessRange(
            min = BrightnessValue(BrightnessValue.MINIMUM),
            max = BrightnessValue(101),
        )
        offsetSource.emit(BrightnessOffset.of(BrightnessOffsetRange.MIN))

        val result = transform.apply(0f, contextOf(floorRange))

        assertEquals(
            BrightnessValue(BrightnessValue.MINIMUM),
            BrightnessRangeScaler.scale(result, floorRange),
        )
        assertEquals(listOf(BrightnessOffsetApplication.ClampedToMinimum), sink.published)
    }

    @Test
    fun `AC2_2 AC2_3 - a most-positive offset at a high fraction clamps to the range maximum`() {
        offsetSource.emit(BrightnessOffset.of(BrightnessOffsetRange.MAX))

        val result = transform.apply(0.9f, contextOf(range))

        assertEquals(1.4f, result, EPSILON)
        assertEquals(listOf(BrightnessOffsetApplication.ClampedToMaximum), sink.published)
        assertEquals(range.max, BrightnessRangeScaler.scale(result, range))
    }

    @Test
    fun `each call publishes the clamp state of the latest sample`() {
        offsetSource.emit(BrightnessOffset.of(BrightnessOffsetRange.MIN))
        transform.apply(0.1f, contextOf(range))

        offsetSource.emit(BrightnessOffset.DEFAULT)
        transform.apply(0.1f, contextOf(range))

        assertEquals(
            listOf(BrightnessOffsetApplication.ClampedToMinimum, BrightnessOffsetApplication.FullyApplied),
            sink.published,
        )
    }

    private fun contextOf(range: BrightnessRange) = BrightnessContext(lux = 0f, range = range)

    private companion object {
        const val HALF_SPAN = 0.5f
        const val EPSILON = 0.0001f
    }
}

private class FakeOffsetSource(initial: BrightnessOffset) : BrightnessOffsetSource {

    private val mutableOffset = MutableStateFlow(initial)

    override val offset: StateFlow<BrightnessOffset> = mutableOffset

    override val application: StateFlow<BrightnessOffsetApplication> =
        MutableStateFlow(BrightnessOffsetApplication.NotEvaluated)

    fun emit(offset: BrightnessOffset) {
        mutableOffset.value = offset
    }
}

private class RecordingApplicationSink : OffsetApplicationSink {

    val published: MutableList<BrightnessOffsetApplication> = mutableListOf()

    override fun publish(application: BrightnessOffsetApplication) {
        published += application
    }
}
