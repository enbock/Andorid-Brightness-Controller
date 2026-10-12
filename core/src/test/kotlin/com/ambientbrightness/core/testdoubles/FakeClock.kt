package com.ambientbrightness.core

import com.ambientbrightness.core.Shared.Clock

/**
 * Manually advanced [Clock] for deterministic tests.
 */
class FakeClock(var nowMillis: Long = 0L) : Clock {

    override fun nowMillis(): Long = nowMillis

    fun advanceBy(deltaMillis: Long) {
        nowMillis += deltaMillis
    }

    fun advanceTo(millis: Long) {
        nowMillis = millis
    }
}
