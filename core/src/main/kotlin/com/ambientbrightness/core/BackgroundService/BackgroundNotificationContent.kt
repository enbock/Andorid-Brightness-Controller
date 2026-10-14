package com.ambientbrightness.core.BackgroundService

/**
 * Android-free description of what the ongoing notification must show (US4).
 *
 * This is the hand-off between the notification builder (T2, `:core`) and the platform notification
 * factory (T4, `:infrastructure`): it carries only strings and flags — never a `Notification`, a
 * channel or a bitmap — so it can be built and asserted in plain JVM tests.
 *
 * @property title the notification title.
 * @property text the single-line body, e.g. the currently applied brightness (US4, AC4.1).
 * @property ongoing whether the notification is non-dismissible while the service runs (AC4.3).
 * @property showStopAction whether the single "Stop" action is offered (US4, AC4.2).
 */
data class BackgroundNotificationContent(
    val title: String,
    val text: String,
    val ongoing: Boolean,
    val showStopAction: Boolean,
)
