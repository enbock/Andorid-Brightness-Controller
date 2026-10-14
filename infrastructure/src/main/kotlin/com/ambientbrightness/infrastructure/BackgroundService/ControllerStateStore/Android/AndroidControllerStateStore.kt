package com.ambientbrightness.infrastructure.BackgroundService.ControllerStateStore.Android

import android.content.Context
import android.content.SharedPreferences
import com.ambientbrightness.core.BackgroundService.ControllerStateStore
import com.ambientbrightness.core.BackgroundService.StartReason

/**
 * Persistent [ControllerStateStore] backed by one [SharedPreferences] file.
 *
 * SharedPreferences is chosen over DataStore because the [ControllerStateStore] contract is
 * synchronous: the boot receiver must read [desiredRunning] inside `onReceive` without suspending,
 * and DataStore only offers a suspending/flow API. Reads are served from the in-memory snapshot
 * SharedPreferences loads on first access; writes are applied asynchronously. The start reason is
 * stored by [StartReason.name] and an unknown or absent name reads back as null, so removing or
 * renaming an enum value can never make boot crash.
 */
class AndroidControllerStateStore(context: Context) : ControllerStateStore {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override var desiredRunning: Boolean
        get() = preferences.getBoolean(KEY_DESIRED_RUNNING, false)
        set(value) {
            preferences.edit().putBoolean(KEY_DESIRED_RUNNING, value).apply()
        }

    override var lastStartReason: StartReason?
        get() = preferences.getString(KEY_LAST_START_REASON, null)?.let(::startReasonOrNull)
        set(value) {
            preferences.edit().putString(KEY_LAST_START_REASON, value?.name).apply()
        }

    companion object {
        const val FILE_NAME = "background_service_state"

        private const val KEY_DESIRED_RUNNING = "desired_running"
        private const val KEY_LAST_START_REASON = "last_start_reason"
    }
}

internal fun startReasonOrNull(name: String): StartReason? =
    StartReason.entries.firstOrNull { it.name == name }
