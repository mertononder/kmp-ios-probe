plugins {
    kotlin("multiplatform") version "2.4.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
    id("org.jetbrains.compose") version "1.12.1"
}

val composeVersion = "1.12.1"

kotlin {
    jvm("desktop")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
        // MapLibre Native (inside maplibre-compose) needs these system libraries in every linked binary,
        // including the Kotlin/Native test executable. The app target gets the same list in project.yml.
        target.binaries.all {
            linkerOpts("-lc++", "-lz", "-framework", "CoreFoundation", "-framework", "CoreGraphics",
                "-framework", "CoreText", "-framework", "Foundation", "-framework", "ImageIO",
                "-framework", "Metal", "-framework", "QuartzCore")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
            implementation("org.jetbrains.compose.foundation:foundation:$composeVersion")
            implementation("org.jetbrains.compose.ui:ui:$composeVersion")
            implementation("org.jetbrains.compose.animation:animation:$composeVersion")
            implementation("org.jetbrains.compose.material3:material3:1.9.0")
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            implementation("org.maplibre.compose:maplibre-compose:0.20.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        named("desktopMain") {
            resources.srcDir(rootProject.file("bundle"))
            dependencies {
                implementation(compose.desktop.currentOs)
                runtimeOnly("org.maplibre.compose:maplibre-compose-runtime-vulkan-windows-x64:0.20.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "dev.probe.kmp.DesktopMainKt"
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
    }
}
