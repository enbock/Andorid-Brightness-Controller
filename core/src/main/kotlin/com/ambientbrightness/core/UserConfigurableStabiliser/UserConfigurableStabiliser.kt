package com.ambientbrightness.core.UserConfigurableStabiliser

import com.ambientbrightness.core.AmbientLightSensing.LuxSample
import com.ambientbrightness.core.AmbientLightSensing.ReadingStabiliser.ReadingStabiliser
import com.ambientbrightness.core.ResponseTuning.ResponseProfile
import com.ambientbrightness.core.ResponseTuning.ResponseProfileResolver.ResponseProfileResolver
import com.ambientbrightness.core.ResponseTuning.ResponseSpeed
import com.ambientbrightness.core.Shared.Clock
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

/**
 * F4 [ReadingStabiliser] that adds the time domain on top of the injected [delegate] (F1's
 * `OutlierClampingStabiliser` in production).
 *
 * The delegate keeps its instantaneous duties unchanged — range clamp, single-outlier rejection and
 * `0 lux` validity — and is never bypassed or reconfigured. This decorator only adds two time-based
 * rules, both re-read from the [ResponseProfile] of the current [responseSpeed] on every call, so a
 * changed setting applies to the next sample without re-injection or restart (AC1.4):
 *
 * 1. **Debounce gate (AC3.1, AC3.2):** a reading that differs from the level in effect by more than
 *    the noise band only becomes the new target once it has been sustained for
 *    `debounceWindowMillis`. Until then the level in effect is emitted, so a change lasting less
 *    than the window (a passing shadow, a hand movement, a quick cloud) is dropped entirely.
 * 2. **Smoothing (AC1.1, AC1.3):** a target is approached by an exponential moving average whose
 *    rate is the fraction of `reactionWindowMillis` that elapsed since the previous call. A fast
 *    setting therefore reaches the target in one step once the window has elapsed, while a slow
 *    setting approaches it gradually. The value snaps onto the target as soon as the remaining
 *    distance is below the noise band, so a steady level settles exactly and cannot oscillate.
 *
 * A reading within the noise band of the level in effect counts as no change at all, which is what
 * makes small alternating readings around a stable average a non-event (AC3.3). The decorator never
 * throws and never emits a non-finite or negative lux value.
 */
class UserConfigurableStabiliser(
    private val delegate: ReadingStabiliser,
    private val responseSpeed: StateFlow<ResponseSpeed>,
    private val clock: Clock,
    private val resolver: ResponseProfileResolver = ResponseProfileResolver(),
) : ReadingStabiliser {

    private var effectiveLux: Float? = null
    private var candidateLux: Float? = null
    private var candidateSinceMillis: Long = 0L
    private var lastCallMillis: Long? = null

    override fun stabilise(sample: LuxSample): LuxSample? {
        val accepted = delegate.stabilise(sample) ?: return null
        val acceptedLux = accepted.lux
        if (!acceptedLux.isFinite() || acceptedLux < 0f) return null

        val nowMillis = clock.nowMillis()
        val current = effectiveLux
        if (current == null) {
            effectiveLux = acceptedLux
            lastCallMillis = nowMillis
            return accepted.copy(lux = acceptedLux)
        }

        val noiseBand = noiseBandLux(reference = current)
        if (abs(acceptedLux - current) <= noiseBand) {
            candidateLux = null
            lastCallMillis = nowMillis
            return accepted.copy(lux = current)
        }

        val candidate = candidateLux
        if (candidate == null || abs(acceptedLux - candidate) > noiseBand) {
            candidateLux = acceptedLux
            candidateSinceMillis = nowMillis
            lastCallMillis = nowMillis
            return accepted.copy(lux = current)
        }

        val profile = resolver.resolve(responseSpeed.value)
        if (nowMillis - candidateSinceMillis < profile.debounceWindowMillis) {
            lastCallMillis = nowMillis
            return accepted.copy(lux = current)
        }

        val smoothedLux = smoothTowards(
            current = current,
            target = acceptedLux,
            elapsedMillis = (nowMillis - (lastCallMillis ?: nowMillis)).coerceAtLeast(0L),
            profile = profile,
            noiseBand = noiseBand,
        )
        effectiveLux = smoothedLux
        lastCallMillis = nowMillis
        return accepted.copy(lux = smoothedLux)
    }

    private fun smoothTowards(
        current: Float,
        target: Float,
        elapsedMillis: Long,
        profile: ResponseProfile,
        noiseBand: Float,
    ): Float {
        val windowFraction =
            (elapsedMillis.toFloat() / profile.reactionWindowMillis.toFloat()).coerceIn(0f, 1f)
        val smoothed = current + (target - current) * windowFraction
        return if (abs(target - smoothed) <= noiseBand) target else smoothed
    }

    /** Differences this small are sensor noise rather than a change in ambient light (AC3.3). */
    private fun noiseBandLux(reference: Float): Float =
        maxOf(MINIMUM_NOISE_BAND_LUX, NOISE_BAND_FRACTION * reference)

    private companion object {
        const val MINIMUM_NOISE_BAND_LUX = 2f
        const val NOISE_BAND_FRACTION = 0.05f
    }
}
