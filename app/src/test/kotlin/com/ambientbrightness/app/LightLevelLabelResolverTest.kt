package com.ambientbrightness.app

import com.ambientbrightness.app.R
import com.ambientbrightness.app.StatusDashboard.View.Task.LightLevelLabelResolver
import com.ambientbrightness.core.AmbientLightSensing.LightLevel.LightLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every light level must resolve to its own label resource (US1, AC1.2). */
class LightLevelLabelResolverTest {

    @Test
    fun `each level resolves to the matching string resource`() {
        assertEquals(R.string.light_level_dark, LightLevelLabelResolver.resolve(LightLevel.DARK))
        assertEquals(R.string.light_level_dim, LightLevelLabelResolver.resolve(LightLevel.DIM))
        assertEquals(R.string.light_level_indoor, LightLevelLabelResolver.resolve(LightLevel.INDOOR))
        assertEquals(R.string.light_level_bright, LightLevelLabelResolver.resolve(LightLevel.BRIGHT))
        assertEquals(R.string.light_level_full_sun, LightLevelLabelResolver.resolve(LightLevel.FULL_SUN))
        assertEquals(R.string.light_level_unknown, LightLevelLabelResolver.resolve(LightLevel.UNKNOWN))
    }

    @Test
    fun `the mapping is total and every level gets its own label`() {
        val resolved: List<Int> =
            LightLevel.entries.map { level -> LightLevelLabelResolver.resolve(level) }

        assertEquals(LightLevel.entries.size, resolved.size)
        assertTrue("every level needs a real label resource", resolved.none { it == 0 })
        assertEquals("every level needs a distinct label", resolved.size, resolved.distinct().size)
    }
}
