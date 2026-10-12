package com.ambientbrightness.core.SettingsStore

import kotlinx.coroutines.flow.Flow

/**
 * Typed, reactive, persistent key/value boundary shared by F3 (offset), F4 (response speed) and F7
 * (dashboard). `:core` owns the contract only: the in-memory implementation serves tests and Dummy
 * composition, the persistent one is an Android DataStore adapter in `:infrastructure`.
 */
interface SettingsStore {

    /**
     * Emits the stored value of [key], or its [SettingsKey.default] while the key is absent, and
     * emits again after every [set] of that key — also when the [set] happened before collection
     * started or through another store instance over the same persistence.
     */
    fun <T> observe(key: SettingsKey<T>): Flow<T>

    /** Persists [value] under [key]; independent of any active collection of [observe]. */
    suspend fun <T> set(key: SettingsKey<T>, value: T)
}
