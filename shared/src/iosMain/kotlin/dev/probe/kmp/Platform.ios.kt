package dev.probe.kmp

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.UIKit.UIDevice
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual fun assetBytes(path: String): ByteArray {
    val root = NSBundle.mainBundle.resourcePath ?: error("no resource path")
    val data = NSData.dataWithContentsOfFile("$root/assets/$path") ?: error("missing asset $path")
    val bytes = ByteArray(data.length.toInt())
    if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    return bytes
}

actual fun platformName(): String = UIDevice.currentDevice.let { "${it.systemName} ${it.systemVersion}" }
