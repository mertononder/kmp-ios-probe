package dev.probe.kmp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_CREATE
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import androidx.sqlite.execSQL
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Routing engine implemented on the platform side (iOS: Swift `ValhallaBridge` over the valhalla-mobile package).
 * Only plain Strings cross the boundary.
 */
interface NativeRouting {
    /** null when the engine is ready, otherwise a message starting with "ERROR:". */
    fun open(configPath: String): String?

    /** Valhalla's raw JSON response, or a message starting with "ERROR:". */
    fun route(requestJson: String): String
}

/** Set by the platform entry point before the first composition. */
var nativeRouting: NativeRouting? = null

/** A writable scratch file path (temporary directory). */
expect fun scratchFilePath(name: String): String

/** Writes [text] to a scratch file and returns its path. */
expect fun writeScratchFile(name: String, text: String): String

/**
 * Bundled SQLite: plain table, FTS5 table, MATCH query, JSON1. Never throws.
 * The reported version shows which SQLite copy the linker kept (androidx bundled vs MapLibre's own).
 */
fun sqliteSmokeTest(dbPath: String): String {
    var connection: SQLiteConnection? = null
    return try {
        val conn = BundledSQLiteDriver().open(dbPath, SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE)
        connection = conn
        val version = conn.prepare("SELECT sqlite_version()").use { st -> st.step(); st.getText(0) }
        val fts5Opt = conn.prepare("SELECT sqlite_compileoption_used('ENABLE_FTS5')").use { st -> st.step(); st.getLong(0) }
        val threadsafe = conn.prepare("SELECT sqlite_compileoption_get(0)").use { st -> st.step(); st.getText(0) }
        val json = conn.prepare("SELECT json_extract('{\"a\":42}', '\$.a')").use { st -> st.step(); st.getLong(0) }
        conn.execSQL("DROP TABLE IF EXISTS ride_fts")
        conn.execSQL("DROP TABLE IF EXISTS ride")
        conn.execSQL("CREATE TABLE ride(id INTEGER PRIMARY KEY, title TEXT NOT NULL)")
        conn.prepare("INSERT INTO ride(title) VALUES (?)").use { st ->
            for (title in listOf("Zigana pass morning ride", "Bosphorus coast night ride")) {
                st.bindText(1, title)
                st.step()
                st.reset()
                st.clearBindings()
            }
        }
        val rows = conn.prepare("SELECT count(*) FROM ride").use { st -> st.step(); st.getLong(0) }
        conn.execSQL("CREATE VIRTUAL TABLE ride_fts USING fts5(title)")
        conn.execSQL("INSERT INTO ride_fts(rowid, title) SELECT id, title FROM ride")
        val hits = mutableListOf<String>()
        conn.prepare("SELECT title FROM ride_fts WHERE ride_fts MATCH ? ORDER BY rank").use { st ->
            st.bindText(1, "zigana")
            while (st.step()) hits += st.getText(0)
        }
        "OK v=$version fts5=$fts5Opt opt0=$threadsafe json=$json rows=$rows match=${hits.size}"
    } catch (t: Throwable) {
        "FAIL ${t::class.simpleName}: ${t.message}"
    } finally {
        try {
            connection?.close()
        } catch (_: Throwable) {
        }
    }
}

/** HTTPS GET with the platform's default Ktor engine. Never throws (except caller cancellation). */
suspend fun networkSmokeTest(): String {
    var client: HttpClient? = null
    return try {
        client = HttpClient {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 15_000
            }
        }
        val response = client.get("https://www.apple.com/library/test/success.html")
        "HTTP ${response.status.value} ${response.bodyAsText().take(40).replace('\n', ' ')}"
    } catch (t: Throwable) {
        currentCoroutineContext().ensureActive()
        "ERROR ${t::class.simpleName}: ${t.message}"
    } finally {
        client?.close()
    }
}

/** Opens the routing engine against an empty tile folder and asks for one route. */
fun routingSmokeTest(): String {
    val routing = nativeRouting ?: return "no native routing on this platform"
    val tileDir = scratchFilePath("valhalla-tiles-empty")
    val config = assetBytes("valhalla/probe-config.json").decodeToString().replace("__TILE_DIR__", tileDir)
    val configPath = writeScratchFile("valhalla-probe-config.json", config)
    val opened = routing.open(configPath)
    if (opened != null) return "open: $opened"
    val request = """{"locations":[{"lat":41.0082,"lon":28.9784},{"lat":41.0151,"lon":28.9795}],"costing":"motorcycle","units":"kilometers"}"""
    // Expected with no tiles: "ERROR: Valhalla error 171: No suitable edges near location"
    return "open ok; route: " + routing.route(request).take(160)
}

@Composable
fun GateAScreen() {
    var sqlite by remember { mutableStateOf("running") }
    var network by remember { mutableStateOf("running") }
    var routing by remember { mutableStateOf("running") }
    LaunchedEffect(Unit) {
        sqlite = withContext(Dispatchers.Default) { sqliteSmokeTest(scratchFilePath("gate-a.db")) }
        reportProbe("sqlite", sqlite)
        routing = withContext(Dispatchers.Default) {
            try {
                routingSmokeTest()
            } catch (t: Throwable) {
                "FAIL ${t::class.simpleName}: ${t.message}"
            }
        }
        reportProbe("routing", routing)
        network = networkSmokeTest()
        reportProbe("network", network)
        reportProbe("gateA", "done")
    }
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Native parts probe", fontSize = 26.sp)
                Text("SQLite (bundled): $sqlite", fontSize = 14.sp)
                Text("Routing (Valhalla): $routing", fontSize = 14.sp)
                Text("Network (Ktor): $network", fontSize = 14.sp)
            }
        }
    }
}
