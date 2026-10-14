package com.ambientbrightness.infrastructure.BackgroundService.ControllerStateStore.Dummy

import com.ambientbrightness.core.BackgroundService.ControllerStateStore
import com.ambientbrightness.core.BackgroundService.StartReason

/** Process-local [ControllerStateStore] for Dummy composition and tests; the values vanish with the process. */
class DummyControllerStateStore(
    override var desiredRunning: Boolean = false,
    override var lastStartReason: StartReason? = null,
) : ControllerStateStore
