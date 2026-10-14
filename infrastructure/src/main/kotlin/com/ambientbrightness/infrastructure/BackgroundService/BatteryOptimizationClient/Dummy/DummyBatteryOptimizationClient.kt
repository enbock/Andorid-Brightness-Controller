package com.ambientbrightness.infrastructure.BackgroundService.BatteryOptimizationClient.Dummy

import com.ambientbrightness.core.BackgroundService.BatteryOptimizationClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory [BatteryOptimizationClient] for Dummy composition and tests; it never accesses Android APIs. */
class DummyBatteryOptimizationClient(
    initiallyExempt: Boolean = false,
) : BatteryOptimizationClient {

    private val exemption = MutableStateFlow(initiallyExempt)

    override val isExempt: Flow<Boolean> = exemption.asStateFlow()

    var exemptionRequests: Int = 0
        private set

    var settingsOpened: Int = 0
        private set

    override fun requestExemption() {
        exemptionRequests++
    }

    override fun openSettings() {
        settingsOpened++
    }

    fun setExempt(exempt: Boolean) {
        exemption.value = exempt
    }
}
