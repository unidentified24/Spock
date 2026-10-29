package io.github.unidentified24.spock.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import io.github.unidentified24.spock.BuildConfig
import io.github.unidentified24.spock.data.update.*
import kotlinx.coroutines.launch

@Composable
fun UpdatePrompt() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { update = UpdateChecker.check(context) }
    val info = update ?: return
    AlertDialog(
        onDismissRequest = { if (!busy) update = null },
        title = { Text("Spock ${info.version}") },
        text = { Text(error ?: if (busy) "Downloading and verifying your update…" else
            "A newer Spock is ready. Installed: ${BuildConfig.VERSION_NAME} · Interface: ${BuildConfig.UI_VERSION}. Android will ask you to confirm installation.") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true; error = null
            scope.launch {
                runCatching { ApkUpdater.install(context, info) }
                    .onSuccess { update = null }.onFailure { error = it.message ?: "Update failed. Try again." }
                busy = false
            }
        }) { Text(if (busy) "Downloading…" else "Download and install") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { UpdateChecker.skipRelease(context, info); update = null }) { Text("Skip this version") }
            TextButton(enabled = !busy, onClick = { update = null }) { Text("Later") } },
    )
}
