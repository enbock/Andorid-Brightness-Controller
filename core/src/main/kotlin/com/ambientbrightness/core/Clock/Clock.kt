package com.ambientbrightness.core

/**
 * Abstraction over the system wall clock so that time-dependent logic can be
 * tested deterministically with a fake implementation.
 */
fun interface Clock {
    fun nowMillis(): Long
}

/**
 * Production [Clock] backed by [System.currentTimeMillis].
 */
object SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
