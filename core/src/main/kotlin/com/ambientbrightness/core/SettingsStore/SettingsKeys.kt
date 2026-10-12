package com.ambientbrightness.core.SettingsStore

/**
 * The single registry of persisted keys. Every feature contributes its keys here so the stored
 * names stay in one place: F4 owns the response speed, F3 adds `BRIGHTNESS_OFFSET_PERCENT` with the
 * stored name `brightness_offset_percent` and the default `0`.
 */
object SettingsKeys {

    val RESPONSE_SPEED_PERCENT: SettingsKey<Int> =
        SettingsKey.IntKey("response_speed_percent", 50)
}
