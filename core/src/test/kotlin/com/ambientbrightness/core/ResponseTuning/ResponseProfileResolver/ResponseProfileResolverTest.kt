package com.ambientbrightness.core.ResponseTuning.ResponseProfileResolver

import com.ambientbrightness.core.ResponseTuning.ResponseSpeed
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResponseProfileResolverTest {

    private val resolver = ResponseProfileResolver()

    @Test
    fun `AC4_2 - the default speed resolves to the middle of both windows`() {
        val profile = resolver.resolve(ResponseSpeed.of(ResponseSpeed.DEFAULT))

        assertEquals(1_650L, profile.reactionWindowMillis)
        assertEquals(375L, profile.debounceWindowMillis)
        assertEquals(ResponseProfileResolver.MAX_STEP_FRACTION, profile.maxStepFraction)
    }

    @Test
    fun `the fastest speed resolves to the shortest windows`() {
        val profile = resolver.resolve(ResponseSpeed.of(ResponseSpeed.FASTEST_PERCENT))

        assertEquals(300L, profile.reactionWindowMillis)
        assertEquals(150L, profile.debounceWindowMillis)
        assertEquals(0.15f, profile.maxStepFraction)
    }

    @Test
    fun `the slowest speed resolves to the longest windows`() {
        val profile = resolver.resolve(ResponseSpeed.of(ResponseSpeed.SLOWEST_PERCENT))

        assertEquals(3_000L, profile.reactionWindowMillis)
        assertEquals(600L, profile.debounceWindowMillis)
        assertEquals(0.15f, profile.maxStepFraction)
    }

    @Test
    fun `both windows grow monotonically with the speed percent`() {
        val profiles = (ResponseSpeed.FASTEST_PERCENT..ResponseSpeed.SLOWEST_PERCENT)
            .map { percent -> resolver.resolve(ResponseSpeed.of(percent)) }

        assertEquals(profiles.sortedBy { it.reactionWindowMillis }, profiles)
        assertEquals(profiles.sortedBy { it.debounceWindowMillis }, profiles)
        assertTrue(profiles.map { it.reactionWindowMillis }.distinct().size > 10)
    }

    @Test
    fun `the anti-flash step cap is the same plain value at every speed`() {
        val stepCaps = (ResponseSpeed.FASTEST_PERCENT..ResponseSpeed.SLOWEST_PERCENT)
            .map { percent -> resolver.resolve(ResponseSpeed.of(percent)).maxStepFraction }
            .distinct()

        assertEquals(listOf(0.15f), stepCaps)
    }
}
