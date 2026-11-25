import SwiftUI
import UIKit
import Shared

/// Hosts the Compose view controller exported by the shared Kotlin framework.
@main
struct ProbeApp: App {
    var body: some Scene {
        WindowGroup {
            SharedRootView()
                .ignoresSafeArea()
        }
    }
}

struct SharedRootView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(routing: ValhallaBridge())
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
