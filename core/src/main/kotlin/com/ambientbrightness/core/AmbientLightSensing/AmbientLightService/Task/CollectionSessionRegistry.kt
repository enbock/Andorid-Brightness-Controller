package com.ambientbrightness.core.AmbientLightSensing.AmbientLightService.Task

import com.ambientbrightness.core.AmbientLightSensing.SensorAvailability
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex

internal class CollectionSessionRegistry(
    private val availabilitySink: MutableStateFlow<SensorAvailability>,
) {

    private val collectionMutex = Mutex()
    private val activeJobLock = Any()
    private var activeJob: Job? = null

    suspend fun lockCollection() {
        collectionMutex.lock()
    }

    fun unlockCollection() {
        collectionMutex.unlock()
    }

    fun registerSession(sessionJob: Job) {
        synchronized(activeJobLock) {
            activeJob = sessionJob
        }
    }

    fun releaseSession(sessionJob: Job) {
        synchronized(activeJobLock) {
            if (activeJob === sessionJob) activeJob = null
        }
    }

    fun publishIfActive(sessionJob: Job, value: SensorAvailability) {
        synchronized(activeJobLock) {
            if (activeJob === sessionJob && sessionJob.isActive) availabilitySink.value = value
        }
    }

    fun cancelActiveSession(): Job? = synchronized(activeJobLock) { activeJob }
}
