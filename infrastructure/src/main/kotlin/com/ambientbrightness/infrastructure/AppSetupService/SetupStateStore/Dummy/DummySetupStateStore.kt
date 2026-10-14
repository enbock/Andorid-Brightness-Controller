package com.ambientbrightness.infrastructure.AppSetupService.SetupStateStore.Dummy

import com.ambientbrightness.core.AppSetupService.SetupStateStore

/** Process-local [SetupStateStore] for Dummy composition and tests; the values vanish with the process. */
class DummySetupStateStore(
    override var setupAcknowledged: Boolean = false,
    override var lastShownAtMillis: Long? = null,
) : SetupStateStore
