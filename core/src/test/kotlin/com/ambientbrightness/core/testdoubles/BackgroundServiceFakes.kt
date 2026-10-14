package com.ambientbrightness.core.testdoubles

import com.ambientbrightness.core.AutomaticBrightnessControl.AutomaticBrightnessController
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessSnapshot
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessStatus
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessWriteResult
import com.ambientbrightness.core.AutomaticBrightnessControl.BuiltInAdaptiveBrightnessState
import com.ambientbrightness.core.AutomaticBrightnessControl.SystemBrightnessClient
import com.ambientbrightness.core.BackgroundService.BackgroundNotificationContent
import com.ambientbrightness.core.BackgroundService.BatteryOptimizationClient
import com.ambientbrightness.core.BackgroundService.ControllerStateStore
import com.ambientbrightness.core.BackgroundService.ForegroundServiceHost
import com.ambientbrightness.core.BackgroundService.StartReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull

/**
 * Shared, ordered record of the calls the F5 fakes receive, so a test can assert the exact
 * start/stop ordering across different collaborators (for example that the brightness restore
 * happens after the controller stopped, but before the foreground service stops).
 */
internal class CallLog {
    private val calls = mutableListOf<String>()

    fun record(call: String) {
        calls += call
    }

    fun snapshot(): List<String> = calls.toList()
}

/** F2 controller double that records start/stop order and lets a test publish status updates. */
internal class RecordingAutomaticBrightnessController(
    private val log: CallLog? = null,
) : AutomaticBrightnessController {

    private val mutableStatus = MutableStateFlow(BrightnessStatus())
    private val mutableConflict = MutableStateFlow(BuiltInAdaptiveBrightnessState.UNKNOWN)

    override val status: StateFlow<BrightnessStatus> = mutableStatus
    override val adaptiveBrightnessConflict: StateFlow<BuiltInAdaptiveBrightnessState> = mutableConflict

    override var snapshot: BrightnessSnapshot? = null

    var startCalls: Int = 0
        private set

    var stopCalls: Int = 0
        private set

    override fun start() {
        startCalls++
        log?.record("controller.start")
        mutableStatus.value = mutableStatus.value.copy(isRunning = true)
    }

    override fun stop() {
        stopCalls++
        log?.record("controller.stop")
        mutableStatus.value = mutableStatus.value.copy(isRunning = false)
    }

    /** Publishes a status as if F2 had reported it. */
    fun publish(status: BrightnessStatus) {
        mutableStatus.value = status
    }
}

/** Recording [ForegroundServiceHost] that captures the notification content it was given. */
internal class RecordingForegroundServiceHost(
    private val log: CallLog? = null,
) : ForegroundServiceHost {

    val started: MutableList<BackgroundNotificationContent> = mutableListOf()
    val updated: MutableList<BackgroundNotificationContent> = mutableListOf()

    var stopForegroundCalls: Int = 0
        private set

    override fun startForeground(content: BackgroundNotificationContent) {
        started += content
        log?.record("host.startForeground")
    }

    override fun updateNotification(content: BackgroundNotificationContent) {
        updated += content
        log?.record("host.updateNotification")
    }

    override fun stopForeground() {
        stopForegroundCalls++
        log?.record("host.stopForeground")
    }
}

/** In-memory [ControllerStateStore]; the fields are plain properties so a test can preset them. */
internal class FakeControllerStateStore(
    override var desiredRunning: Boolean = false,
    override var lastStartReason: StartReason? = null,
) : ControllerStateStore

/**
 * [BatteryOptimizationClient] double with a settable exemption and call counters. A null [exempt]
 * withholds the first emission, so a subject sees no value until [setExempt] is called.
 */
internal class FakeBatteryOptimizationClient(
    exempt: Boolean? = false,
) : BatteryOptimizationClient {

    private val mutableExempt = MutableStateFlow(exempt)

    override val isExempt: Flow<Boolean> = mutableExempt.filterNotNull()

    var requestExemptionCalls: Int = 0
        private set

    var openSettingsCalls: Int = 0
        private set

    override fun requestExemption() {
        requestExemptionCalls++
    }

    override fun openSettings() {
        openSettingsCalls++
    }

    fun setExempt(exempt: Boolean?) {
        mutableExempt.value = exempt
    }
}

/** [SystemBrightnessClient] double that records writes and can be made to fail. */
internal class RecordingSystemBrightnessClient(
    private val log: CallLog? = null,
) : SystemBrightnessClient {

    var lastWrite: BrightnessValue? = null
        private set

    var writeAttempts: Int = 0
        private set

    var failureReason: String? = null

    override fun readCurrent(): Int? = lastWrite?.value

    override fun write(value: BrightnessValue): BrightnessWriteResult {
        writeAttempts++
        log?.record("brightness.write:${value.value}")
        val reason = failureReason
        return if (reason != null) {
            BrightnessWriteResult.Failed(reason)
        } else {
            lastWrite = value
            BrightnessWriteResult.Applied(value)
        }
    }
}
