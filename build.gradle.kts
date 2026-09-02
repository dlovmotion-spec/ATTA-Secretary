plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.attaproductions.secretary"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.attaproductions.secretary"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}

kotlin { jvmToolchain(17) }
