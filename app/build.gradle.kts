import com.github.triplet.gradle.androidpublisher.ReleaseStatus
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.play.publisher)
}

android {
    namespace = "com.example.pulsetransit"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.homieapps.pulsetransit"
        minSdk = 24
        targetSdk = 37
        // Play rejects an upload whose versionCode is not higher than every earlier one, so CI
        // passes its run number plus BUILD_NUMBER_BASE (see .github/workflows/ci.yml). Local builds
        // stay at 1 and are never uploaded.
        versionCode =
            providers
                .gradleProperty("pulseVersionCode")
                .orElse(providers.environmentVariable("pulseVersionCode"))
                .map(String::toInt)
                .getOrElse(1)
        versionName = "2.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        // The app is translated into English and Norwegian Bokmål only. Without this filter the
        // libraries' translations ship too, and Play lists the app under ~80 languages.
        localeFilters += listOf("en", "nb")
    }

    signingConfigs {
        // Project-local debug key so every machine signs debug builds identically. Falls back to
        // the SDK's ~/.android/debug.keystore when the file is absent.
        val debugKeystore = file("debug.keystore")
        if (debugKeystore.exists()) {
            getByName("debug") {
                storeFile = debugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }

        val releaseFile =
            providers
                .gradleProperty("pulseReleaseFile")
                .orElse(providers.environmentVariable("pulseReleaseFile"))
                .filter { it.isNotBlank() }
                .orNull

        if (releaseFile != null) {
            create("release") {
                keyAlias =
                    providers
                        .gradleProperty("pulseReleaseKeyAlias")
                        .orElse(providers.environmentVariable("pulseReleaseKeyAlias"))
                        .getOrElse("")
                keyPassword =
                    providers
                        .gradleProperty("pulseReleaseKeyPassword")
                        .orElse(providers.environmentVariable("pulseReleaseKeyPassword"))
                        .getOrElse("")
                storeFile = file(releaseFile)
                storePassword =
                    providers
                        .gradleProperty("pulseReleaseStorePassword")
                        .orElse(providers.environmentVariable("pulseReleaseStorePassword"))
                        .getOrElse("")
            }
        }
    }

    buildTypes {
        release {
            manifestPlaceholders += mapOf()
            signingConfigs.findByName("release")?.let {
                signingConfig = it
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Crashlytics needs the mapping file to deobfuscate stack traces from a
            // minified build; without this upload every release report is unreadable.
            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = true
            }
            manifestPlaceholders["crashlyticsCollectionEnabled"] = true
        }
        debug {
            // Crashes from a development machine would drown out real ones. There is
            // also nothing to deobfuscate, since debug builds are not minified.
            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = false
            }
            manifestPlaceholders["crashlyticsCollectionEnabled"] = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // :shared uses kotlinx-datetime, built on java.time, which only exists natively from API 26.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        aidl = false
        buildConfig = false
        shaders = false
    }

    packaging {
        resources {
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
        }
    }
}

kotlin {
    jvmToolchain(17)
}

// Uploads to Google Play: `publishReleaseBundle` (AAB + release notes) and
// `publishReleaseListing` (store texts from src/main/play). Country availability, graphics and
// the policy declarations are managed in Play Console, not here.
play {
    providers
        .gradleProperty("pulsePlayCredentials")
        .orElse(providers.environmentVariable("pulsePlayCredentials"))
        .filter { it.isNotBlank() }
        .orNull
        ?.let { serviceAccountCredentials.set(file(it)) }
    track.set("internal")
    defaultToAppBundles.set(true)
    // An app that has never passed review only accepts draft releases; switch to "completed"
    // once the first production review is through.
    releaseStatus.set(
        providers
            .gradleProperty("pulsePlayReleaseStatus")
            .orElse(providers.environmentVariable("pulsePlayReleaseStatus"))
            .map { ReleaseStatus.valueOf(it.uppercase()) }
            .getOrElse(ReleaseStatus.DRAFT),
    )
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
}

dependencies {
    // Data, cache, settings and ViewModels, shared with the iOS app.
    implementation(project(":shared"))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Koin DI
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Kotlinx Serialization (navigation keys)
    implementation(libs.kotlinx.serialization.json)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Firebase (crash reporting). The BOM pins the individual artifact versions.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)

    // Arch Components
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    // Tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Instrumented tests
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Instrumented tests: jUnit rules and runners
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)

    // Navigation
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
