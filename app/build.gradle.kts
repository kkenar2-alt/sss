plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mentis.sahasiparis"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mentis.sahasiparis"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }
}
