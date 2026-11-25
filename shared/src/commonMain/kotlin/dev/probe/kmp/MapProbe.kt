package dev.probe.kmp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.delay
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.DefaultMapRuntime
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

/** Absolute filesystem path of a bundled asset (desktop copies it out of the jar first). */
expect fun assetFilePath(path: String): String

/** Marks a screen as ready for its screenshot; CI waits for this before capturing. */
expect fun reportProbe(key: String, value: String)

private val routeCoordinates = listOf(
    28.9784 to 41.0082, 28.9850 to 41.0250, 29.0050 to 41.0420, 29.0270 to 41.0560, 29.0400 to 41.0780,
)

private fun routeGeoJson(): String =
    """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{"name":"route"},""" +
        """"geometry":{"type":"LineString","coordinates":[""" +
        routeCoordinates.joinToString(",") { (lon, lat) -> "[$lon,$lat]" } + "]}}]}"

private fun pointsGeoJson(): String {
    val (startLon, startLat) = routeCoordinates.first()
    val (endLon, endLat) = routeCoordinates.last()
    return """{"type":"FeatureCollection","features":[""" +
        """{"type":"Feature","properties":{"kind":"start"},"geometry":{"type":"Point","coordinates":[$startLon,$startLat]}},""" +
        """{"type":"Feature","properties":{"kind":"end"},"geometry":{"type":"Point","coordinates":[$endLon,$endLat]}}]}"""
}

/** A neutral offline style over a bundled Protomaps PMTiles extract (vector, no text: needs no glyphs). */
fun offlineStyleJson(pmtilesPath: String): String {
    val url = "pmtiles://file://" + if (pmtilesPath.startsWith("/")) pmtilesPath else "/$pmtilesPath"
    return """
    {"version":8,"name":"probe-offline",
     "sources":{"base":{"type":"vector","url":"$url","attribution":"© OpenStreetMap"}},
     "layers":[
      {"id":"bg","type":"background","paint":{"background-color":"#F5F2EB"}},
      {"id":"earth","type":"fill","source":"base","source-layer":"earth","paint":{"fill-color":"#EFEAE0"}},
      {"id":"landuse","type":"fill","source":"base","source-layer":"landuse","paint":{"fill-color":"#E3E8D6","fill-opacity":0.7}},
      {"id":"water","type":"fill","source":"base","source-layer":"water","paint":{"fill-color":"#BFD4E6"}},
      {"id":"roads-minor","type":"line","source":"base","source-layer":"roads","minzoom":11,
       "paint":{"line-color":"#FFFFFF","line-width":1.2}},
      {"id":"roads","type":"line","source":"base","source-layer":"roads",
       "filter":["in",["get","kind"],["literal",["highway","major_road"]]],
       "paint":{"line-color":"#D9C9A8","line-width":2.2}},
      {"id":"boundaries","type":"line","source":"base","source-layer":"boundaries",
       "paint":{"line-color":"#9C8F80","line-width":1,"line-dasharray":[3,2]}}
     ]}
    """.trimIndent()
}

/** Draws the marker bitmap in common code (no platform graphics API needed). */
private fun markerBitmap(): ImageBitmap {
    val px = 72
    val bitmap = ImageBitmap(px, px)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(px.toFloat(), px.toFloat())) {
        drawCircle(Color.White, radius = 30f, center = Offset(36f, 36f))
        drawCircle(Color(0xFFC85A32), radius = 24f, center = Offset(36f, 36f))
        drawCircle(Color.White, radius = 9f, center = Offset(36f, 36f), style = Stroke(width = 6f))
    }
    return bitmap
}

@Composable
fun MapProbeScreen() {
    val style = remember { BaseStyle.Json(offlineStyleJson(assetFilePath("maps/istanbul-z10.pmtiles"))) }
    var status by remember { mutableStateOf("map: waiting") }
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var snapshotStatus by remember { mutableStateOf("snapshot: waiting") }
    val marker = remember { markerBitmap() }

    val state = rememberMapState(
        baseStyle = style,
        initialCameraPosition = CameraPosition(center = Position(longitude = 29.01, latitude = 41.04), zoom = 10.6),
    ) {
        val route = rememberGeoJsonSource(GeoJsonData.JsonString(routeGeoJson()))
        val points = rememberGeoJsonSource(GeoJsonData.JsonString(pointsGeoJson()))
        LineLayer(id = "route-casing", source = route, color = const(Color.White), width = const(9.dp))
        LineLayer(id = "route", source = route, color = const(Color(0xFFC85A32)), width = const(5.dp))
        CircleLayer(id = "points", source = points, radius = const(7.dp), color = const(Color(0xFF2B6CB0)),
            strokeColor = const(Color.White), strokeWidth = const(2.dp))
        SymbolLayer(id = "end-marker", source = points, iconImage = image(marker), iconAllowOverlap = const(true))
    }

    LaunchedEffect(state) {
        // Programmatic "tap": query what is rendered under the route's start point.
        state.awaitViewport()
        var hits = 0
        var screen = "?"
        for (attempt in 0 until 40) {
            delay(500)
            val (lon, lat) = routeCoordinates.first()
            val offset = state.screenLocationFromPosition(Position(longitude = lon, latitude = lat)) ?: continue
            screen = "${offset.x.value.toInt()},${offset.y.value.toInt()}"
            hits = state.queryRenderedFeatures(offset, setOf("points", "route")).size
            if (hits > 0) break
        }
        status = "map: query at $screen found $hits feature(s)"
        reportProbe("mapQueryHits", hits.toString())
    }

    LaunchedEffect(Unit) {
        snapshotStatus = try {
            val snapshotter = DefaultMapRuntime.instance.createSnapshotter(style) {
                val route = rememberGeoJsonSource(GeoJsonData.JsonString(routeGeoJson()))
                LineLayer(id = "route", source = route, color = const(Color(0xFFC85A32)), width = const(4.dp))
            }
            try {
                val image = snapshotter.capture(DpSize(160.dp, 110.dp)) {
                    cameraPosition = CameraPosition(center = Position(longitude = 29.01, latitude = 41.04), zoom = 10.0)
                }
                snapshot = image
                "snapshot: ${image.width}x${image.height}"
            } finally {
                snapshotter.close()
            }
        } catch (t: Throwable) {
            "snapshot failed: ${t::class.simpleName}: ${t.message}"
        }
        reportProbe("snapshot", snapshotStatus)
    }

    MaterialTheme(colorScheme = lightColorScheme()) {
        Box(Modifier.fillMaxSize()) {
            MaplibreMap(modifier = Modifier.fillMaxSize(), state = state)
            // Compose overlay above the live map: a translucent panel, as map chrome typically uses.
            Column(
                Modifier.safeDrawingPadding().padding(16.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFAFAF8F5))
                    .border(1.dp, Color(0x1A181614), RoundedCornerShape(10.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Offline map probe", fontSize = 20.sp, color = Color(0xFF181614))
                Text(status, fontSize = 13.sp, color = Color(0xFF645E56))
                Text(snapshotStatus, fontSize = 13.sp, color = Color(0xFF645E56))
            }
            Row(
                Modifier.align(Alignment.BottomStart).safeDrawingPadding().padding(16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                val image = snapshot
                Box(
                    Modifier.size(160.dp, 110.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFE4DED2))
                        .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                ) {
                    if (image != null) Image(image, contentDescription = "snapshot", modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
