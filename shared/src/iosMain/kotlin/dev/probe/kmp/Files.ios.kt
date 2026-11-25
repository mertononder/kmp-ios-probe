package dev.probe.kmp

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile

actual fun assetFilePath(path: String): String {
    val root = NSBundle.mainBundle.resourcePath ?: error("no resource path")
    return "$root/assets/$path"
}

private val probeReport = LinkedHashMap<String, String>()

actual fun reportProbe(key: String, value: String) {
    // One file per key as well, so results from separate app launches (one per screen) all survive.
    writeDocument("probe-$key.txt", value)
    probeReport[key] = value
    val json = probeReport.entries.joinToString(",", "{", "}") { (k, v) -> "\"$k\":\"${v.replace("\"", "'")}\"" }
    writeDocument("probe-report.json", json)
}

/** Writes a UTF-8 text file into the app's Documents folder (CI reads it from the simulator container). */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun writeDocument(name: String, text: String) {
    @Suppress("UNCHECKED_CAST")
    val dir = (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true) as List<String>).first()
    NSString.create(string = text).writeToFile("$dir/$name", atomically = true, encoding = NSUTF8StringEncoding, error = null)
}

actual fun scratchFilePath(name: String): String = platform.Foundation.NSTemporaryDirectory().trimEnd('/') + "/" + name

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual fun writeScratchFile(name: String, text: String): String {
    val path = scratchFilePath(name)
    NSString.create(string = text).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    return path
}
