package com.ambientbrightness.core.ResponseTuning.ResponseTuningService

import com.ambientbrightness.core.ResponseTuning.ResponseSpeed
import com.ambientbrightness.core.SettingsStore.SettingsKeys
import com.ambientbrightness.core.SettingsStore.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Mirrors the persisted response speed into a [StateFlow] for the UI.
 *
 * The store is the single source of truth: construction seeds [responseSpeed] from the stored value
 * (or the key default while it is absent), and [setResponseSpeed] only writes to the store, whose
 * emission updates [responseSpeed] in turn. The collection runs in the injected [scope], so the
 * composition root stays the only owner of coroutine scopes.
 */
class DefaultResponseTuningService(
    private val store: SettingsStore,
    scope: CoroutineScope,
) : ResponseTuningService {

    override val responseSpeed: StateFlow<ResponseSpeed> =
        store.observe(SettingsKeys.RESPONSE_SPEED_PERCENT)
            .map { storedPercent -> ResponseSpeed.of(storedPercent) }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = ResponseSpeed.of(ResponseSpeed.DEFAULT),
            )

    override suspend fun setResponseSpeed(speed: ResponseSpeed) {
        store.set(SettingsKeys.RESPONSE_SPEED_PERCENT, speed.percent)
    }
}
