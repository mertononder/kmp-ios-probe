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

actual fun scratchFilePath(name: String): String =
    File(System.getProperty("java.io.tmpdir"), "kmp-ios-probe/$name").also { it.parentFile.mkdirs() }.absolutePath

actual fun writeScratchFile(name: String, text: String): String =
    File(scratchFilePath(name)).also { it.writeText(text) }.absolutePath
