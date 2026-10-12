package com.ambientbrightness.app.AmbientLightSensing.View

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Top-level view composable of the ambient brightness screen. */
@Composable
internal fun AmbientBrightnessApp() {
    MaterialTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
        ) { }
    }
}
