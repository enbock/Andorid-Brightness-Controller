package com.ambientbrightness.app

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.ambientbrightness.app.SetupFlow.Lifecycle.SetupLifecycleObserver
import com.ambientbrightness.core.AppSetupService.AppSetupService
import com.ambientbrightness.core.AppSetupService.SetupStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

/** The resume event must re-derive the setup status, so a grant made in a system screen is seen. */
class SetupLifecycleObserverTest {

    @Test
    fun `resuming refreshes the setup status`() {
        val setupService = RecordingSetupService()

        SetupLifecycleObserver(setupService).onResume(FakeLifecycleOwner())

        assertEquals(1, setupService.refreshCalls)
    }

    private class RecordingSetupService : AppSetupService {

        var refreshCalls: Int = 0
            private set

        override val status: StateFlow<SetupStatus> =
            MutableStateFlow(SetupStatus(emptyList(), false, emptyList()))

        override val isReadyToStart: StateFlow<Boolean> = MutableStateFlow(false)

        override fun refresh() {
            refreshCalls++
        }

        override fun acknowledgeSetup() = Unit
    }

    /** The observer never reads [LifecycleOwner.lifecycle], so a stub is enough here. */
    private class FakeLifecycleOwner : LifecycleOwner {
        override val lifecycle: Lifecycle
            get() = throw UnsupportedOperationException("the observer must not touch the lifecycle")
    }
}
