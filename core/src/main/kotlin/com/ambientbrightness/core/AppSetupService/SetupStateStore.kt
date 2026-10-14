package com.ambientbrightness.core.AppSetupService

/**
 * Persists the two setup facts that must survive process death (US1, AC1.3).
 *
 * `:core` owns the contract only; the persistent implementation is an Android DataStore adapter in
 * `:infrastructure`. All requirement states stay live and are never stored here, because caching
 * them would contradict the re-check on resume (US2, AC2.4).
 */
interface SetupStateStore {

    /** True once the user completed or skipped the setup, so it is not asked again on every start. */
    var setupAcknowledged: Boolean

    /** When the checklist was last shown, for analytics/debugging; null when it never was. */
    var lastShownAtMillis: Long?
}
