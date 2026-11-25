package dev.probe.kmp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Bytes of a file under bundle/assets (desktop classpath, iOS app bundle). */
expect fun assetBytes(path: String): ByteArray

expect fun platformName(): String

@OptIn(ExperimentalTextApi::class)
private val Inter: FontFamily by lazy {
    val bytes = assetBytes("fonts/inter.ttf")
    FontFamily(
        listOf(400, 800).map { w ->
            Font("inter-$w", bytes, FontWeight(w), FontStyle.Normal, FontVariation.Settings(FontVariation.weight(w)))
        }
    )
}

/** Screens selectable with `-probeScreen <id>` on iOS: "light" (default) or "dark". */
@Composable
fun ProbeApp(screen: String) {
    when (screen) {
        "map" -> return MapProbeScreen()
        "ride" -> return RideProbeScreen()
        "gate" -> return GateAScreen()
    }
    val dark = screen == "dark"
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("KMP iOS probe", fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
                Text("Running on ${platformName()} · screen=$screen", fontFamily = Inter, fontWeight = FontWeight.Normal)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF2F7D57))
                    Icon(Icons.Outlined.Explore, contentDescription = null)
                    Text("material3 + icons + bundled variable font")
                }
                Canvas(Modifier.fillMaxWidth().height(160.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    val path = Path().apply {
                        moveTo(0f, size.height * 0.8f)
                        cubicTo(size.width * 0.3f, 0f, size.width * 0.6f, size.height, size.width, size.height * 0.2f)
                    }
                    drawPath(path, Color(0xFFC85A32), style = Stroke(width = 8f))
                    drawCircle(Color(0xFF2B6CB0), radius = 18f, center = Offset(size.width * 0.5f, size.height * 0.5f))
                }
                Button(onClick = {}) { Text("Button") }
                Text("ğüşiöç İIıi 1.234,5", fontFamily = Inter)
            }
        }
    }
}
