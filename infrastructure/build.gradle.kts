// :infrastructure — Android adapters implementing the :core client interfaces.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ambientbrightness.infrastructure"
    compileSdk = 36

    defaultConfig {
        minSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core"))
    // StateFlow appears in the public ScreenStateClient API, so it must be exposed transitively.
    api(libs.kotlinx.coroutines.core)

    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
