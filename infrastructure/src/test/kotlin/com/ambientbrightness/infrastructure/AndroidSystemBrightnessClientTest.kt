package com.ambientbrightness.infrastructure

import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessWriteResult
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.SystemBrightnessClient.Android.readBrightnessSetting
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.SystemBrightnessClient.Android.writeBrightnessSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidSystemBrightnessClientTest {

    @Test
    fun `read returns value and turns expected platform read failures into null`() {
        assertEquals(63, readBrightnessSetting { 63 })
        assertNull(readBrightnessSetting { throw SecurityException("denied") })
        assertNull(readBrightnessSetting { throw IllegalArgumentException("invalid setting") })
        assertNull(
            readBrightnessSetting {
                throw android.provider.Settings.SettingNotFoundException("missing")
            },
        )
    }

    @Test
    fun `write succeeds only when permission and put both succeed`() {
        val value = BrightnessValue(72)
        var putCalled = false

        val result = writeBrightnessSetting(
            hasPermission = { true },
            putValue = {
                putCalled = true
                true
            },
            value = value,
        )

        assertEquals(BrightnessWriteResult.Applied(value), result)
        assertTrue(putCalled)
    }

    @Test
    fun `permission denial and false put result return diagnostic failures`() {
        var putCalled = false
        val permissionDenied = writeBrightnessSetting(
            hasPermission = { false },
            putValue = {
                putCalled = true
                true
            },
            value = BrightnessValue(72),
        )
        assertTrue(permissionDenied is BrightnessWriteResult.Failed)
        assertTrue((permissionDenied as BrightnessWriteResult.Failed).reason.isNotBlank())
        assertFalse(putCalled)

        val rejectedWrite = writeBrightnessSetting(
            hasPermission = { true },
            putValue = { false },
            value = BrightnessValue(72),
        )
        assertTrue(rejectedWrite is BrightnessWriteResult.Failed)
        assertTrue((rejectedWrite as BrightnessWriteResult.Failed).reason.isNotBlank())
    }

    @Test
    fun `expected platform exceptions return failures with nonempty reasons`() {
        for (exception in listOf(SecurityException(), IllegalArgumentException())) {
            val result = writeBrightnessSetting(
                hasPermission = { true },
                putValue = { throw exception },
                value = BrightnessValue(72),
            )

            assertTrue(result is BrightnessWriteResult.Failed)
            assertTrue((result as BrightnessWriteResult.Failed).reason.isNotBlank())
        }
    }
}
