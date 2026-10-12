package com.ambientbrightness.core.SettingsStore

/**
 * Identity, value type and absent-case value of one persisted preference.
 *
 * The value type is the sealed subtype, so the key decides how the value is stored and a caller
 * cannot pair a default with the wrong type. [SettingsStore] implementations map the subtype to
 * their mechanism (an in-memory map entry, a Preferences accessor) without `:core` naming any
 * storage type.
 */
sealed class SettingsKey<T>(val name: String, val default: T) {

    /** Interprets a stored value: a value of the key's own type passes through, anything absent or foreign becomes [default]. */
    abstract fun coerce(stored: Any?): T

    class IntKey(name: String, default: Int) : SettingsKey<Int>(name, default) {
        override fun coerce(stored: Any?): Int = stored as? Int ?: default
    }

    class LongKey(name: String, default: Long) : SettingsKey<Long>(name, default) {
        override fun coerce(stored: Any?): Long = stored as? Long ?: default
    }

    class FloatKey(name: String, default: Float) : SettingsKey<Float>(name, default) {
        override fun coerce(stored: Any?): Float = stored as? Float ?: default
    }

    class BooleanKey(name: String, default: Boolean) : SettingsKey<Boolean>(name, default) {
        override fun coerce(stored: Any?): Boolean = stored as? Boolean ?: default
    }

    class StringKey(name: String, default: String) : SettingsKey<String>(name, default) {
        override fun coerce(stored: Any?): String = stored as? String ?: default
    }
}
