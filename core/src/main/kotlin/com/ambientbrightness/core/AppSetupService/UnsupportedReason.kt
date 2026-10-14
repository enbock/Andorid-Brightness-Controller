package com.ambientbrightness.core.AppSetupService

/**
 * Why a requirement is [RequirementState.Unsupported].
 */
enum class UnsupportedReason {

    /** The device has no usable ambient light sensor, so the controller cannot adapt (US4, AC4.1). */
    NoAmbientLightSensor,
}
