package com.ambientbrightness.core.SetupRequirementAggregator

import com.ambientbrightness.core.AmbientLightSensing.SensorAvailability
import com.ambientbrightness.core.AmbientLightSensing.SensorProfile
import com.ambientbrightness.core.AmbientLightSensing.SensorUnavailableReason
import com.ambientbrightness.core.AppSetupService.MissingReason
import com.ambientbrightness.core.AppSetupService.RequirementSeverity
import com.ambientbrightness.core.AppSetupService.RequirementState
import com.ambientbrightness.core.AppSetupService.SetupAction
import com.ambientbrightness.core.AppSetupService.SetupRequirement
import com.ambientbrightness.core.AppSetupService.SetupRequirementId
import com.ambientbrightness.core.AppSetupService.SetupStateStore
import com.ambientbrightness.core.AppSetupService.UnsupportedReason
import com.ambientbrightness.core.BackgroundService.BackgroundServiceState
import com.ambientbrightness.core.FakeClock
import com.ambientbrightness.core.Shared.Clock
import com.ambientbrightness.core.testdoubles.FakeAmbientLightService
import com.ambientbrightness.core.testdoubles.FakeBatteryOptimizationClient
import com.ambientbrightness.core.testdoubles.FakeBrightnessBackgroundService
import com.ambientbrightness.core.testdoubles.FakeNotificationPermissionChecker
import com.ambientbrightness.core.testdoubles.FakeSetupStateStore
import com.ambientbrightness.core.testdoubles.FakeWriteSettingsPermissionChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetupRequirementAggregatorTest {

    @Test
    fun `US3 - before any source reports, every requirement is unknown and start is blocked`() = runTest {
        val fixture = Fixture(backgroundScope)

        val status = fixture.aggregator.status.value

        assertEquals(
            listOf(
                SetupRequirementId.AmbientLightSensor,
                SetupRequirementId.WriteSettings,
                SetupRequirementId.Notifications,
                SetupRequirementId.BatteryOptimization,
            ),
            status.requirements.map { it.id },
        )
        status.requirements.forEach { requirement ->
            assertEquals(RequirementState.Unknown, requirement.state)
            assertEquals(SetupAction.None, requirement.action)
        }
        assertFalse(status.isReadyToStart)
        assertEquals(
            listOf(SetupRequirementId.AmbientLightSensor, SetupRequirementId.WriteSettings),
            status.blockers,
        )
        assertFalse(fixture.aggregator.isReadyToStart.value)
    }

    @Test
    fun `AC3_3 - satisfied blocking requirements enable start and clear the blockers`() = runTest {
        val fixture = Fixture(backgroundScope, availability = AVAILABLE)

        runCurrent()

        assertEquals(RequirementState.Satisfied, fixture.aggregator.requirement(SetupRequirementId.AmbientLightSensor).state)
        assertEquals(RequirementState.Satisfied, fixture.aggregator.requirement(SetupRequirementId.WriteSettings).state)
        assertEquals(RequirementState.Satisfied, fixture.aggregator.requirement(SetupRequirementId.Notifications).state)
        assertEquals(
            RequirementState.Satisfied,
            fixture.aggregator.requirement(SetupRequirementId.BatteryOptimization).state,
        )
        assertTrue(fixture.aggregator.status.value.isReadyToStart)
        assertTrue(fixture.aggregator.isReadyToStart.value)
        assertEquals(emptyList<SetupRequirementId>(), fixture.aggregator.status.value.blockers)
    }

    @Test
    fun `AC4_1 - a device without a sensor is unsupported and blocks start`() = runTest {
        val fixture = Fixture(
            backgroundScope,
            availability = SensorAvailability.Unavailable(SensorUnavailableReason.NoSensor),
        )

        runCurrent()

        val sensor = fixture.aggregator.requirement(SetupRequirementId.AmbientLightSensor)
        assertEquals(
            RequirementState.Unsupported(UnsupportedReason.NoAmbientLightSensor),
            sensor.state,
        )
        assertEquals(RequirementSeverity.Blocking, sensor.severity)
        assertEquals(SetupAction.None, sensor.action)
        assertFalse(fixture.aggregator.isReadyToStart.value)
        assertEquals(
            listOf(SetupRequirementId.AmbientLightSensor),
            fixture.aggregator.status.value.blockers,
        )
    }

    @Test
    fun `AC4_1 - a sensor that never reports is unsupported as well`() = runTest {
        val fixture = Fixture(
            backgroundScope,
            availability = SensorAvailability.Unavailable(SensorUnavailableReason.NotUsable),
        )

        runCurrent()

        assertEquals(
            RequirementState.Unsupported(UnsupportedReason.NoAmbientLightSensor),
            fixture.aggregator.requirement(SetupRequirementId.AmbientLightSensor).state,
        )
    }

    @Test
    fun `AC2_1 - a missing write settings grant is not granted and offers the settings screen`() = runTest {
        val fixture = Fixture(backgroundScope, writeSettingsGranted = false)

        runCurrent()

        val writeSettings = fixture.aggregator.requirement(SetupRequirementId.WriteSettings)
        assertEquals(RequirementState.Missing(MissingReason.NotGranted), writeSettings.state)
        assertEquals(RequirementSeverity.Blocking, writeSettings.severity)
        assertEquals(SetupAction.OpenWriteSettings, writeSettings.action)
        assertEquals(
            listOf(SetupRequirementId.WriteSettings),
            fixture.aggregator.status.value.blockers,
        )
        assertFalse(fixture.aggregator.isReadyToStart.value)
    }

    @Test
    fun `AC4_2 - a write settings grant revoked after being observed is reported as revoked`() = runTest {
        val fixture = Fixture(backgroundScope, writeSettingsGranted = true)
        runCurrent()
        assertEquals(
            RequirementState.Satisfied,
            fixture.aggregator.requirement(SetupRequirementId.WriteSettings).state,
        )

        fixture.writeSettings.setGranted(false)
        runCurrent()

        val writeSettings = fixture.aggregator.requirement(SetupRequirementId.WriteSettings)
        assertEquals(RequirementState.Missing(MissingReason.Revoked), writeSettings.state)
        assertEquals(SetupAction.OpenWriteSettings, writeSettings.action)
        assertFalse(fixture.aggregator.isReadyToStart.value)
    }

    @Test
    fun `AC4_3 - a denied notification permission is advisory and never blocks start`() = runTest {
        val fixture = Fixture(backgroundScope, notificationsGranted = false)

        runCurrent()

        val notifications = fixture.aggregator.requirement(SetupRequirementId.Notifications)
        assertEquals(RequirementState.Missing(MissingReason.NotGranted), notifications.state)
        assertEquals(RequirementSeverity.Advisory, notifications.severity)
        assertEquals(SetupAction.RequestNotificationPermission, notifications.action)
        assertTrue(fixture.aggregator.isReadyToStart.value)
        assertEquals(emptyList<SetupRequirementId>(), fixture.aggregator.status.value.blockers)
    }

    @Test
    fun `AC4_3 - a granted notification permission is satisfied and offers no action`() = runTest {
        val fixture = Fixture(backgroundScope, notificationsGranted = true)

        runCurrent()

        val notifications = fixture.aggregator.requirement(SetupRequirementId.Notifications)
        assertEquals(RequirementState.Satisfied, notifications.state)
        assertEquals(SetupAction.None, notifications.action)
    }

    @Test
    fun `AC2_3 - a non exempt app is advisory missing and offers the battery settings screen`() = runTest {
        val fixture = Fixture(backgroundScope, batteryExempt = false)

        runCurrent()

        val battery = fixture.aggregator.requirement(SetupRequirementId.BatteryOptimization)
        assertEquals(RequirementState.Missing(MissingReason.NotGranted), battery.state)
        assertEquals(RequirementSeverity.Advisory, battery.severity)
        assertEquals(SetupAction.OpenBatteryOptimizationSettings, battery.action)
        assertTrue(fixture.aggregator.isReadyToStart.value)
        assertEquals(emptyList<SetupRequirementId>(), fixture.aggregator.status.value.blockers)
    }

    @Test
    fun `AC2_3 - an exempt app is satisfied and offers no action`() = runTest {
        val fixture = Fixture(backgroundScope, batteryExempt = true)

        runCurrent()

        val battery = fixture.aggregator.requirement(SetupRequirementId.BatteryOptimization)
        assertEquals(RequirementState.Satisfied, battery.state)
        assertEquals(SetupAction.None, battery.action)
    }

    @Test
    fun `an advisory source that never emits stays unknown and never blocks start`() = runTest {
        val fixture = Fixture(backgroundScope, notificationsGranted = null, batteryExempt = null)

        runCurrent()

        assertEquals(
            RequirementState.Unknown,
            fixture.aggregator.requirement(SetupRequirementId.Notifications).state,
        )
        assertEquals(
            RequirementState.Unknown,
            fixture.aggregator.requirement(SetupRequirementId.BatteryOptimization).state,
        )
        assertEquals(
            SetupAction.None,
            fixture.aggregator.requirement(SetupRequirementId.Notifications).action,
        )
        assertEquals(
            SetupAction.None,
            fixture.aggregator.requirement(SetupRequirementId.BatteryOptimization).action,
        )
        assertTrue(fixture.aggregator.isReadyToStart.value)
        assertEquals(emptyList<SetupRequirementId>(), fixture.aggregator.status.value.blockers)
    }

    @Test
    fun `AC2_4 - a source change updates the status without rebuilding the aggregator`() = runTest {
        val fixture = Fixture(backgroundScope, availability = AVAILABLE, writeSettingsGranted = true)
        runCurrent()
        assertTrue(fixture.aggregator.isReadyToStart.value)

        fixture.sensor.setAvailability(SensorAvailability.Unavailable(SensorUnavailableReason.NoSensor))
        runCurrent()

        assertFalse(fixture.aggregator.isReadyToStart.value)
        assertEquals(
            listOf(SetupRequirementId.AmbientLightSensor),
            fixture.aggregator.status.value.blockers,
        )

        fixture.writeSettings.setGranted(false)
        runCurrent()

        assertEquals(
            listOf(SetupRequirementId.AmbientLightSensor, SetupRequirementId.WriteSettings),
            fixture.aggregator.status.value.blockers,
        )

        fixture.notifications.setGranted(false)
        runCurrent()

        assertEquals(
            RequirementState.Missing(MissingReason.NotGranted),
            fixture.aggregator.requirement(SetupRequirementId.Notifications).state,
        )
        assertEquals(
            listOf(SetupRequirementId.AmbientLightSensor, SetupRequirementId.WriteSettings),
            fixture.aggregator.status.value.blockers,
        )
    }

    @Test
    fun `refresh re-derives from the latest observed values and stays idempotent`() = runTest {
        val fixture = Fixture(backgroundScope, writeSettingsGranted = false)
        runCurrent()
        val derived = fixture.aggregator.status.value

        fixture.aggregator.refresh()

        assertEquals(derived, fixture.aggregator.status.value)
        assertEquals(derived.isReadyToStart, fixture.aggregator.isReadyToStart.value)
        assertEquals(listOf(SetupRequirementId.WriteSettings), fixture.aggregator.status.value.blockers)
    }

    @Test
    fun `AC1_3 - completing the setup persists the acknowledgement and exposes the flag`() = runTest {
        val store = FakeSetupStateStore()
        val clock = FakeClock(nowMillis = 1_700_000_000_000L)
        val fixture = Fixture(backgroundScope, store = store, clock = clock)

        assertFalse(fixture.aggregator.setupAcknowledged.value)

        fixture.aggregator.completeSetup()

        assertTrue(store.setupAcknowledged)
        assertEquals(1_700_000_000_000L, store.lastShownAtMillis)
        assertTrue(fixture.aggregator.setupAcknowledged.value)
    }

    @Test
    fun `AC1_3 - skipping the setup persists the acknowledgement as well`() = runTest {
        val store = FakeSetupStateStore()
        val clock = FakeClock(nowMillis = 1_700_000_000_000L)
        val fixture = Fixture(backgroundScope, store = store, clock = clock)

        fixture.aggregator.skipSetup()

        assertTrue(store.setupAcknowledged)
        assertEquals(
            1_700_000_000_000L,
            store.lastShownAtMillis,
            "skipping stores the same timestamp as completing",
        )
        assertTrue(fixture.aggregator.setupAcknowledged.value)
    }

    @Test
    fun `AC1_3 - a fresh aggregator over the same store reports the persisted acknowledgement`() =
        runTest {
            val store = FakeSetupStateStore()
            val first = Fixture(backgroundScope, store = store)
            first.aggregator.completeSetup()

            val second = Fixture(backgroundScope, store = store)
            runCurrent()

            assertTrue(
                second.aggregator.setupAcknowledged.value,
                "a new aggregator must seed the flag from the store",
            )
        }

    @Test
    fun `AC1_3 - acknowledgeSetup keeps persisting the flag and the timestamp`() = runTest {
        val store = FakeSetupStateStore()
        val clock = FakeClock(nowMillis = 1_700_000_000_000L)
        val fixture = Fixture(backgroundScope, store = store, clock = clock)

        fixture.aggregator.acknowledgeSetup()

        assertTrue(store.setupAcknowledged)
        assertEquals(1_700_000_000_000L, store.lastShownAtMillis)
        assertTrue(fixture.aggregator.setupAcknowledged.value)
    }

    @Test
    fun `AC3_1 - a missing blocking requirement is listed with its state and disables start`() =
        runTest {
            val fixture = Fixture(backgroundScope, writeSettingsGranted = false)
            runCurrent()

            val writeSettings =
                fixture.aggregator.requirement(SetupRequirementId.WriteSettings)
            assertEquals(RequirementState.Missing(MissingReason.NotGranted), writeSettings.state)
            assertEquals(
                4,
                fixture.aggregator.status.value.requirements.size,
                "every requirement must be listed separately",
            )
            assertFalse(fixture.aggregator.status.value.isReadyToStart)
        }

    @Test
    fun `AC3_2 - the blockers name exactly the requirement that blocks the start`() = runTest {
        val fixture = Fixture(backgroundScope, writeSettingsGranted = false)

        runCurrent()

        assertEquals(
            listOf(SetupRequirementId.WriteSettings),
            fixture.aggregator.status.value.blockers,
        )
        assertEquals(
            SetupAction.OpenWriteSettings,
            fixture.aggregator.requirement(SetupRequirementId.WriteSettings).action,
        )
    }

    @Test
    fun `AC3_3 - all requirements satisfied means the setup is complete and start is enabled`() =
        runTest {
            val fixture = Fixture(backgroundScope)

            runCurrent()

            assertTrue(
                fixture.aggregator.status.value.isReadyToStart,
                "all requirements satisfied must be ready to start",
            )
            assertEquals(emptyList<SetupRequirementId>(), fixture.aggregator.status.value.blockers)
            assertTrue(fixture.aggregator.isReadyToStart.value)
        }

    @Test
    fun `AC4_1 - no sensor reports unsupported and does not offer the start`() = runTest {
        val fixture = Fixture(
            backgroundScope,
            availability = SensorAvailability.Unavailable(SensorUnavailableReason.NoSensor),
        )

        runCurrent()

        assertEquals(
            RequirementState.Unsupported(UnsupportedReason.NoAmbientLightSensor),
            fixture.aggregator.requirement(SetupRequirementId.AmbientLightSensor).state,
        )
        assertFalse(fixture.aggregator.isReadyToStart.value)
        assertFalse(fixture.aggregator.status.value.isReadyToStart)
    }

    @Test
    fun `AC4_2 - an F5 failed controller is reported as revoked write settings and blocks start`() =
        runTest {
            val fixture = Fixture(backgroundScope, writeSettingsGranted = true)
            runCurrent()
            assertTrue(fixture.aggregator.isReadyToStart.value)

            fixture.backgroundService.setState(BackgroundServiceState.Failed("write failed"))
            runCurrent()

            val writeSettings =
                fixture.aggregator.requirement(SetupRequirementId.WriteSettings)
            assertEquals(RequirementState.Missing(MissingReason.Revoked), writeSettings.state)
            assertEquals(SetupAction.OpenWriteSettings, writeSettings.action)
            assertEquals(
                listOf(SetupRequirementId.WriteSettings),
                fixture.aggregator.status.value.blockers,
            )
            assertFalse(fixture.aggregator.isReadyToStart.value)
        }

    @Test
    fun `AC4_2 - the revoked report survives after the controller recovers`() = runTest {
        val fixture = Fixture(backgroundScope, writeSettingsGranted = true)
        runCurrent()

        fixture.backgroundService.setState(BackgroundServiceState.Failed("write failed"))
        runCurrent()
        fixture.writeSettings.setGranted(false)
        fixture.backgroundService.setState(BackgroundServiceState.Running())
        runCurrent()

        assertEquals(
            RequirementState.Missing(MissingReason.Revoked),
            fixture.aggregator.requirement(SetupRequirementId.WriteSettings).state,
            "a failure proved the revocation, so the later missing grant stays revoked",
        )
    }

    @Test
    fun `AC4_3 - the notification warning is observable while the controller runs without the grant`() =
        runTest {
            val fixture = Fixture(backgroundScope, notificationsGranted = false)
            runCurrent()
            assertFalse(
                fixture.aggregator.notificationWarning.value,
                "no warning before the controller starts",
            )

            fixture.backgroundService.setState(BackgroundServiceState.Running())
            runCurrent()

            assertTrue(fixture.aggregator.notificationWarning.value)
            assertTrue(
                fixture.aggregator.isReadyToStart.value,
                "the warning must never block the start",
            )
            assertEquals(emptyList<SetupRequirementId>(), fixture.aggregator.status.value.blockers)
        }

    @Test
    fun `AC4_3 - the notification warning stays off when the grant is present`() = runTest {
        val fixture = Fixture(backgroundScope, notificationsGranted = true)
        runCurrent()

        fixture.backgroundService.setState(BackgroundServiceState.Running())
        runCurrent()

        assertFalse(fixture.aggregator.notificationWarning.value)
    }

    private companion object {
        val AVAILABLE: SensorAvailability = SensorAvailability.Available(AVAILABLE_PROFILE)
    }
}

