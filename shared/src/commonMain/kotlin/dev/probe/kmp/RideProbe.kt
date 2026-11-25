package dev.probe.kmp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * A ride session spike: location updates that must keep arriving while the app is in the background,
 * and a short sound played every few fixes, also in the background. The platform writes its evidence
 * (counts per app state) to a file that CI reads after sending the app to the background.
 */
interface RideSession {
    fun start()
    fun summary(): String
}

expect fun createRideSession(): RideSession

@Composable
fun RideProbeScreen() {
    val session = remember { createRideSession() }
    var text by remember { mutableStateOf("starting") }
    LaunchedEffect(session) {
        session.start()
        while (true) {
            text = session.summary()
            delay(1000)
        }
    }
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.safeDrawingPadding().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Ride session probe", fontSize = 26.sp)
                Text(text, fontSize = 15.sp)
            }
        }
    }
}
