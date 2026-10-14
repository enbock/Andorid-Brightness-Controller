package com.ambientbrightness.core.BrightnessOffsetStateHolder

import com.ambientbrightness.core.BrightnessOffset.BrightnessOffset
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffsetApplication
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffsetRange
import com.ambientbrightness.core.FakeSettingsStore
import com.ambientbrightness.core.SettingsStore.SettingsKeys
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrightnessOffsetStateHolderTest {

    @Test
    fun `AC4_3 - an empty store reports the neutral default without writing`() = runTest {
        val store = FakeSettingsStore()
        val holder = BrightnessOffsetStateHolder(store, backgroundScope)

        runCurrent()

        assertEquals(BrightnessOffset.DEFAULT, holder.offset.value)
        assertEquals(0, holder.offset.value.percent)
        assertEquals(emptyList<Pair<String, Any?>>(), store.writes)
        assertEquals(0, store.observe(SettingsKeys.BRIGHTNESS_OFFSET_PERCENT).first())
    }

    @Test
    fun `AC4_1_AC4_2 - an offset persisted before construction is restored`() = runTest {
        val holder = BrightnessOffsetStateHolder(storeStoring(25), backgroundScope)

        runCurrent()

        assertEquals(BrightnessOffset.of(25), holder.offset.value)
    }

    @Test
    fun `AC4_1_AC4_2 - the most negative stored offset is restored`() = runTest {
        val holder = BrightnessOffsetStateHolder(storeStoring(BrightnessOffsetRange.MIN), backgroundScope)

        runCurrent()

        assertEquals(BrightnessOffset.of(BrightnessOffsetRange.MIN), holder.offset.value)
    }

    @Test
    fun `AC3_3 - setting an offset persists it and emits it to a running collector`() = runTest {
        val store = FakeSettingsStore()
        val holder = BrightnessOffsetStateHolder(store, backgroundScope)
        runCurrent()

        holder.setOffset(BrightnessOffset.of(30))
        runCurrent()

        assertEquals(BrightnessOffset.of(30), holder.offset.value)
        assertEquals(
            listOf(SettingsKeys.BRIGHTNESS_OFFSET_PERCENT.name to 30),
            store.writes,
        )
    }

    @Test
    fun `AC3_3 - reset returns the offset to the neutral default`() = runTest {
        val store = FakeSettingsStore()
        val holder = BrightnessOffsetStateHolder(store, backgroundScope)
        runCurrent()
        holder.setOffset(BrightnessOffset.of(-40))
        runCurrent()

        holder.reset()
        runCurrent()

        assertEquals(BrightnessOffset.DEFAULT, holder.offset.value)
        assertEquals(0, store.observe(SettingsKeys.BRIGHTNESS_OFFSET_PERCENT).first())
    }

    @Test
    fun `AC3_3 - adjustBy saturates at the maximum instead of throwing`() = runTest {
        val store = FakeSettingsStore()
        val holder = BrightnessOffsetStateHolder(store, backgroundScope)
        runCurrent()
        holder.setOffset(BrightnessOffset.of(30))
        runCurrent()

        holder.adjustBy(70)
        runCurrent()

        assertEquals(BrightnessOffset.of(BrightnessOffsetRange.MAX), holder.offset.value)
        assertEquals(
            BrightnessOffsetRange.MAX,
            store.observe(SettingsKeys.BRIGHTNESS_OFFSET_PERCENT).first(),
        )
    }

    @Test
    fun `AC3_3 - adjustBy saturates at the minimum instead of throwing`() = runTest {
        val store = FakeSettingsStore()
        val holder = BrightnessOffsetStateHolder(store, backgroundScope)
        runCurrent()

        holder.adjustBy(-100)
        runCurrent()

        assertEquals(BrightnessOffset.of(BrightnessOffsetRange.MIN), holder.offset.value)
        assertEquals(
            BrightnessOffsetRange.MIN,
            store.observe(SettingsKeys.BRIGHTNESS_OFFSET_PERCENT).first(),
        )
    }

    @Test
    fun `an out-of-range positive seed is clamped to the maximum`() = runTest {
        val holder = BrightnessOffsetStateHolder(storeStoring(999), backgroundScope)

        runCurrent()

        assertEquals(BrightnessOffset.of(BrightnessOffsetRange.MAX), holder.offset.value)
    }

    @Test
    fun `an out-of-range negative seed is clamped to the minimum`() = runTest {
        val holder = BrightnessOffsetStateHolder(storeStoring(-999), backgroundScope)

        runCurrent()

        assertEquals(BrightnessOffset.of(BrightnessOffsetRange.MIN), holder.offset.value)
    }

    @Test
    fun `AC1_2 - a change with no ambient reading still updates the offset`() = runTest {
        val store = FakeSettingsStore()
        val holder = BrightnessOffsetStateHolder(store, backgroundScope)
        runCurrent()

        holder.setOffset(BrightnessOffset.of(15))
        runCurrent()

        assertEquals(BrightnessOffset.of(15), holder.offset.value)
        assertEquals(BrightnessOffsetApplication.NotEvaluated, holder.application.value)
    }

    @Test
    fun `the application state starts NotEvaluated and surfaces what is published`() = runTest {
        val holder = BrightnessOffsetStateHolder(FakeSettingsStore(), backgroundScope)

        runCurrent()

        assertEquals(BrightnessOffsetApplication.NotEvaluated, holder.application.value)

        holder.publish(BrightnessOffsetApplication.ClampedToMaximum)

        assertEquals(BrightnessOffsetApplication.ClampedToMaximum, holder.application.value)
        assertEquals(
            BrightnessOffsetApplication.ClampedToMaximum,
            holder.application.first(),
        )
    }

    private fun storeStoring(percent: Int): FakeSettingsStore =
        FakeSettingsStore(mapOf(SettingsKeys.BRIGHTNESS_OFFSET_PERCENT.name to percent))
}
