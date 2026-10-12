package com.ambientbrightness.core.HysteresisGate

/**
 * Stateful 10% directional hysteresis around the default curve's internal lux anchors.
 *
 * Create one gate per controller; rejected or invalid readings never change its active region.
 */
class DefaultHysteresisGate : HysteresisGate {
    private var activeRegion: Int? = null

    override fun accept(lux: Float): Float? {
        if (!lux.isFinite() || lux < 0f) return null

        val currentRegion = activeRegion
        if (currentRegion == null) {
            activeRegion = regionFor(lux)
            return lux
        }

        var nextRegion = currentRegion
        while (
            nextRegion < BOUNDARIES.size &&
            lux.toDouble() >= BOUNDARIES[nextRegion] * (1.0 + HYSTERESIS_WIDTH)
        ) {
            nextRegion++
        }
        while (
            nextRegion > 0 &&
            lux.toDouble() <= BOUNDARIES[nextRegion - 1] * (1.0 - HYSTERESIS_WIDTH)
        ) {
            nextRegion--
        }

        if (nextRegion != currentRegion) {
            activeRegion = nextRegion
            return lux
        }

        return if (regionFor(lux) == currentRegion) lux else null
    }

    private fun regionFor(lux: Float): Int =
        BOUNDARIES.count { lux.toDouble() >= it }

    private companion object {
        const val HYSTERESIS_WIDTH = 0.10
        val BOUNDARIES = doubleArrayOf(1.0, 10.0, 100.0, 1_000.0, 10_000.0)
    }
}
