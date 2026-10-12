package com.ambientbrightness.infrastructure.AutomaticBrightnessControl.SystemBrightnessClient.Android

import android.content.Context
import android.provider.Settings
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessWriteResult
import com.ambientbrightness.core.AutomaticBrightnessControl.SystemBrightnessClient

/**
 * Reads and writes only [Settings.System.SCREEN_BRIGHTNESS] through the application resolver.
 */
class AndroidSystemBrightnessClient(context: Context) : SystemBrightnessClient {

    private val applicationContext = context.applicationContext
    private val contentResolver = applicationContext.contentResolver

    override fun readCurrent(): Int? = readBrightnessSetting {
        Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    }

    override fun write(value: BrightnessValue): BrightnessWriteResult =
        writeBrightnessSetting(
            hasPermission = { Settings.System.canWrite(applicationContext) },
            putValue = {
                Settings.System.putInt(
                    contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    value.value,
                )
            },
            value = value,
        )
}

internal fun readBrightnessSetting(readValue: () -> Int): Int? =
    try {
        readValue()
    } catch (_: Settings.SettingNotFoundException) {
        null
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

internal fun writeBrightnessSetting(
    hasPermission: () -> Boolean,
    putValue: () -> Boolean,
    value: BrightnessValue,
): BrightnessWriteResult =
    try {
        if (!hasPermission()) {
            BrightnessWriteResult.Failed("WRITE_SETTINGS permission is not granted.")
        } else if (!putValue()) {
            BrightnessWriteResult.Failed("Android rejected the SCREEN_BRIGHTNESS write.")
        } else {
            BrightnessWriteResult.Applied(value)
        }
    } catch (exception: SecurityException) {
        BrightnessWriteResult.Failed(
            "Permission denied writing SCREEN_BRIGHTNESS: ${exception.message ?: "SecurityException"}",
        )
    } catch (exception: IllegalArgumentException) {
        BrightnessWriteResult.Failed(
            "Invalid SCREEN_BRIGHTNESS write: ${exception.message ?: "IllegalArgumentException"}",
        )
    }
