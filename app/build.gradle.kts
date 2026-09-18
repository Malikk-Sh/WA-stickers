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
        versionCode = 13
        versionName = "1.5.7"
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
    // ffmpeg-kit-video 7.1.6 is published without this transitive dependency in its POM.
    // FFmpegKitConfig references com.arthenica.smartexception.java.Exceptions at runtime.
    implementation("com.arthenica:smart-exception-java:0.2.1")

    // Native libwebp decoder/animation encoder. Used for animated WebP files that FFmpeg
    // cannot decode (for example TikTok animated stickers), and to upscale their frames.
    implementation("com.aureusapps.android:webp-android:1.1.2")

    testImplementation("junit:junit:4.13.2")
}
