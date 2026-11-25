import Foundation
import Shared
import Valhalla

/// Swift side of the Kotlin `NativeRouting` interface, over the valhalla-mobile package (Valhalla 3.9.1).
/// Only plain Strings cross the boundary; nothing here throws into Kotlin.
final class ValhallaBridge: NSObject, NativeRouting {
    private let lock = NSLock()
    private var engine: Valhalla?

    /// nil when the engine is ready; otherwise a message starting with "ERROR:".
    func open(configPath: String) -> String? {
        lock.lock()
        defer { lock.unlock() }
        engine?.close()
        engine = nil
        guard FileManager.default.fileExists(atPath: configPath) else {
            return "ERROR: config file not found: \(configPath)"
        }
        do {
            engine = try Valhalla(configPath: configPath)
            return nil
        } catch {
            return "ERROR: \(error.localizedDescription)"
        }
    }

    /// Valhalla's raw response JSON, or a message starting with "ERROR:".
    func route(requestJson: String) -> String {
        lock.lock()
        let current = engine
        lock.unlock()
        guard let current else { return "ERROR: engine not open" }
        do {
            return try current.route(rawRequest: requestJson)
        } catch {
            return "ERROR: \(error.localizedDescription)"
        }
    }
}
