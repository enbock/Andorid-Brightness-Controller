package com.ambientbrightness.infrastructure

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ambientbrightness.core.SettingsStore.SettingsKey
import com.ambientbrightness.infrastructure.SettingsStore.SettingsStore.Android.AndroidDataStoreSettingsStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Simulated process restart on a device: the first store instance is released, so the second one
 * starts from the file the first one wrote. Runs only with a device/emulator attached.
 */
@RunWith(AndroidJUnit4::class)
class AndroidDataStoreSettingsStoreTest {

    @Test
    fun valueWrittenByOneStoreInstanceIsReadByAFreshInstance() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fileName = "$FILE_PREFIX${System.nanoTime()}$FILE_SUFFIX"
        val key = SettingsKey.IntKey("round_trip_percent", ABSENT_DEFAULT)
        val file = preferencesFile(context.filesDir, fileName)

        try {
            val firstJob = SupervisorJob()
            try {
                val first = AndroidDataStoreSettingsStore.create(
                    context = context,
                    scope = CoroutineScope(firstJob + Dispatchers.IO),
                    fileName = fileName,
                )

                assertEquals("an absent key reads as its default", ABSENT_DEFAULT, first.observe(key).first())
                first.set(key, WRITTEN_VALUE)
                assertEquals("the write is observable on the writing instance", WRITTEN_VALUE, first.observe(key).first())
            } finally {
                firstJob.cancelAndJoin()
            }

            val secondJob = SupervisorJob()
            try {
                val second = AndroidDataStoreSettingsStore.create(
                    context = context,
                    scope = CoroutineScope(secondJob + Dispatchers.IO),
                    fileName = fileName,
                )

                assertEquals(
                    "a fresh instance reads the persisted value",
                    WRITTEN_VALUE,
                    second.observe(key).first(),
                )
            } finally {
                secondJob.cancelAndJoin()
            }
        } finally {
            file.delete()
        }
    }

    private fun preferencesFile(filesDir: File, fileName: String): File =
        File(File(filesDir, PREFERENCES_DIRECTORY), fileName)

    private companion object {
        const val FILE_PREFIX = "settings_store_test_"
        const val FILE_SUFFIX = ".preferences_pb"
        const val PREFERENCES_DIRECTORY = "datastore"
        const val ABSENT_DEFAULT = 50
        const val WRITTEN_VALUE = 37
    }
}
