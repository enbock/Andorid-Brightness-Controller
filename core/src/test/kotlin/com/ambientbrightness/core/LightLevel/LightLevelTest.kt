package com.ambientbrightness.core

import com.ambientbrightness.core.AmbientLightSensing.LightLevel.LightLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The dashboard and label resolution (F7, Decision D10) switch exhaustively over [LightLevel], so
 * the member set is part of the contract.
 */
class LightLevelTest {

    @Test
    fun `declares the five light levels plus UNKNOWN (Decision D10)`() {
        assertEquals(
            listOf("DARK", "DIM", "INDOOR", "BRIGHT", "FULL_SUN", "UNKNOWN"),
            LightLevel.entries.map { it.name },
        )
    }
}
