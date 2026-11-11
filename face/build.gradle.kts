plugins {
    id("com.android.application")
}

// BeyondStride watch face: Watch Face Format (no code). Its data comes from the BeyondStride watch app's complications.
android {
    namespace = "io.github.michealsizemore1.beyondstride.watchface"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.michealsizemore1.beyondstride.watchface"
        minSdk = 33
        targetSdk = 34
        versionCode = 1000 + (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0)
        versionName = "1.0 · build " + (System.getenv("GITHUB_RUN_NUMBER") ?: "dev")
    }

    signingConfigs {
        create("fixed") {
            storeFile = file("../app/top25.keystore")
            storePassword = "top25widget"
            keyAlias = "top25"
            keyPassword = "top25widget"
        }
    }
    // No code at all in a Watch Face Format APK.
    buildFeatures { buildConfig = false }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("fixed") }
        release { isMinifyEnabled = true; isShrinkResources = false; signingConfig = signingConfigs.getByName("fixed") }
    }
}
