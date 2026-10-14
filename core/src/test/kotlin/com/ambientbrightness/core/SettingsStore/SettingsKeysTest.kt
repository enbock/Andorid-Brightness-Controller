package com.ambientbrightness.core.SettingsStore

import com.ambientbrightness.core.FakeSettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SettingsKeysTest {

    @Test
    fun `the response speed key freezes the stored name and the middle-of-the-range default`() {
        assertEquals("response_speed_percent", SettingsKeys.RESPONSE_SPEED_PERCENT.name)
        assertEquals(50, SettingsKeys.RESPONSE_SPEED_PERCENT.default)
    }

    @Test
    fun `a stored value survives the round-trip through the key type tag`() {
        val key = SettingsKeys.RESPONSE_SPEED_PERCENT

        assertEquals(50, key.coerce(null))
        assertEquals(50, key.coerce("not a percent"))
        assertEquals(80, key.coerce(80))
    }

    @Test
    fun `the brightness offset key freezes the stored name and the neutral default`() {
        assertEquals("brightness_offset_percent", SettingsKeys.BRIGHTNESS_OFFSET_PERCENT.name)
        assertEquals(0, SettingsKeys.BRIGHTNESS_OFFSET_PERCENT.default)
    }

    @Test
    fun `the brightness offset key coerces the absent case and a foreign value to its default`() {
        val key = SettingsKeys.BRIGHTNESS_OFFSET_PERCENT

        assertEquals(0, key.coerce(null))
        assertEquals(0, key.coerce("not a percent"))
        assertEquals(0, key.coerce(12.5f))
    }

    @Test
    fun `the brightness offset key round-trips a stored int`() {
        val key = SettingsKeys.BRIGHTNESS_OFFSET_PERCENT

        assertEquals(25, key.coerce(25))
        assertEquals(-50, key.coerce(-50))
        assertEquals(50, key.coerce(50))
    }

    @Test
    fun `the shared store emits the offset default before a write and the written value after`() =
        runTest {
            val store = FakeSettingsStore()
            val key = SettingsKeys.BRIGHTNESS_OFFSET_PERCENT

            assertEquals(0, store.observe(key).first())

            store.set(key, 25)

            assertEquals(25, store.observe(key).first())
            assertEquals(1, store.writes.size)
            assertEquals("brightness_offset_percent", store.writes.single().first)
            assertEquals(25, store.writes.single().second)
        }
}