private val AVAILABLE_PROFILE = SensorProfile("Fake light sensor", maxRangeLux = 10_000f, minDelayMicros = 0)

private class Fixture(
    scope: CoroutineScope,
    availability: SensorAvailability = SensorAvailability.Available(AVAILABLE_PROFILE),
    writeSettingsGranted: Boolean = true,
    notificationsGranted: Boolean? = true,
    batteryExempt: Boolean? = true,
    store: SetupStateStore = FakeSetupStateStore(),
    clock: Clock = FakeClock(),
    val backgroundService: FakeBrightnessBackgroundService = FakeBrightnessBackgroundService(),
) {
    val sensor = FakeAmbientLightService(availability)
    val writeSettings = FakeWriteSettingsPermissionChecker(granted = writeSettingsGranted)
    val notifications = FakeNotificationPermissionChecker(initiallyGranted = notificationsGranted)
    val battery = FakeBatteryOptimizationClient(exempt = batteryExempt)

    val aggregator = SetupRequirementAggregator(
        ambientLightService = sensor,
        writeSettingsPermissionChecker = writeSettings,
        batteryOptimizationClient = battery,
        notificationPermissionChecker = notifications,
        stateStore = store,
        scope = scope,
        clock = clock,
        backgroundService = backgroundService,
    )
}

private fun SetupRequirementAggregator.requirement(id: SetupRequirementId): SetupRequirement =
    status.value.requirements.single { it.id == id }
