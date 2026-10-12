package com.ambientbrightness.infrastructure

import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessWriteResult
import com.ambientbrightness.core.AutomaticBrightnessControl.SystemBrightnessClient

/** Deterministic in-memory system-brightness client for tests and development. */
class DummySystemBrightnessClient(
    initiallyCurrentValue: Int? = null,
    private var writeFailure: ((BrightnessValue) -> String?)? = null,
) : SystemBrightnessClient {

    private var currentValue: Int? = initiallyCurrentValue

    override fun readCurrent(): Int? = currentValue

    override fun write(value: BrightnessValue): BrightnessWriteResult {
        val failureReason = writeFailure?.invoke(value)
        if (failureReason != null) {
            return BrightnessWriteResult.Failed(
                failureReason.ifBlank { "Injected system brightness write failure." },
            )
        }

        currentValue = value.value
        return BrightnessWriteResult.Applied(value)
    }

    fun setCurrentValue(value: Int?) {
        currentValue = value
    }

    fun setWriteFailure(failure: ((BrightnessValue) -> String?)?) {
        writeFailure = failure
    }
}
