package com.ambientbrightness.core.BrightnessOffsetStateHolder

import com.ambientbrightness.core.BrightnessOffset.BrightnessOffset
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffsetApplication
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffsetRange
import com.ambientbrightness.core.BrightnessOffsetService.BrightnessOffsetService
import com.ambientbrightness.core.BrightnessOffsetService.OffsetApplicationSink
import com.ambientbrightness.core.SettingsStore.SettingsKeys
import com.ambientbrightness.core.SettingsStore.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The app-scoped, single source of truth for the manual [offset], backed by the shared
 * [SettingsStore] (F4).
 *
 * Construction seeds [offset] from the stored value, so an offset set before a restart is restored
 * immediately (AC4.1, AC4.2) and an absent key reports [BrightnessOffset.DEFAULT] (`0`) without a
 * write (AC4.3). The store stays the source of truth: [setOffset]/[adjustBy]/[reset] only persist
 * the new percent, and the resulting store emission updates [offset] — a change lands even when no
 * ambient reading exists (AC1.2), because the holder never depends on readings. The collection runs
 * in the injected [scope], so the composition root stays the only owner of coroutine scopes.
 */
class BrightnessOffsetStateHolder(
    private val store: SettingsStore,
    scope: CoroutineScope,
) : BrightnessOffsetService, OffsetApplicationSink {

    override val offset: StateFlow<BrightnessOffset> =
        store.observe(SettingsKeys.BRIGHTNESS_OFFSET_PERCENT)
            .map { storedPercent ->
                // Defence in depth: the shared store performs no domain validation, so an
                // out-of-range stored Int is clamped before it becomes a BrightnessOffset.
                BrightnessOffset.of(
                    storedPercent.coerceIn(BrightnessOffsetRange.MIN, BrightnessOffsetRange.MAX),
                )
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = BrightnessOffset.DEFAULT,
            )

    private val mutableApplication: MutableStateFlow<BrightnessOffsetApplication> =
        MutableStateFlow(BrightnessOffsetApplication.NotEvaluated)

    override val application: StateFlow<BrightnessOffsetApplication> = mutableApplication.asStateFlow()

    override suspend fun setOffset(offset: BrightnessOffset) {
        store.set(SettingsKeys.BRIGHTNESS_OFFSET_PERCENT, offset.percent)
    }

    override suspend fun adjustBy(deltaPercent: Int) {
        val target = offset.value.percent.toLong() + deltaPercent.toLong()
        store.set(
            SettingsKeys.BRIGHTNESS_OFFSET_PERCENT,
            target.coerceIn(BrightnessOffsetRange.MIN.toLong(), BrightnessOffsetRange.MAX.toLong())
                .toInt(),
        )
    }

    override suspend fun reset() {
        setOffset(BrightnessOffset.DEFAULT)
    }

    override fun publish(application: BrightnessOffsetApplication) {
        mutableApplication.value = application
    }
}
