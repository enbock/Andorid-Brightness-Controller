package com.ambientbrightness.infrastructure.AutomaticBrightnessControl.DeviceBrightnessRangeClient.Android

import android.content.res.Resources
import android.util.Log
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessRange
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.AutomaticBrightnessControl.DeviceBrightnessRangeClient

/**
 * Discovers the device maximum using Android's internal system resource when it is available.
 *
 * The resource is intentionally looked up by name because it is not part of the public SDK API.
 * Callers should use the core resolver's fallback when [readRange] returns null.
 */
class AndroidDeviceBrightnessRangeClient(
    private val resources: Resources = Resources.getSystem(),
) : DeviceBrightnessRangeClient {

    override fun readRange(): BrightnessRange? {
        val resourceId = resources.getIdentifier(
            MAXIMUM_BRIGHTNESS_RESOURCE,
            RESOURCE_TYPE_INTEGER,
            ANDROID_PACKAGE,
        )
        if (resourceId == NO_RESOURCE) {
            Log.w(TAG, "System brightness maximum resource is unavailable; using the core fallback.")
            return null
        }

        val maximum = try {
            resources.getInteger(resourceId)
        } catch (_: Resources.NotFoundException) {
            Log.w(TAG, "System brightness maximum resource could not be read; using the core fallback.")
            return null
        } catch (_: IllegalArgumentException) {
            Log.w(TAG, "System brightness maximum resource is invalid; using the core fallback.")
            return null
        }
        return brightnessRangeFromMaximum(maximum).also {
            if (it == null) {
                Log.w(TAG, "System brightness maximum is below the minimum; using the core fallback.")
            }
        }
    }

    private companion object {
        const val TAG = "BrightnessRangeClient"
        const val MAXIMUM_BRIGHTNESS_RESOURCE = "config_screenBrightnessSettingMaximum"
        const val RESOURCE_TYPE_INTEGER = "integer"
        const val ANDROID_PACKAGE = "android"
        const val NO_RESOURCE = 0
    }
}

internal fun brightnessRangeFromMaximum(maximum: Int?): BrightnessRange? {
    if (maximum == null || maximum < BrightnessValue.MINIMUM) return null

    val minimum = BrightnessValue(BrightnessValue.MINIMUM)
    val maximumValue = BrightnessValue(maximum)
    if (maximumValue.value < minimum.value) return null
    return BrightnessRange(min = minimum, max = maximumValue)
}
