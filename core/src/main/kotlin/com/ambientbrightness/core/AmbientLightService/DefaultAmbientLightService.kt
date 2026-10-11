package com.ambientbrightness.core

import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

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
    private val startupTimeoutMillis: Long = DEFAULT_SENSOR_STARTUP_TIMEOUT_MILLIS,
) : AmbientLightService {

    init {
        require(startupTimeoutMillis > 0L) { "startupTimeoutMillis must be positive" }
    }

    private val initialAvailability =
        (sensorClient.availability as? SensorAvailability.Unavailable)
            ?: SensorAvailability.Unavailable(SensorUnavailableReason.NotUsable)

    private val mutableAvailability = MutableStateFlow<SensorAvailability>(initialAvailability)
    override val availability: StateFlow<SensorAvailability> = mutableAvailability.asStateFlow()

    private val collectionMutex = Mutex()
    private val activeJobLock = Any()
    private var activeJob: Job? = null

    override fun start(): Flow<LuxSample> = channelFlow {
        collectionMutex.lock()
        val sessionJob = checkNotNull(currentCoroutineContext()[Job])
        synchronized(activeJobLock) { activeJob = sessionJob }

        try {
            val sensorAvailability = sensorClient.availability
            if (sensorAvailability is SensorAvailability.Unavailable) {
                publishIfActive(sessionJob, sensorAvailability)
                return@channelFlow
            }
            val profile = (sensorAvailability as SensorAvailability.Available).profile
            publishIfActive(
                sessionJob,
                SensorAvailability.Unavailable(SensorUnavailableReason.NotUsable),
            )

            val firstSample = CompletableDeferred<Unit>()
            val sampleJob = launch(start = CoroutineStart.UNDISPATCHED) {
                useCase.samples().collect { sample ->
                    firstSample.complete(Unit)
                    publishIfActive(sessionJob, SensorAvailability.Available(profile))
                    send(sample)
                }
            }

            try {
                val receivedFirstSample = withTimeoutOrNull(startupTimeoutMillis) {
                    firstSample.await()
                    true
                } ?: false

                if (!receivedFirstSample) {
                    publishIfActive(
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
            synchronized(activeJobLock) {
                if (activeJob === sessionJob) activeJob = null
            }
            collectionMutex.unlock()
        }
    }

    /**
     * Cancels the active collection and unregisters immediately. The use case also unregisters
     * from its cancellation cleanup; the client contract makes that operation idempotent.
     */
    override fun stop() {
        val jobToStop = synchronized(activeJobLock) { activeJob }
        if (jobToStop != null) {
            sensorClient.unregister()
            jobToStop.cancel()
        }
    }

    private fun publishIfActive(job: Job, value: SensorAvailability) {
        synchronized(activeJobLock) {
            if (activeJob === job && job.isActive) mutableAvailability.value = value
        }
    }
}
