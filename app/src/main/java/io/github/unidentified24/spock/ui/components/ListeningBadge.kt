package io.github.unidentified24.spock.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import io.github.unidentified24.spock.data.preferences.ListeningMeter
import io.github.unidentified24.spock.ui.theme.AppPalette
import kotlinx.coroutines.delay

@Composable
fun ListeningBadge(songId: Int) {
    val context = LocalContext.current
    var seconds by remember(songId) { mutableStateOf(0L) }
    LaunchedEffect(songId) {
        while (true) { seconds = ListeningMeter.songMilliseconds(context, songId) / 1000; delay(1000) }
    }
    Text("LISTENED · ${seconds / 60}m ${seconds % 60}s", color = AppPalette)
}
