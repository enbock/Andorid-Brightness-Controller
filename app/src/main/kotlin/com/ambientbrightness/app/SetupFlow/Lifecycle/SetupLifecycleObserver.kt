package com.ambientbrightness.app.SetupFlow.Lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.ambientbrightness.core.AppSetupService.AppSetupService

/**
 * Re-derives the setup status when the app resumes, so a permission granted in a system screen is
 * reflected without a restart (US2, AC2.4).
 *
 * There is no polling: the platform offers no reliable change callback for these grants, so the
 * resume event is the single trigger.
 */
class SetupLifecycleObserver(
    private val setupService: AppSetupService,
) : DefaultLifecycleObserver {

    override fun onResume(owner: LifecycleOwner) {
        setupService.refresh()
    }
}
