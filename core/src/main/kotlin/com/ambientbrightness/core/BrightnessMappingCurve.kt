package com.ambientbrightness.core

/** Maps ambient illuminance in lux to a normalized brightness fraction in the range [0, 1]. */
interface BrightnessMappingCurve {
    fun toFraction(lux: Float): Float
}
