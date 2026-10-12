package com.ambientbrightness.infrastructure.AutomaticBrightnessControl.BuiltInAdaptiveBrightnessClient.Android

import android.content.Context
import android.provider.Settings
import com.ambientbrightness.core.AutomaticBrightnessControl.BuiltInAdaptiveBrightnessClient
import com.ambientbrightness.core.AutomaticBrightnessControl.BuiltInAdaptiveBrightnessState

/**
 * Reads only [Settings.System.SCREEN_BRIGHTNESS_MODE] (D4) through the application resolver.
 *
 * Any platform failure — the setting is absent, unreadable or carries an unexpected value — is
 * reported as [BuiltInAdaptiveBrightnessState.UNKNOWN] instead of throwing.
 */
class AndroidBuiltInAdaptiveBrightnessClient(context: Context) : BuiltInAdaptiveBrightnessClient {

    private val contentResolver = context.applicationContext.contentResolver

    override fun readState(): BuiltInAdaptiveBrightnessState {
        val mode = try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
        } catch (_: Settings.SettingNotFoundException) {
            return BuiltInAdaptiveBrightnessState.UNKNOWN
        } catch (_: SecurityException) {
            return BuiltInAdaptiveBrightnessState.UNKNOWN
        } catch (_: IllegalArgumentException) {
            return BuiltInAdaptiveBrightnessState.UNKNOWN
        }

        return when (mode) {
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC -> BuiltInAdaptiveBrightnessState.ENABLED
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL -> BuiltInAdaptiveBrightnessState.DISABLED
            else -> BuiltInAdaptiveBrightnessState.UNKNOWN
        }
    }
}
