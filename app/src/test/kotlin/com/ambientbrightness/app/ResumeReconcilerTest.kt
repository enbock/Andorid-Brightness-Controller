package com.ambientbrightness.app

import com.ambientbrightness.app.StatusDashboard.View.Task.ResumeReconciler
import com.ambientbrightness.app.testdoubles.FakeAppSetupService
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Coming back to the app must re-derive the setup state, because F6 is the one part the dashboard
 * cannot observe live (US2 AC2.4, US3 AC3.3).
 */
class ResumeReconcilerTest {

    @Test
    fun `a resume re-derives the setup state`() {
        val setupService = FakeAppSetupService()
        val reconciler = ResumeReconciler(setupService)

        reconciler.onResume()

        assertEquals(1, setupService.refreshCalls)
    }

    @Test
    fun `every resume re-derives it again instead of trusting the first result`() {
        val setupService = FakeAppSetupService()
        val reconciler = ResumeReconciler(setupService)

        reconciler.onResume()
        reconciler.onResume()
        reconciler.onResume()

        assertEquals(3, setupService.refreshCalls)
    }
}
