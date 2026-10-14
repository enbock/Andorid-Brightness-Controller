package com.ambientbrightness.core.AppSetupService

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards the F6 setup model: every state US1–US4 needs must be expressible, and the sealed
 * hierarchies must stay exhaustively branchable without an `else`.
 */
class AppSetupModelTest {

    private val severityById: Map<SetupRequirementId, RequirementSeverity> = mapOf(
        SetupRequirementId.AmbientLightSensor to RequirementSeverity.Blocking,
        SetupRequirementId.WriteSettings to RequirementSeverity.Blocking,
        SetupRequirementId.Notifications to RequirementSeverity.Advisory,
        SetupRequirementId.BatteryOptimization to RequirementSeverity.Advisory,
    )

    private val allRequirementIds: List<SetupRequirementId> = listOf(
        SetupRequirementId.AmbientLightSensor,
        SetupRequirementId.WriteSettings,
        SetupRequirementId.Notifications,
        SetupRequirementId.BatteryOptimization,
    )

    private val allStates: List<RequirementState> = listOf(
        RequirementState.Satisfied,
        RequirementState.Missing(MissingReason.NotGranted),
        RequirementState.Missing(MissingReason.Revoked),
        RequirementState.Unsupported(UnsupportedReason.NoAmbientLightSensor),
        RequirementState.Unknown,
    )

    private val allActions: List<SetupAction> = listOf(
        SetupAction.RequestNotificationPermission,
        SetupAction.OpenWriteSettings,
        SetupAction.OpenBatteryOptimizationSettings,
        SetupAction.None,
    )

    @Test
    fun `the sealed RequirementState hierarchy is exhaustive - every variant maps to a distinct label`() {
        assertEquals(
            listOf(
                "satisfied",
                "missing-NotGranted",
                "missing-Revoked",
                "unsupported-NoAmbientLightSensor",
                "unknown",
            ),
            allStates.map { label(it) },
        )
    }

    @Test
    fun `the sealed SetupAction hierarchy is exhaustive - every variant maps to a distinct label`() {
        assertEquals(
            listOf(
                "request-notification-permission",
                "open-write-settings",
                "open-battery-optimization-settings",
                "none",
            ),
            allActions.map { label(it) },
        )
        assertEquals(allActions.size, allActions.map { label(it) }.distinct().size)
    }

    @Test
    fun `US1 to US4 - every requirement id is modelled with its documented severity`() {
        assertEquals(allRequirementIds, SetupRequirementId.entries.toList())

        assertEquals(RequirementSeverity.Blocking, severityOf(SetupRequirementId.AmbientLightSensor))
        assertEquals(RequirementSeverity.Blocking, severityOf(SetupRequirementId.WriteSettings))
        assertEquals(RequirementSeverity.Advisory, severityOf(SetupRequirementId.Notifications))
        assertEquals(RequirementSeverity.Advisory, severityOf(SetupRequirementId.BatteryOptimization))
    }

    @Test
    fun `US2 AC2_1 and US4 AC4_2 - a revoked WRITE_SETTINGS carries the revoke reason`() {
        val requirement = requirement(
            SetupRequirementId.WriteSettings,
            RequirementState.Missing(MissingReason.Revoked),
        )

        assertEquals(RequirementSeverity.Blocking, requirement.severity)
        assertEquals("missing-Revoked", label(requirement.state))
        assertEquals(MissingReason.Revoked, (requirement.state as RequirementState.Missing).reason)
        assertNotEquals(
            RequirementState.Missing(MissingReason.NotGranted),
            RequirementState.Missing(MissingReason.Revoked),
        )
    }

    @Test
    fun `US4 AC4_1 - a device without an ambient light sensor is unsupported, not missing`() {
        val requirement = requirement(
            SetupRequirementId.AmbientLightSensor,
            RequirementState.Unsupported(UnsupportedReason.NoAmbientLightSensor),
        )

        assertEquals(RequirementSeverity.Blocking, requirement.severity)
        assertEquals("unsupported-NoAmbientLightSensor", label(requirement.state))
    }

    @Test
    fun `US3 AC3_1 and AC3_2 - only blocking requirements can become blockers`() {
        val status = statusOf(
            requirement(SetupRequirementId.AmbientLightSensor, RequirementState.Satisfied),
            requirement(SetupRequirementId.WriteSettings, RequirementState.Missing(MissingReason.NotGranted)),
            requirement(SetupRequirementId.Notifications, RequirementState.Missing(MissingReason.NotGranted)),
            requirement(SetupRequirementId.BatteryOptimization, RequirementState.Missing(MissingReason.NotGranted)),
        )

        assertEquals(listOf(SetupRequirementId.WriteSettings), status.blockers)
        assertFalse(status.isReadyToStart)
    }

