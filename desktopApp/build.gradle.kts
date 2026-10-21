import org.jetbrains.compose.reload.gradle.ComposeHotRun

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.hot.reload)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

// The board on the desktop, for developing the UI with Compose Hot Reload:
//   ./gradlew :desktopApp:hotRun --auto
// Not a product: it runs the shared :ui with stand-ins for the device services.
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":ui"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "com.example.pulsetransit.desktop.MainKt"
    }
}

tasks.withType<ComposeHotRun>().configureEach {
    mainClass.set("com.example.pulsetransit.desktop.MainKt")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
}
