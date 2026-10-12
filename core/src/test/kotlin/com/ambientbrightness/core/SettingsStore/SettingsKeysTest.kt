package com.ambientbrightness.core.SettingsStore

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
}
