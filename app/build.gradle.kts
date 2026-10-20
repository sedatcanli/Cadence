plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

import java.io.File

// Auto versioning from git
fun getVersionCode(): Int {
    try {
        val process = Runtime.getRuntime().exec("git rev-list --count HEAD")
        process.waitFor()
        val output = process.inputStream.bufferedReader().readText().trim()
        return output.toIntOrNull() ?: 1
    } catch (e: Exception) {
        return 1
    }
}

fun getVersionName(): String {
    try {
        val process = Runtime.getRuntime().exec("git describe --tags --always --dirty=-dirty")
        process.waitFor()
        val output = process.inputStream.bufferedReader().readText().trim()
        return if (output.startsWith("v")) output.substring(1) else output
    } catch (e: Exception) {
        return "0.1.0-dev"
    }
}

android {
    namespace = "com.cadence.speech"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cadence.speech"
        minSdk = 26
        targetSdk = 34
        versionCode = getVersionCode()
        versionName = getVersionName()
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        disable += "MissingTranslation"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.vosk.android)

    debugImplementation(libs.androidx.ui.tooling)
}