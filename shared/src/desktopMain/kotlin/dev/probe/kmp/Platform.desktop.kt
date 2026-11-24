package dev.probe.kmp

actual fun assetBytes(path: String): ByteArray =
    (object {}.javaClass.classLoader.getResourceAsStream("assets/$path") ?: error("missing asset $path"))
        .use { it.readBytes() }

actual fun platformName(): String = "desktop JVM ${System.getProperty("java.version")}"
