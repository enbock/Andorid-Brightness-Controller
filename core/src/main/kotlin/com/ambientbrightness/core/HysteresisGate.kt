package com.ambientbrightness.core

/** Boundary for filtering small lux oscillations around mapping-curve transitions. */
interface HysteresisGate {
    /** Returns the lux value accepted by the gate, or null when the sample should be held back. */
    fun accept(lux: Float): Float?
}
