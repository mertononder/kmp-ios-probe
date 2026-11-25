package dev.probe.kmp

import java.io.File

actual fun assetFilePath(path: String): String {
    val target = File(System.getProperty("java.io.tmpdir"), "kmp-ios-probe/assets/$path")
    if (!target.isFile) {
        target.parentFile.mkdirs()
        target.writeBytes(assetBytes(path))
    }
    return target.absolutePath.replace('\\', '/')
}

actual fun reportProbe(key: String, value: String) {
    println("probe $key=$value")
}

actual fun createRideSession(): RideSession = object : RideSession {
    override fun start() {}
    override fun summary(): String = "desktop preview: no location or background audio here"
}
