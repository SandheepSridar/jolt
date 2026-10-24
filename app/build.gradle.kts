import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Version lives in version.properties (release.sh bumps it).
val versionProps = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }

// Release key lives OUTSIDE the repo: ~/.config/jolt/keystore.properties (override with
// JOLT_KEYSTORE_PROPS). Android only installs an update signed with the same key.
val keystorePropsFile = file(
    System.getenv("JOLT_KEYSTORE_PROPS") ?: "${System.getProperty("user.home")}/.config/jolt/keystore.properties",
)
val keystoreProps = Properties().apply { if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) } }

android {
    namespace = "com.jolt"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jolt"
        // Built for one phone: the Galaxy Z Flip 6 (64-bit ARM, shipped with Android 14).
        minSdk = 34
        targetSdk = 36
        versionCode = versionProps.getProperty("VERSION_CODE").toInt()
        versionName = versionProps.getProperty("VERSION_NAME")
        // In-app updates come from this repo's GitHub Releases.
        buildConfigField("String", "UPDATE_REPO", "\"SandheepSridar/jolt\"")
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // MediaPipe memory-maps the model, so it must be stored uncompressed.
    androidResources {
        noCompress += "tflite"
        // Drop the translations bundled in AndroidX; the app's own text is English only.
        localeFilters += "en"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    implementation(libs.mediapipe.tasks.vision)

    testImplementation(libs.junit)
}
