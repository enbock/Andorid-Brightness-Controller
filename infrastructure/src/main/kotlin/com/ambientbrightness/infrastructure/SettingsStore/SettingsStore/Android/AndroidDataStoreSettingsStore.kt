package com.ambientbrightness.infrastructure.SettingsStore.SettingsStore.Android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.ambientbrightness.core.SettingsStore.SettingsKey
import com.ambientbrightness.core.SettingsStore.SettingsStore
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext

/**
 * Persistent [SettingsStore] over one Preferences DataStore file. Its DataStore and dispatcher are
 * injected rather than resolved from a process-wide singleton, so the composition root owns the one
 * app-scoped instance; writes are suspended onto [ioDispatcher] and reads are dispatched on the
 * DataStore's own scope. An absent key reads as its own default and an IO failure reads as defaults.
 */
class AndroidDataStoreSettingsStore(
    private val dataStore: DataStore<Preferences>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SettingsStore {

    override fun <T> observe(key: SettingsKey<T>): Flow<T> =
        dataStore.data
            .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
            .map { preferences -> key.coerce(preferences.rawValue(key)) }

    override suspend fun <T> set(key: SettingsKey<T>, value: T) {
        withContext(ioDispatcher) {
            dataStore.edit { preferences -> preferences.write(key, value) }
        }
    }

    companion object {

        const val SETTINGS_FILE_NAME = "settings_store.preferences_pb"

        /** Builds the store for the app-private [fileName], replacing a corrupt file with empty preferences so a bad file can only yield defaults. */
        fun create(
            context: Context,
            scope: CoroutineScope,
            fileName: String = SETTINGS_FILE_NAME,
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        ): AndroidDataStoreSettingsStore = AndroidDataStoreSettingsStore(
            dataStore = PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = scope + ioDispatcher,
                produceFile = { context.applicationContext.preferencesDataStoreFile(fileName) },
            ),
            ioDispatcher = ioDispatcher,
        )
    }
}

private fun Preferences.rawValue(key: SettingsKey<*>): Any? =
    when (key) {
        is SettingsKey.IntKey -> this[intPreferencesKey(key.name)]
        is SettingsKey.LongKey -> this[longPreferencesKey(key.name)]
        is SettingsKey.FloatKey -> this[floatPreferencesKey(key.name)]
        is SettingsKey.BooleanKey -> this[booleanPreferencesKey(key.name)]
        is SettingsKey.StringKey -> this[stringPreferencesKey(key.name)]
    }

private fun MutablePreferences.write(key: SettingsKey<*>, value: Any?) {
    when (key) {
        is SettingsKey.IntKey -> this[intPreferencesKey(key.name)] = key.coerce(value)
        is SettingsKey.LongKey -> this[longPreferencesKey(key.name)] = key.coerce(value)
        is SettingsKey.FloatKey -> this[floatPreferencesKey(key.name)] = key.coerce(value)
        is SettingsKey.BooleanKey -> this[booleanPreferencesKey(key.name)] = key.coerce(value)
        is SettingsKey.StringKey -> this[stringPreferencesKey(key.name)] = key.coerce(value)
    }
}
