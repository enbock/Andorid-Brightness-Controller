package com.ambientbrightness.core

import kotlinx.coroutines.flow.StateFlow

/**
 * Observable screen on/off state; the Android adapter lives in `:infrastructure` (T2).
 *
 * [isScreenOn] is a hot, conflated stream that always has a current value, so consumers can gate
 * work on it without polling (AC1.4).
 */
interface ScreenStateClient {

    val isScreenOn: StateFlow<Boolean>
}
