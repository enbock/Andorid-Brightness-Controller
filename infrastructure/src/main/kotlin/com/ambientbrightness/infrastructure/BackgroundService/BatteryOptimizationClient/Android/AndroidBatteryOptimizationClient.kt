package com.ambientbrightness.infrastructure.BackgroundService.BatteryOptimizationClient.Android

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import com.ambientbrightness.core.BackgroundService.BatteryOptimizationClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Battery-optimization exemption backed by [PowerManager.isIgnoringBatteryOptimizations].
 *
 * Android offers no change callback for the exemption, so the state is a [MutableStateFlow] that is
 * re-read from the platform in [refresh]: on construction, before every [requestExemption] or
 * [openSettings] launch, and whenever the composition root asks after the user returns from the
 * system screen. Both entry points open the advisory
 * [Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS] list; the app-specific
 * [Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS] dialog is deliberately not used because it
 * requires the `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission (Play policy implications).
 */
class AndroidBatteryOptimizationClient(
    private val exemptionReader: () -> Boolean,
    private val settingsScreenOpener: (String) -> Unit,
) : BatteryOptimizationClient {

    private val exemption = MutableStateFlow(exemptionReader())

    override val isExempt: StateFlow<Boolean> = exemption.asStateFlow()

    override fun requestExemption() = openExemptionScreen()

    override fun openSettings() = openExemptionScreen()

    fun refresh() {
        exemption.value = exemptionReader()
    }

    private fun openExemptionScreen() {
        refresh()
        settingsScreenOpener(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }

    companion object {
        fun create(context: Context): AndroidBatteryOptimizationClient {
            val applicationContext = context.applicationContext
            return AndroidBatteryOptimizationClient(
                exemptionReader = { isIgnoringBatteryOptimizations(applicationContext) },
                settingsScreenOpener = { action ->
                    applicationContext.startActivity(
                        Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
        }
    }
}

internal fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true
