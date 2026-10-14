package com.ambientbrightness.infrastructure.AppSetupService.SetupStateStore.Android

import android.content.Context
import android.content.SharedPreferences
import com.ambientbrightness.core.AppSetupService.SetupStateStore

/**
 * Persistent [SetupStateStore] backed by one [SharedPreferences] file.
 *
 * SharedPreferences is chosen over DataStore for the same reason as F5's
 * `AndroidControllerStateStore`: the [SetupStateStore] contract is synchronous (`var
 * setupAcknowledged` / `var lastShownAtMillis`), while DataStore only offers a suspending/flow API.
 * Reads are served from the in-memory snapshot SharedPreferences loads on first access; writes are
 * applied asynchronously. Absent values are tolerated: an unwritten acknowledgement reads as `false`
 * and an unwritten [lastShownAtMillis] reads as `null` rather than a misleading `0L` (US1, AC1.3).
 */
class AndroidSetupStateStore(context: Context) : SetupStateStore {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override var setupAcknowledged: Boolean
        get() = preferences.getBoolean(KEY_SETUP_ACKNOWLEDGED, false)
        set(value) {
            preferences.edit().putBoolean(KEY_SETUP_ACKNOWLEDGED, value).apply()
        }

    override var lastShownAtMillis: Long?
        get() = if (preferences.contains(KEY_LAST_SHOWN_AT_MILLIS)) {
            preferences.getLong(KEY_LAST_SHOWN_AT_MILLIS, 0L)
        } else {
            null
        }
        set(value) {
            preferences.edit().apply {
                if (value == null) remove(KEY_LAST_SHOWN_AT_MILLIS) else putLong(KEY_LAST_SHOWN_AT_MILLIS, value)
            }.apply()
        }

    companion object {
        const val FILE_NAME = "setup_state"

        private const val KEY_SETUP_ACKNOWLEDGED = "setup_acknowledged"
        private const val KEY_LAST_SHOWN_AT_MILLIS = "last_shown_at_millis"
    }
}
