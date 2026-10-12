package com.ambientbrightness.core.AmbientLightSensing.AmbientLightService

import com.ambientbrightness.core.AmbientLightSensing.AmbientLightSensorClient
import com.ambientbrightness.core.AmbientLightSensing.AmbientLightService.Task.CollectionSessionRegistry
import com.ambientbrightness.core.AmbientLightSensing.AmbientLightService.Task.StartupAvailabilityGate
import com.ambientbrightness.core.AmbientLightSensing.AmbientLightUseCase.AmbientLightUseCase
import com.ambientbrightness.core.AmbientLightSensing.LuxSample
import com.ambientbrightness.core.AmbientLightSensing.SensorAvailability
import com.ambientbrightness.core.AmbientLightSensing.SensorUnavailableReason
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Maximum time to wait for the sensor's first reading after registration. */
const val DEFAULT_SENSOR_STARTUP_TIMEOUT_MILLIS: Long = 5_000L

/**
 * Adds first-reading availability detection around the throttled sensor use case.
 *
 * The timeout includes the first use-case sampling interval. A sensor is not reported available
 * until a real (including zero-lux) sample has been produced.
 */
class DefaultAmbientLightService(
    private val sensorClient: AmbientLightSensorClient,
    private val useCase: AmbientLightUseCase,
    startupTimeoutMillis: Long = DEFAULT_SENSOR_STARTUP_TIMEOUT_MILLIS,
) : AmbientLightService {

    private val startupGate = StartupAvailabilityGate(startupTimeoutMillis)

    private val initialAvailability =
        (sensorClient.availability as? SensorAvailability.Unavailable)
            ?: SensorAvailability.Unavailable(SensorUnavailableReason.NotUsable)

    private val mutableAvailability = MutableStateFlow<SensorAvailability>(initialAvailability)
    override val availability: StateFlow<SensorAvailability> = mutableAvailability.asStateFlow()

    private val sessions = CollectionSessionRegistry(mutableAvailability)

    override fun start(): Flow<LuxSample> = channelFlow {
        sessions.lockCollection()
        val sessionJob = checkNotNull(currentCoroutineContext()[Job])
        sessions.registerSession(sessionJob)

        try {
            val sensorAvailability = sensorClient.availability
            if (sensorAvailability is SensorAvailability.Unavailable) {
                sessions.publishIfActive(sessionJob, sensorAvailability)
                return@channelFlow
            }
            val profile = (sensorAvailability as SensorAvailability.Available).profile
            sessions.publishIfActive(
                sessionJob,
                SensorAvailability.Unavailable(SensorUnavailableReason.NotUsable),
            )

            val watchdog = startupGate.newWatchdog()
            val sampleJob = launch(start = CoroutineStart.UNDISPATCHED) {
                useCase.samples().collect { sample ->
                    watchdog.markFirstSample()
                    sessions.publishIfActive(sessionJob, SensorAvailability.Available(profile))
                    send(sample)
                }
            }

            try {
                val receivedFirstSample = watchdog.awaitFirstSample()

                if (!receivedFirstSample) {
                    sessions.publishIfActive(
                        sessionJob,
                        SensorAvailability.Unavailable(SensorUnavailableReason.NotUsable),
                    )
                    sampleJob.cancelAndJoin()
                    return@channelFlow
                }

                sampleJob.join()
            } finally {
                sampleJob.cancelAndJoin()
            }
        } finally {
            sessions.releaseSession(sessionJob)
            sessions.unlockCollection()
        }
    }

    /**
     * Cancels the active collection and unregisters immediately. The use case also unregisters
     * from its cancellation cleanup; the client contract makes that operation idempotent.
     */
    override fun stop() {
        val jobToStop = sessions.cancelActiveSession()
        if (jobToStop != null) {
            sensorClient.unregister()
            jobToStop.cancel()
        }
    }
}
