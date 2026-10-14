package com.ambientbrightness.infrastructure

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ambientbrightness.infrastructure.AppSetupService.NotificationPermissionChecker.Android.AndroidNotificationPermissionChecker
import com.ambientbrightness.infrastructure.AppSetupService.SetupStateStore.Android.AndroidSetupStateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device tests for the F6 setup adapters: the store's persistence round-trip and the checker's read
 * of the live notification state. Compile-only for now — no device/emulator is available in CI.
 */
@RunWith(AndroidJUnit4::class)
class AndroidAppSetupAdaptersTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun setupStoreReadsAbsentValuesAsFalseAndNull() {
        clearStore()
        try {
            val store = AndroidSetupStateStore(context)

            assertFalse("an unwritten acknowledgement reads as false", store.setupAcknowledged)
            assertNull("an unwritten shown time reads as null, not 0", store.lastShownAtMillis)
        } finally {
            clearStore()
        }
    }

    @Test
    fun setupStoreRoundTripsValuesAcrossInstances() {
        clearStore()
        try {
            val first = AndroidSetupStateStore(context)
            first.setupAcknowledged = true
            first.lastShownAtMillis = SHOWN_AT

            val second = AndroidSetupStateStore(context)
            assertTrue("the acknowledgement survives the first instance", second.setupAcknowledged)
            assertEquals("the shown time survives the first instance", SHOWN_AT, second.lastShownAtMillis)
        } finally {
            clearStore()
        }
    }

    @Test
    fun setupStoreClearsTheShownTimeWhenSetToNull() {
        clearStore()
        try {
            val first = AndroidSetupStateStore(context)
            first.lastShownAtMillis = SHOWN_AT

            first.lastShownAtMillis = null

            assertNull(AndroidSetupStateStore(context).lastShownAtMillis)
        } finally {
            clearStore()
        }
    }

    @Test
    fun notificationCheckerReadsTheCurrentState() {
        val expected = platformNotificationsEnabled() && platformPermissionGranted()

        val checker = AndroidNotificationPermissionChecker.create(context)
        assertEquals("the checker reports the live platform combination", expected, checker.isGranted.value)

        checker.refresh()
        assertEquals("refresh re-reads the same live state", expected, checker.isGranted.value)
    }

    private fun platformNotificationsEnabled(): Boolean =
        context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    private fun platformPermissionGranted(): Boolean =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun clearStore() {
        context.getSharedPreferences(AndroidSetupStateStore.FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private companion object {
        const val SHOWN_AT = 1_700_000_000_000L
    }
}
