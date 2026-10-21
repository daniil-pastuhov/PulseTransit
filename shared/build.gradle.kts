plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    // Swift-friendly framework API: StateFlow as AsyncSequence, sealed and enum types as Swift enums.
    alias(libs.plugins.skie)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvmToolchain(17)

    compilerOptions {
        // Room's KMP setup needs an `expect object` (CacheDatabaseConstructor), which is still Beta.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "com.example.pulsetransit.shared"
        compileSdk = 37
        minSdk = 24
        // kotlinx-datetime is built on java.time, which only exists natively from API 26.
        enableCoreLibraryDesugaring = true

        withHostTest {}
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            // Same project-local debug key as :app, so the test APK signs identically on every
            // machine. Falls back to the SDK's ~/.android/debug.keystore when the file is absent.
            val debugKeystore = rootProject.file("app/debug.keystore")
            if (debugKeystore.exists()) {
                signing {
                    storeFile = debugKeystore
                    storePassword = "android"
                    keyAlias = "androiddebugkey"
                    keyPassword = "android"
                }
            }
        }
    }

    // The desktop app (`:desktopApp`), which runs the board for Compose Hot Reload.
    jvm("desktop")

    // Consumed by the iOS app as a static framework named `Shared`.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            binaryOption("bundleId", "com.example.pulsetransit.shared")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            api(libs.multiplatform.settings)
            implementation(libs.room.runtime)
            api(libs.koin.core)
            implementation(libs.koin.core.viewmodel)
            api(libs.androidx.lifecycle.viewmodel)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.koin.android)
            implementation(libs.androidx.core.ktx)
            implementation(libs.play.services.location)
            api(libs.play.app.update.ktx)
            implementation(libs.androidx.camera.core)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.crashkios.crashlytics)
            implementation(libs.sqlite.bundled)
        }
        getByName("desktopMain").dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqlite.bundled)
            implementation(libs.kotlinx.coroutines.swing)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.multiplatform.settings.test)
        }
        // The Room contract runs on each platform's real SQLite; see CacheDatabaseContract.
        iosTest {
            kotlin.srcDir("src/cacheContractTest/kotlin")
        }
        getByName("androidDeviceTest") {
            kotlin.srcDir("src/cacheContractTest/kotlin")
        }
        getByName("androidDeviceTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.androidx.test.runner)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    // Room's generated code, per target that has a database.
    listOf("kspAndroid", "kspDesktop", "kspIosArm64", "kspIosSimulatorArm64").forEach { add(it, libs.room.compiler) }
    "coreLibraryDesugaring"(libs.desugar.jdk.libs)
}

skie {
    features {
        // Ktor's types reach the framework through the repositories' constructors. Their name
        // clashes with NSObject are Ktor's to fix, and the iOS app never touches those members.
        group("io.ktor") {
            co.touchlab.skie.configuration.SuppressSkieWarning
                .NameCollision(true)
        }
    }
}

room {
    // Cache schema history, checked in so cache schema changes are reviewable.
    schemaDirectory("$projectDir/schemas")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    // The plain `detekt` task only looks in src/main by default, which a KMP module does not have.
    source.setFrom(
        "src/commonMain/kotlin",
        "src/androidMain/kotlin",
        "src/iosMain/kotlin",
        "src/desktopMain/kotlin",
        "src/commonTest/kotlin",
        "src/androidDeviceTest/kotlin",
        "src/iosTest/kotlin",
        "src/cacheContractTest/kotlin",
    )
}
