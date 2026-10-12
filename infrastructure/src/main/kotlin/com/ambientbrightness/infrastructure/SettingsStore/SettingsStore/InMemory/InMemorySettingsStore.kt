package com.ambientbrightness.infrastructure.SettingsStore.SettingsStore.InMemory

import com.ambientbrightness.core.SettingsStore.SettingsKey
import com.ambientbrightness.core.SettingsStore.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Process-local [SettingsStore] for Dummy composition and tests: as reactive as the persistent
 * store, but the values vanish with the process. Thread-safe because every write goes through the
 * atomic [MutableStateFlow.update] and every read observes the resulting immutable snapshot.
 */
class InMemorySettingsStore : SettingsStore {

    private val stored = MutableStateFlow<Map<String, Any?>>(emptyMap())

    override fun <T> observe(key: SettingsKey<T>): Flow<T> =
        stored.map { values -> key.coerce(values[key.name]) }

    override suspend fun <T> set(key: SettingsKey<T>, value: T) {
        stored.update { values -> values + (key.name to value) }
    }
}
