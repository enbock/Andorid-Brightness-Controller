package com.ambientbrightness.core

/**
 * Recording [ReadingStabiliser] test double. [resultProvider] decides what is returned, so throttle
 * behaviour can be asserted independently of the real stabiliser.
 */
class FakeReadingStabiliser(
    var resultProvider: (LuxSample) -> LuxSample? = { it },
) : ReadingStabiliser {

    val received: MutableList<LuxSample> = mutableListOf()

    override fun stabilise(sample: LuxSample): LuxSample? {
        received += sample
        return resultProvider(sample)
    }
}
