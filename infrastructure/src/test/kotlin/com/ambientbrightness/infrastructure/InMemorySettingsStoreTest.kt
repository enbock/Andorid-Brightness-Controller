package com.ambientbrightness.infrastructure

import com.ambientbrightness.core.SettingsStore.SettingsKey
import com.ambientbrightness.core.SettingsStore.SettingsKeys
import com.ambientbrightness.infrastructure.SettingsStore.SettingsStore.InMemory.InMemorySettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemorySettingsStoreTest {

    @Test
    fun `a set value is read back through observe`() = runTest {
        val store = InMemorySettingsStore()
        val key = SettingsKeys.RESPONSE_SPEED_PERCENT

        assertEquals(50, store.observe(key).first())

        store.set(key, 73)

        assertEquals(73, store.observe(key).first())
    }

    @Test
    fun `an absent key falls back to its own default for every stored type`() = runTest {
        val store = InMemorySettingsStore()

        assertEquals(50, store.observe(SettingsKeys.RESPONSE_SPEED_PERCENT).first())
        assertEquals(0L, store.observe(SettingsKey.LongKey("count", 0L)).first())
        assertEquals(1.5f, store.observe(SettingsKey.FloatKey("ratio", 1.5f)).first())
        assertTrue(store.observe(SettingsKey.BooleanKey("enabled", true)).first())
        assertEquals("none", store.observe(SettingsKey.StringKey("name", "none")).first())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `an active collector sees the default and every later set of that key`() = runTest {
        val store = InMemorySettingsStore()
        val key = SettingsKey.IntKey("step", 0)
        val emissions = mutableListOf<Int>()
        val collection = launch { store.observe(key).take(3).toList(emissions) }

        runCurrent()
        store.set(key, 1)
        runCurrent()
        store.set(key, 2)
        collection.join()

        assertEquals(listOf(0, 1, 2), emissions)
    }

    @Test
    fun `two keys of the same declared type stay independent of each other`() = runTest {
        val store = InMemorySettingsStore()
        val responseSpeed = SettingsKeys.RESPONSE_SPEED_PERCENT
        val brightnessOffset = SettingsKey.IntKey("brightness_offset_percent", 0)

        store.set(responseSpeed, 20)
        store.set(brightnessOffset, -25)

        assertEquals(20, store.observe(responseSpeed).first())
        assertEquals(-25, store.observe(brightnessOffset).first())
    }
}
