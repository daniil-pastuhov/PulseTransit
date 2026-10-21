plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

// The board's Compose UI and strings, shared by the Android app and the desktop app that runs it
// under Compose Hot Reload. Only JVM targets, so the code may still use java.* where it did on
// Android (dates, locales); Android-only pieces are expect/actual in androidMain and desktopMain.
kotlin {
    jvmToolchain(17)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "com.example.pulsetransit.ui"
        compileSdk = 37
        minSdk = 24
        enableCoreLibraryDesugaring = true
        // Compose resources (the strings) are packaged as Android assets.
        androidResources { enable = true }
        // Gives the module its Android lint tasks (`:ui:lint`), as in :shared.
        withHostTest {}
    }

    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            api(libs.compose.mp.runtime)
            api(libs.compose.mp.foundation)
            api(libs.compose.mp.ui)
            api(libs.compose.mp.material3)
            api(libs.compose.mp.resources)
            implementation(libs.compose.mp.material.icons.extended)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.play.app.update.ktx)
        }
    }
}

dependencies {
    "coreLibraryDesugaring"(libs.desugar.jdk.libs)
}

compose.resources {
    // :app's Settings screens use these strings too.
    publicResClass = true
    packageOfResClass = "com.example.pulsetransit.ui.resources"
    generateResClass = always
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    source.setFrom("src/commonMain/kotlin", "src/androidMain/kotlin", "src/desktopMain/kotlin")
}
