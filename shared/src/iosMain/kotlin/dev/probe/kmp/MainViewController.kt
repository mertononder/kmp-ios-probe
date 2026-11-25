package dev.probe.kmp

import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController

/** Swift: `MainViewControllerKt.MainViewController(routing: ValhallaBridge())`. */
@Suppress("FunctionName", "unused")
fun MainViewController(routing: NativeRouting): UIViewController {
    nativeRouting = routing
    val screen = NSUserDefaults.standardUserDefaults.stringForKey("probeScreen") ?: "light"
    return ComposeUIViewController { ProbeApp(screen) }
}
