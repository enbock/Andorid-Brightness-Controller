package com.ambientbrightness.app.SetupFlow.Navigator

/**
 * A system screen the setup flow may open: the `Settings` action and, when the screen is
 * app-specific, the `package:` data URI.
 *
 * `:app` owns this small value type so the navigation decision (which screen, in which order) is pure
 * data and stays unit-testable without an Android runtime; the Android `Intent` is built only in
 * [SetupFlowNavigator.create], where the Activity context lives.
 */
data class SetupScreen(
    val action: String,
    val dataUri: String? = null,
)
