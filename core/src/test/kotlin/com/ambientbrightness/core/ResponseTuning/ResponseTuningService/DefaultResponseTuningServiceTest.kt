package com.ambientbrightness.core.ResponseTuning.ResponseTuningService

import com.ambientbrightness.core.FakeSettingsStore
import com.ambientbrightness.core.ResponseTuning.ResponseSpeed
import com.ambientbrightness.core.SettingsStore.SettingsKeys
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultResponseTuningServiceTest {

    @Test
    fun `AC4_2 - an absent key seeds the middle-of-the-range default`() = runTest {
        val service = DefaultResponseTuningService(FakeSettingsStore(), backgroundScope)

        runCurrent()

        assertEquals(ResponseSpeed.of(50), service.responseSpeed.value)
    }

    @Test
    fun `AC4_2 - a stored percent is clamped before it is exposed`() = runTest {
        val service = DefaultResponseTuningService(storeStoring(140), backgroundScope)

        runCurrent()

        assertEquals(ResponseSpeed.of(100), service.responseSpeed.value)
    }

    @Test
    fun `a speed persisted before construction is the seed and reaches a late collector`() =
        runTest {
            val service = DefaultResponseTuningService(storeStoring(20), backgroundScope)
            runCurrent()

            assertEquals(ResponseSpeed.of(20), service.responseSpeed.value)

            val emissions = mutableListOf<ResponseSpeed>()
            val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                service.responseSpeed.collect { speed -> emissions += speed }
            }
            runCurrent()

            assertEquals(listOf(ResponseSpeed.of(20)), emissions)
            collector.cancelAndJoin()
        }

    @Test
    fun `AC1_4 - setting the speed persists it and emits it to the running service`() = runTest {
        val store = FakeSettingsStore()
        val service = DefaultResponseTuningService(store, backgroundScope)
        val emissions = mutableListOf<ResponseSpeed>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            service.responseSpeed.collect { speed -> emissions += speed }
        }
        runCurrent()

        service.setResponseSpeed(ResponseSpeed.of(80))
        runCurrent()

        assertEquals(listOf(ResponseSpeed.of(50), ResponseSpeed.of(80)), emissions)
        assertEquals(ResponseSpeed.of(80), service.responseSpeed.value)
        assertEquals(listOf(SettingsKeys.RESPONSE_SPEED_PERCENT.name to 80), store.writes)
        assertEquals(80, store.observe(SettingsKeys.RESPONSE_SPEED_PERCENT).first())
        collector.cancelAndJoin()
    }

    @Test
    fun `AC1_4 - an out-of-range speed is clamped on the way into the store`() = runTest {
        val store = FakeSettingsStore()
        val service = DefaultResponseTuningService(store, backgroundScope)
        runCurrent()

        service.setResponseSpeed(ResponseSpeed.of(250))
        runCurrent()

        assertEquals(ResponseSpeed.of(100), service.responseSpeed.value)
        assertEquals(100, store.observe(SettingsKeys.RESPONSE_SPEED_PERCENT).first())
    }

    private fun storeStoring(percent: Int): FakeSettingsStore =
        FakeSettingsStore(mapOf(SettingsKeys.RESPONSE_SPEED_PERCENT.name to percent))
}