    @Test
    fun `US3 AC3_3 - the status is ready when every blocking requirement is satisfied`() {
        val status = statusOf(
            requirement(SetupRequirementId.AmbientLightSensor, RequirementState.Satisfied),
            requirement(SetupRequirementId.WriteSettings, RequirementState.Satisfied),
            requirement(SetupRequirementId.Notifications, RequirementState.Missing(MissingReason.NotGranted)),
            requirement(SetupRequirementId.BatteryOptimization, RequirementState.Missing(MissingReason.NotGranted)),
        )

        assertTrue(status.blockers.isEmpty())
        assertTrue(status.isReadyToStart)
    }

    @Test
    fun `US2 AC2_2 - a missing notification permission requests the runtime permission and never blocks`() {
        val requirement = requirement(
            SetupRequirementId.Notifications,
            RequirementState.Missing(MissingReason.NotGranted),
            SetupAction.RequestNotificationPermission,
        )

        assertEquals(RequirementSeverity.Advisory, requirement.severity)
        assertEquals("request-notification-permission", label(requirement.action))
    }

    @Test
    fun `US3 - a requirement nothing is known about yet is not treated as satisfied`() {
        val status = statusOf(
            requirement(SetupRequirementId.AmbientLightSensor, RequirementState.Unknown),
            requirement(SetupRequirementId.WriteSettings, RequirementState.Satisfied),
            requirement(SetupRequirementId.Notifications, RequirementState.Satisfied),
            requirement(SetupRequirementId.BatteryOptimization, RequirementState.Satisfied),
        )

        assertEquals(listOf(SetupRequirementId.AmbientLightSensor), status.blockers)
        assertFalse(status.isReadyToStart)
    }

    @Test
    fun `SetupStatus is a value object - equality, copy and blockers are part of the value`() {
        val requirements = listOf(
            requirement(SetupRequirementId.AmbientLightSensor, RequirementState.Satisfied),
            requirement(SetupRequirementId.WriteSettings, RequirementState.Missing(MissingReason.NotGranted)),
        )
        val blocked = SetupStatus(
            requirements = requirements,
            isReadyToStart = false,
            blockers = listOf(SetupRequirementId.WriteSettings),
        )
        val same = statusOf(*requirements.toTypedArray())
        val ready = blocked.copy(isReadyToStart = true, blockers = emptyList())

        assertEquals(blocked, same)
        assertEquals(blocked.hashCode(), same.hashCode())
        assertEquals(listOf(SetupRequirementId.WriteSettings), blocked.blockers)
        assertTrue(ready.isReadyToStart)
        assertTrue(ready.blockers.isEmpty())
        assertNotEquals(blocked, ready)
    }

    @Test
    fun `the store contract exposes the acknowledgement and the last shown timestamp only`() {
        val store = InMemorySetupStateStore()

        assertNull(store.lastShownAtMillis)
        assertFalse(store.setupAcknowledged)

        store.setupAcknowledged = true
        store.lastShownAtMillis = 1_000L

        assertTrue(store.setupAcknowledged)
        assertEquals(1_000L, store.lastShownAtMillis)
    }

    private class InMemorySetupStateStore : SetupStateStore {
        override var setupAcknowledged: Boolean = false
        override var lastShownAtMillis: Long? = null
    }

    private fun requirement(
        id: SetupRequirementId,
        state: RequirementState,
        action: SetupAction = SetupAction.None,
    ): SetupRequirement = SetupRequirement(
        id = id,
        severity = severityOf(id),
        state = state,
        action = action,
    )

    private fun statusOf(vararg requirements: SetupRequirement): SetupStatus {
        val blockers = requirements
            .filter { it.severity == RequirementSeverity.Blocking && it.state != RequirementState.Satisfied }
            .map { it.id }

        return SetupStatus(
            requirements = requirements.toList(),
            isReadyToStart = blockers.isEmpty(),
            blockers = blockers,
        )
    }

    private fun severityOf(id: SetupRequirementId): RequirementSeverity =
        severityById.getValue(id)

    private fun label(state: RequirementState): String = when (state) {
        RequirementState.Satisfied -> "satisfied"
        is RequirementState.Missing -> "missing-${state.reason}"
        is RequirementState.Unsupported -> "unsupported-${state.reason}"
        RequirementState.Unknown -> "unknown"
    }

    private fun label(action: SetupAction): String = when (action) {
        SetupAction.RequestNotificationPermission -> "request-notification-permission"
        SetupAction.OpenWriteSettings -> "open-write-settings"
        SetupAction.OpenBatteryOptimizationSettings -> "open-battery-optimization-settings"
        SetupAction.None -> "none"
    }
}
