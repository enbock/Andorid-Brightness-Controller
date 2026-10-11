// :infrastructure — Android adapters implementing the :core client interfaces.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ambientbrightness.infrastructure"
    compileSdk = 36

    defaultConfig {
        minSdk = 36
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}
