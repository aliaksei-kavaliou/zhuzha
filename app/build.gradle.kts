import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.inkfirst.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.inkfirst"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // Onyx pen SDK ships prebuilt native libs only for arm; strip the
        // rest from the release to keep APK small and avoid loader misses.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Plain JVM unit tests, for the pure logic that has no business needing a
    // device — TapRouting above all, whose whole reason for existing is that
    // the tap rules drifted apart once already.
    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }

    packaging {
        // Three of Onyx's transitive artifacts each ship their own copy of
        // libc++_shared.so. They're the same runtime; keep the first.
        jniLibs {
            // All ABIs, not just the two we ship — the merge task runs before
            // abiFilters strips the unused ones.
            pickFirsts += "**/libc++_shared.so"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.security.crypto)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Onyx pen SDK — direct-to-EPD raw drawing for Boox stylus.
    // hiddenapibypass is required because the pen SDK reaches into
    // internal Android graphics/display APIs on Android 11+; Application
    // must call HiddenApiBypass.addHiddenApiExemptions("") before use.
    implementation("com.onyx.android.sdk:onyxsdk-pen:1.5.4")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
}
