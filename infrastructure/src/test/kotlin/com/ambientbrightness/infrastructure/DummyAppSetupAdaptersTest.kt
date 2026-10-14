package com.ambientbrightness.infrastructure

import com.ambientbrightness.infrastructure.AppSetupService.NotificationPermissionChecker.Dummy.DummyNotificationPermissionChecker
import com.ambientbrightness.infrastructure.AppSetupService.SetupStateStore.Dummy.DummySetupStateStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DummyAppSetupAdaptersTest {

    @Test
    fun `the dummy notification checker exposes and toggles the grant`() = runTest {
        val checker = DummyNotificationPermissionChecker()

        assertFalse(checker.isGranted.first())

        checker.setPermissionGranted(true)

        assertTrue(checker.isGranted.first())
    }

    @Test
    fun `the dummy notification checker honours an initially granted permission`() = runTest {
        val checker = DummyNotificationPermissionChecker(initiallyGranted = true)

        assertTrue(checker.isGranted.first())
    }

    @Test
    fun `the dummy setup store starts absent and keeps both facts in memory`() {
        val store = DummySetupStateStore()

        assertFalse(store.setupAcknowledged)
        assertNull(store.lastShownAtMillis)

        store.setupAcknowledged = true
        store.lastShownAtMillis = SHOWN_AT

        assertTrue(store.setupAcknowledged)
        assertEquals(SHOWN_AT, store.lastShownAtMillis)
    }

    private companion object {
        const val SHOWN_AT = 1_700_000_000_000L
    }
}
