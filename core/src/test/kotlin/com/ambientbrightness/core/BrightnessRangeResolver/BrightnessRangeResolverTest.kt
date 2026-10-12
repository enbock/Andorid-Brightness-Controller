package com.ambientbrightness.core.BrightnessRangeResolver

import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessRange
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessRangeResolver.BrightnessRangeResolver
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class BrightnessRangeResolverTest {

    private val resolver = BrightnessRangeResolver()

    @Test
    fun `AC2_3 reported positive device range passes through unchanged`() {
        val reported = BrightnessRange(BrightnessValue(7), BrightnessValue(180))

        assertSame(reported, resolver.resolve(reported))
    }

    @Test
    fun `AC2_3 null device range uses the positive 1 to 255 fallback and never zero`() {
        val resolved = resolver.resolve(null)

        assertEquals(BrightnessRange(BrightnessValue(1), BrightnessValue(255)), resolved)
        assertEquals(1, resolved.min.value)
        assertEquals(255, resolved.max.value)
    }
}
