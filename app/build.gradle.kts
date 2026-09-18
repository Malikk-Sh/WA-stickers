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
        versionCode = 7
        versionName = "1.5.1"
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-video:7.1.6")
}
