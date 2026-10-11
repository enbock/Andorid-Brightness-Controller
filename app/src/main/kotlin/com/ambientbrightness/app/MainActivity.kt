package com.ambientbrightness.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job

/**
 * Entry point of the application. The UI remains minimal while F1 collection follows this
 * activity's started/stopped lifecycle.
 */
class MainActivity : ComponentActivity() {

    private var collectionJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AmbientBrightnessApp() }
    }

    override fun onStart() {
        super.onStart()
        val component = (application as AmbientBrightnessApplication).component
        collectionJob = component.collector.collect(
            scope = lifecycleScope,
            onSample = {},
            onAvailability = {},
        )
    }

    override fun onStop() {
        stopAmbientLightCollection()
        super.onStop()
    }

    override fun onDestroy() {
        stopAmbientLightCollection()
        super.onDestroy()
    }

    private fun stopAmbientLightCollection() {
        collectionJob?.cancel()
        collectionJob = null
        (application as AmbientBrightnessApplication).component.stop()
    }
}

@Composable
private fun AmbientBrightnessApp() {
    MaterialTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
        ) { }
    }
}
