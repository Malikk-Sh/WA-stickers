plugins {
    id("com.android.application")
}

android {
    namespace = "com.malikksh.wastickers"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.malikksh.wastickers"
        minSdk = 24
        targetSdk = 35
        versionCode = 4
        versionName = "1.3"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
