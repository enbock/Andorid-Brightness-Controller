package com.ambientbrightness.app.StatusDashboard.View.Task

import androidx.annotation.StringRes
import com.ambientbrightness.app.R
import com.ambientbrightness.core.AmbientLightSensing.LightLevel.LightLevel

/**
 * Maps F1's [LightLevel] onto the string resource that labels it (US1, AC1.2).
 *
 * The mapping lives here, outside `DashboardUiStateMapper`, so the pure state mapping stays free of
 * resource ids and the labels can be translated independently (the View resolves the id in the
 * current locale).
 */
object LightLevelLabelResolver {

    /** The label resource for [level]; total, so every level always has readable text. */
    @StringRes
    fun resolve(level: LightLevel): Int = when (level) {
        LightLevel.DARK -> R.string.light_level_dark
        LightLevel.DIM -> R.string.light_level_dim
        LightLevel.INDOOR -> R.string.light_level_indoor
        LightLevel.BRIGHT -> R.string.light_level_bright
        LightLevel.FULL_SUN -> R.string.light_level_full_sun
        LightLevel.UNKNOWN -> R.string.light_level_unknown
    }
}
