package com.ambientbrightness.core.HysteresisGate

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DefaultHysteresisGateTest {
    @Test
    fun `AC3_2 - accepts initial valid sample and same-region samples`() {
        val gate = DefaultHysteresisGate()

        assertEquals(4f, gate.accept(4f))
        assertEquals(5f, gate.accept(5f))
        assertEquals(9f, gate.accept(9f))
    }

    @Test
    fun `AC3_2 - advances upward only after crossing 110 percent of boundary`() {
        val gate = DefaultHysteresisGate()
        gate.accept(0.5f)

        assertNull(gate.accept(1f))
        assertNull(gate.accept(1.09f))
        assertEquals(1.1f, gate.accept(1.1f))
        assertEquals(1.05f, gate.accept(1.05f))
        assertNull(gate.accept(0.95f))
    }

    @Test
    fun `AC3_2 - retreats downward only after crossing 90 percent of boundary`() {
        val gate = DefaultHysteresisGate()
        gate.accept(2f)

        assertNull(gate.accept(0.91f))
        assertEquals(0.9f, gate.accept(0.9f))
        assertEquals(0.95f, gate.accept(0.95f))
        assertNull(gate.accept(1.05f))
    }

    @Test
    fun `AC3_2 - jitters inside transition bands without changing active region`() {
        val gate = DefaultHysteresisGate()
        gate.accept(0.5f)

        assertEquals(0.95f, gate.accept(0.95f))
        assertNull(gate.accept(1.04f))
        assertEquals(0.97f, gate.accept(0.97f))
        assertNull(gate.accept(1.09f))
        assertEquals(0.92f, gate.accept(0.92f))
        assertEquals(0.8f, gate.accept(0.8f))
    }

    @Test
    fun `AC3_2 - accepts jumps across multiple segments in either direction`() {
        val gate = DefaultHysteresisGate()
        gate.accept(0f)

        assertEquals(20_000f, gate.accept(20_000f))
        assertEquals(50f, gate.accept(50f))
        assertEquals(0f, gate.accept(0f))
    }

    @Test
    fun `AC3_2 - invalid inputs are rejected without corrupting state`() {
        val gate = DefaultHysteresisGate()
        assertEquals(0.5f, gate.accept(0.5f))

        listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
            .forEach { assertNull(gate.accept(it)) }
        assertNull(gate.accept(1.05f))
        assertEquals(1.1f, gate.accept(1.1f))
    }
}
