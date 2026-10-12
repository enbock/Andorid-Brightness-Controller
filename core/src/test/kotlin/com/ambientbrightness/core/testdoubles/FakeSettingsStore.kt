package com.ambientbrightness.core

import com.ambientbrightness.core.SettingsStore.SettingsKey
import com.ambientbrightness.core.SettingsStore.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [SettingsStore] test double, reactive like the real stores and seeded through its
 * constructor. [writes] records what a subject persisted, so a test can assert both the write and
 * the value read back through [observe].
 */
class FakeSettingsStore(initialValues: Map<String, Any?> = emptyMap()) : SettingsStore {

    private val stored = MutableStateFlow(initialValues)

    val writes: MutableList<Pair<String, Any?>> = mutableListOf()

    override fun <T> observe(key: SettingsKey<T>): Flow<T> =
        stored.map { values -> key.coerce(values[key.name]) }

    override suspend fun <T> set(key: SettingsKey<T>, value: T) {
        writes += key.name to value
        stored.update { values -> values + (key.name to value) }
    }
}
