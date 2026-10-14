package com.ambientbrightness.infrastructure

import com.ambientbrightness.core.BackgroundService.StartReason
import com.ambientbrightness.infrastructure.BackgroundService.ControllerStateStore.Android.startReasonOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidControllerStateStoreTest {

    @Test
    fun `a stored enum name reads back as its reason`() {
        assertEquals(StartReason.UserRequest, startReasonOrNull("UserRequest"))
        assertEquals(StartReason.SystemRestart, startReasonOrNull("SystemRestart"))
        assertEquals(StartReason.Boot, startReasonOrNull("Boot"))
    }

    @Test
    fun `an unknown stored name reads back as null instead of throwing`() {
        assertNull(startReasonOrNull("RemovedInAFutureVersion"))
    }
}
