package com.ambientbrightness.app

import com.ambientbrightness.app.StatusDashboard.View.Task.SetupTextResolver
import com.ambientbrightness.core.AppSetupService.MissingReason
import com.ambientbrightness.core.AppSetupService.RequirementState
import com.ambientbrightness.core.AppSetupService.SetupRequirementId
import com.ambientbrightness.core.AppSetupService.UnsupportedReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dashboard must be able to name every requirement and every state, so the mapping has to be
 * total and each lookup has to return a real resource (US3 AC3.2, US4).
 */
class SetupTextResolverTest {

    @Test
    fun `every requirement has its own name`() {
        val labels = SetupRequirementId.entries.map(SetupTextResolver::requirementLabel)

        assertEquals(SetupRequirementId.entries.size, labels.size)
        assertTrue("every requirement needs a real label", labels.none { it == 0 })
        assertEquals("two requirements must not share a name", labels.size, labels.distinct().size)
    }

    @Test
    fun `every requirement state has a real label`() {
        val states: List<RequirementState> = listOf(
            RequirementState.Satisfied,
            RequirementState.Unknown,
            RequirementState.Missing(MissingReason.NotGranted),
            RequirementState.Missing(MissingReason.Revoked),
            RequirementState.Unsupported(UnsupportedReason.NoAmbientLightSensor),
        )

        val labels = states.map(SetupTextResolver::stateLabel)

        assertTrue("every state needs a real label", labels.none { it == 0 })
        assertEquals("every state needs a distinct label", states.size, labels.distinct().size)
    }

    @Test
    fun `a revoked permission is not described as one that was never granted`() {
        assertEquals(
            R.string.setup_state_missing_revoked,
            SetupTextResolver.stateLabel(RequirementState.Missing(MissingReason.Revoked)),
        )
        assertEquals(
            R.string.setup_state_missing_not_granted,
            SetupTextResolver.stateLabel(RequirementState.Missing(MissingReason.NotGranted)),
        )
    }

    @Test
    fun `an unsupported requirement says the device cannot do it`() {
        assertEquals(
            R.string.setup_state_unsupported,
            SetupTextResolver.stateLabel(
                RequirementState.Unsupported(UnsupportedReason.NoAmbientLightSensor),
            ),
        )
    }
}
