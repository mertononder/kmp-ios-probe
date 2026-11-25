package dev.probe.kmp

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionMixWithOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSDate
import platform.Foundation.NSURL
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState
import platform.darwin.NSObject

actual fun createRideSession(): RideSession = IosRideSession()

/** Counts everything per application state, so the evidence shows what happened while backgrounded. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class IosRideSession : RideSession {
    private val manager = CLLocationManager()
    private val locationDelegate = LocationDelegate()
    private val audioDelegate = AudioDelegate()
    private var player: AVAudioPlayer? = null

    private var authorization = -1
    private var fixesForeground = 0
    private var fixesBackground = 0
    private var lastLat = 0.0
    private var lastLon = 0.0
    private var playsRequested = 0
    private var playsStarted = 0
    private var playsStartedInBackground = 0
    private var playsFinished = 0
    private var audioSessionOk = false
    private val events = ArrayDeque<String>()
    private val startedAt = now()

    private fun now(): Double = NSDate().timeIntervalSince1970

    private fun appState(): String = when (UIApplication.sharedApplication.applicationState) {
        UIApplicationState.UIApplicationStateActive -> "active"
        UIApplicationState.UIApplicationStateInactive -> "inactive"
        UIApplicationState.UIApplicationStateBackground -> "background"
    }

    private fun event(text: String) {
        events.addLast("${(now() - startedAt).toInt()}s ${appState()} $text")
        while (events.size > 40) events.removeFirst()
    }

    override fun start() {
        val session = AVAudioSession.sharedInstance()
        audioSessionOk = session.setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionCategoryOptionMixWithOthers, null) &&
            session.setActive(true, null)
        event("audio session ok=$audioSessionOk")

        val url = NSURL.fileURLWithPath(assetFilePath("sounds/tone.wav"))
        player = AVAudioPlayer(contentsOfURL = url, error = null).also {
            it.delegate = audioDelegate
            it.prepareToPlay()
        }
        event("player ready=${player != null}")

        manager.delegate = locationDelegate
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = -1.0 // kCLDistanceFilterNone
        manager.pausesLocationUpdatesAutomatically = false
        manager.allowsBackgroundLocationUpdates = true
        manager.showsBackgroundLocationIndicator = true
        manager.requestWhenInUseAuthorization()
        manager.startUpdatingLocation()
        event("location updates requested")
        writeEvidence()
    }

    fun onLocation(location: CLLocation) {
        location.coordinate.useContents {
            lastLat = latitude
            lastLon = longitude
        }
        if (appState() == "background") fixesBackground++ else fixesForeground++
        val total = fixesForeground + fixesBackground
        if (total % 10 == 0) playTone()
        writeEvidence()
    }

    private fun playTone() {
        val p = player ?: return
        playsRequested++
        p.currentTime = 0.0
        val ok = p.play()
        if (ok) {
            playsStarted++
            if (appState() == "background") playsStartedInBackground++
        }
        event("tone play ok=$ok")
    }

    fun onAuthorization(status: Int) {
        authorization = status
        event("authorization=$status")
        writeEvidence()
    }

    fun onAudioFinished(success: Boolean) {
        if (success) playsFinished++
        event("tone finished ok=$success")
        writeEvidence()
    }

    override fun summary(): String =
        "state: ${appState()}\nauthorization: $authorization\n" +
            "fixes foreground: $fixesForeground\nfixes background: $fixesBackground\n" +
            "last: ${(lastLat * 1e5).toLong() / 1e5}, ${(lastLon * 1e5).toLong() / 1e5}\n" +
            "audio session: $audioSessionOk\ntone started: $playsStarted / $playsRequested " +
            "(background $playsStartedInBackground), finished $playsFinished"

    private fun writeEvidence() {
        val json = buildString {
            append("{")
            append("\"seconds\":").append((now() - startedAt).toInt()).append(',')
            append("\"state\":\"").append(appState()).append("\",")
            append("\"authorization\":").append(authorization).append(',')
            append("\"fixesForeground\":").append(fixesForeground).append(',')
            append("\"fixesBackground\":").append(fixesBackground).append(',')
            append("\"lastLat\":").append(lastLat).append(',')
            append("\"lastLon\":").append(lastLon).append(',')
            append("\"audioSessionOk\":").append(audioSessionOk).append(',')
            append("\"playsRequested\":").append(playsRequested).append(',')
            append("\"playsStarted\":").append(playsStarted).append(',')
            append("\"playsStartedInBackground\":").append(playsStartedInBackground).append(',')
            append("\"playsFinished\":").append(playsFinished).append(',')
            append("\"events\":[")
            append(events.joinToString(",") { "\"" + it.replace("\"", "'") + "\"" })
            append("]}")
        }
        writeDocument("ride-spike.json", json)
    }

    private inner class LocationDelegate : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            (didUpdateLocations.lastOrNull() as? CLLocation)?.let { onLocation(it) }
        }

        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            onAuthorization(manager.authorizationStatus)
        }
    }

    private inner class AudioDelegate : NSObject(), AVAudioPlayerDelegateProtocol {
        override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
            onAudioFinished(successfully)
        }
    }
}
