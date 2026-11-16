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

// The board gallery: renders the board offscreen (no window) to PNGs, per scenario, state and
// size, from fixtures (no network). See gallery/GalleryMain.kt and gallery/GalleryFixtures.kt.
//   ./gradlew :desktopApp:renderGallery -Pscenarios=simple,three -Psizes=tv,phone
// Scenarios: simple, long, two, three, five, states; sizes: phone, tabP, tabL, tv, tvdp (all by
// default). Optional: -Pout=<dir> (relative to the repository root; default
// desktopApp/build/gallery), -PfontScale=1.5, -Plocale=nb (default en).
val renderGallery by tasks.registering(JavaExec::class) {
    group = "application"
    description = "Renders the board offscreen to PNGs, per scenario, state and size."
    mainClass.set("com.example.pulsetransit.desktop.gallery.GalleryMainKt")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootDir
    jvmArgs("-Djava.awt.headless=true")
    val defaultOut = layout.buildDirectory.dir("gallery").map { it.asFile.absolutePath }
    val options = listOf("scenarios", "sizes", "fontScale", "locale").associateWith { providers.gradleProperty(it) }
    val out = providers.gradleProperty("out").orElse(defaultOut)
    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf("--out=${out.get()}") + options.mapNotNull { (name, value) -> value.orNull?.let { "--$name=$it" } }
        },
    )
    outputs.upToDateWhen { false }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
}
