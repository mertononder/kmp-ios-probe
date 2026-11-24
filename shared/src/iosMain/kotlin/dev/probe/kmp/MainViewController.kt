package dev.probe.kmp

import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController

@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController {
    val screen = NSUserDefaults.standardUserDefaults.stringForKey("probeScreen") ?: "light"
    return ComposeUIViewController { ProbeApp(screen) }
}
