package dev.probe.kmp

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Rectangle
import java.awt.Robot
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.delay
import org.maplibre.compose.desktop.ProvideMapPresentationHost
import org.maplibre.compose.desktop.rememberAwtComposeMapPresentationHost

/**
 * Desktop window for the probe screens.
 *   gradlew :shared:run --args="map"              opens the map screen
 *   gradlew :shared:run --args="map shot.png"     captures the window after 12 s and exits
 */
fun main(args: Array<String>) {
    val screen = args.getOrNull(0) ?: "map"
    val shot = args.getOrNull(1)
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "probe - $screen",
            state = rememberWindowState(size = DpSize(393.dp, 852.dp)),
            alwaysOnTop = shot != null,
        ) {
            ProvideMapPresentationHost(rememberAwtComposeMapPresentationHost(window)) {
                when (screen) {
                    "map" -> MapProbeScreen()
                    "ride" -> RideProbeScreen()
                    "gate" -> GateAScreen()
                    else -> ProbeApp(screen)
                }
            }
            if (shot != null) {
                LaunchedEffect(Unit) {
                    delay(12_000)
                    val bounds = window.contentPane.let { pane ->
                        val origin = pane.locationOnScreen
                        Rectangle(origin.x, origin.y, pane.width, pane.height)
                    }
                    val image = Robot().createScreenCapture(bounds)
                    ImageIO.write(image, "png", File(shot).absoluteFile.also { it.parentFile?.mkdirs() })
                    println("shot ${File(shot).absolutePath}")
                    exitApplication()
                }
            }
        }
    }
}
