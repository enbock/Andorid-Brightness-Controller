package com.ambientbrightness.core.BackgroundService

/**
 * Why the controller was asked to start.
 *
 * The reason is explicit instead of a bare boolean so the lifecycle (T3) and the boot receiver (T5)
 * can tell a deliberate user start apart from a system-driven restart (US2).
 */
enum class StartReason {

    /** The user started the controller from the app (US1). */
    UserRequest,

    /**
     * The system restarted the service after reclaiming the process (`START_STICKY`); resumes with
     * the persisted settings (US2, AC2.1).
     */
    SystemRestart,

    /** The device finished booting and the controller was running before shutdown (US2, AC2.3). */
    Boot,
}
