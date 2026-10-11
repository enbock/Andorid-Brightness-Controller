package com.ambientbrightness.core

import com.ambientbrightness.core.testdoubles.FakeDeviceBrightnessRangeClient
import com.ambientbrightness.core.testdoubles.FakeSystemBrightnessClient
import com.ambientbrightness.core.testdoubles.FakeWriteSettingsPermissionChecker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrightnessContractsTest {

    @Test
    fun `brightness values reject values below one`() {
        assertEquals(1, BrightnessValue(1).value)
        assertThrows(IllegalArgumentException::class.java) { BrightnessValue(0) }
        assertThrows(IllegalArgumentException::class.java) { BrightnessValue(-1) }
    }

    @Test
    fun `brightness range accepts valid inclusive endpoints and rejects reversed bounds`() {
        val range = BrightnessRange(BrightnessValue(5), BrightnessValue(255))

        assertEquals(5, range.min.value)
        assertEquals(255, range.max.value)
        assertEquals(
            BrightnessRange(BrightnessValue(5), BrightnessValue(5)),
            BrightnessRange(BrightnessValue(5), BrightnessValue(5)),
        )
        assertThrows(IllegalArgumentException::class.java) {
            BrightnessRange(BrightnessValue(6), BrightnessValue(5))
        }
    }

    @Test
    fun `write outcomes and status expose applied suppressed and failure details`() {
        val value = BrightnessValue(42)
        val applied = BrightnessWriteResult.Applied(value)
        val suppressed = BrightnessWriteResult.Suppressed(value)
        val failed = BrightnessWriteResult.Failed("permission revoked")
        val status = BrightnessStatus(
            isRunning = true,
            appliedBrightness = value,
            lastLux = 12.5f,
            writeState = failed,
            writeError = failed.reason,
        )

        assertEquals(value, applied.value)
        assertEquals(value, suppressed.requested)
        assertEquals("permission revoked", failed.reason)
        assertTrue(status.isRunning)
        assertEquals(value, status.appliedBrightness)
        assertEquals(12.5f, status.lastLux)
        assertEquals(failed, status.writeState)
        assertEquals("permission revoked", status.writeError)
        assertEquals(
            listOf(
                BuiltInAdaptiveBrightnessState.ENABLED,
                BuiltInAdaptiveBrightnessState.DISABLED,
                BuiltInAdaptiveBrightnessState.UNKNOWN,
            ),
            BuiltInAdaptiveBrightnessState.entries,
        )
        assertNotNull(BrightnessSnapshot(value))
        assertNotNull(BrightnessContext(12.5f, BrightnessRange(BrightnessValue(1), value)))
    }

    @Test
    fun `fakes exercise separate range brightness and observable permission boundaries`() {
        val rangeClient =
            FakeDeviceBrightnessRangeClient(BrightnessRange(BrightnessValue(1), BrightnessValue(200)))
        val brightnessClient = FakeSystemBrightnessClient(current = 20)
        val permissionChecker = FakeWriteSettingsPermissionChecker(granted = false)

        assertEquals(1, rangeClient.readRange()?.min?.value)
        assertEquals(20, brightnessClient.readCurrent())
        assertNull(FakeDeviceBrightnessRangeClient(null).readRange())
        assertFalse(permissionChecker.hasWriteSettingsPermission.value)

        val result = brightnessClient.write(BrightnessValue(25))
        permissionChecker.setGranted(true)

        assertEquals(BrightnessWriteResult.Applied(BrightnessValue(25)), result)
        assertEquals(BrightnessValue(25), brightnessClient.lastWrite)
        assertTrue(permissionChecker.hasWriteSettingsPermission.value)
    }
}
