package io.github.unidentified24.spock.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import io.github.unidentified24.spock.data.preferences.CROSSFADE_MAX_MS
import io.github.unidentified24.spock.data.preferences.StreamQuality
import io.github.unidentified24.spock.data.preferences.getCellularQuality
import io.github.unidentified24.spock.data.preferences.getCrossfadeMs
import io.github.unidentified24.spock.data.preferences.setCrossfadeMs
import io.github.unidentified24.spock.data.preferences.getDownloadQuality
import io.github.unidentified24.spock.data.preferences.getLyricTranslateLang
import io.github.unidentified24.spock.data.preferences.isVideoFallbackEnabled
import io.github.unidentified24.spock.data.preferences.getWifiQuality
import io.github.unidentified24.spock.data.preferences.setCellularQuality
import io.github.unidentified24.spock.data.preferences.setDownloadQuality
import io.github.unidentified24.spock.data.preferences.setLyricTranslateLang
import io.github.unidentified24.spock.data.preferences.setVideoFallbackEnabled
import io.github.unidentified24.spock.data.preferences.setWifiQuality
import io.github.unidentified24.spock.data.preferences.MusicSource
import io.github.unidentified24.spock.data.preferences.getPrimaryMusicSource
import io.github.unidentified24.spock.data.preferences.isYoutubeLoggedIn
import io.github.unidentified24.spock.data.preferences.setPrimaryMusicSource
import io.github.unidentified24.spock.ui.theme.AppBackground
import io.github.unidentified24.spock.ui.theme.AppPalette
import java.util.Locale

private val translateLanguages by lazy {
    Locale.getISOLanguages()
        .map { it to Locale.forLanguageTag(it).getDisplayLanguage(Locale.getDefault()) }
        .filter { it.second.isNotBlank() }
        .sortedBy { it.second }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current

    var wifiQ by remember { mutableStateOf(getWifiQuality(context)) }
    var cellQ by remember { mutableStateOf(getCellularQuality(context)) }
    var dlQ by remember { mutableStateOf(getDownloadQuality(context)) }
    var crossfadeMs by remember { mutableStateOf(getCrossfadeMs(context).toFloat()) }
    var videoFallback by remember { mutableStateOf(isVideoFallbackEnabled(context)) }
    var selectedLanguage by remember { mutableStateOf(getLyricTranslateLang(context)) }
    var languageQuery by remember { mutableStateOf("") }
    // Read fresh each composition so returning from the Deezer login reflects it.
    val deezerConnected = io.github.unidentified24.spock.data.preferences.getDeezerArl(context) != null
    val deezerTier = io.github.unidentified24.spock.data.preferences.getDeezerTier(context)
    val youtubeConnected = isYoutubeLoggedIn(context)
    var primarySource by remember {
        mutableStateOf(getPrimaryMusicSource(context) ?: MusicSource.YOUTUBE_MUSIC)
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(26.dp)
                            .clickable { navController.popBackStack() }
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = AppBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            io.github.unidentified24.spock.ui.components.SpockPageTitle("Make it yours.", "Your sound. Your sources. Your preferences.")
            SectionTitle("Appearance")
            Text("Spock ${io.github.unidentified24.spock.BuildConfig.VERSION_NAME} · Interface ${io.github.unidentified24.spock.BuildConfig.UI_VERSION}", color = AppPalette)
            io.github.unidentified24.spock.ui.theme.SpockLook.entries.forEach { look ->
                androidx.compose.material3.TextButton(onClick = {
                    io.github.unidentified24.spock.ui.theme.currentLook = look
                    context.getSharedPreferences("appearance", 0).edit().putString("theme", look.name).apply()
                }) { Text((if (io.github.unidentified24.spock.ui.theme.currentLook == look) "●  " else "○  ") + look.label, color = look.accent) }
            }
            SectionTitle("Audio quality")
            QualityPicker(
                title = "Streaming over Wi-Fi",
                selected = wifiQ,
            ) { wifiQ = it; setWifiQuality(context, it) }

            QualityPicker(
                title = "Streaming over cellular",
                selected = cellQ,
            ) { cellQ = it; setCellularQuality(context, it) }

            QualityPicker(
                title = "Download quality",
                selected = dlQ,
            ) { dlQ = it; setDownloadQuality(context, it) }

            // Lossless FLAC comes from the Tidal community backend (no login) and, if
            // connected, Deezer HiFi. Falls back to best-quality YouTube on a miss.
            val losslessNote = if (deezerConnected) {
                "Lossless: Tidal (free) + Deezer${if (deezerTier.isNotBlank()) " $deezerTier" else ""} — real FLAC"
            } else {
                "Lossless: Tidal community FLAC (no login needed) — or add Deezer HiFi below"
            }
            Text(
                losslessNote,
                color = Color(0xFFB9B4C3),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )

            Spacer(Modifier.height(12.dp))
            SectionTitle("Matching")
            SettingsSwitchRow(
                title = "Allow video fallback",
                subtitle = "Use regular YouTube videos only after Music song results fail",
                checked = videoFallback,
            ) {
                videoFallback = it
                setVideoFallbackEnabled(context, it)
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Lyrics")
            val languageMatches = remember(languageQuery) {
                val query = languageQuery.trim()
                if (query.isBlank()) {
                    emptyList()
                } else {
                    translateLanguages.filter { (code, name) ->
                        code.contains(query, true) || name.contains(query, true)
                    }.sortedWith(
                        compareByDescending<Pair<String, String>> {
                            it.first.equals(query, true) || it.second.equals(query, true)
                        }.thenBy { it.second }
                    ).take(8)
                }
            }
            OutlinedTextField(
                value = languageQuery,
                onValueChange = {
                    languageQuery = it
                    if (it.isBlank()) {
                        selectedLanguage = ""
                        setLyricTranslateLang(context, "")
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                singleLine = true,
                label = { Text("Translate lyrics to", color = Color(0xFFB9B4C3)) },
                placeholder = { Text("Search languages", color = Color(0xFF72728A)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = AppPalette,
                    focusedBorderColor = AppPalette,
                    unfocusedBorderColor = Color(0xFF3A3A46),
                    focusedLabelColor = AppPalette,
                    unfocusedLabelColor = Color(0xFFB9B4C3),
                    focusedContainerColor = AppBackground,
                    unfocusedContainerColor = AppBackground,
                ),
            )
            languageMatches.forEach { (code, name) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(io.github.unidentified24.spock.ui.theme.GridBackground)
                        .clickable {
                            selectedLanguage = code
                            setLyricTranslateLang(context, code)
                            languageQuery = ""
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text(code, color = AppPalette, fontSize = 13.sp)
                }
            }
            Text(
                text = if (selectedLanguage.isBlank()) {
                    "Using phone language"
                } else {
                    "Selected: ${translateLanguages.firstOrNull { it.first == selectedLanguage }?.second ?: selectedLanguage} ($selectedLanguage)"
                },
                color = Color(0xFFB9B4C3),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )

            Spacer(Modifier.height(12.dp))
            SectionTitle("Crossfade")
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Crossfade", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    if (crossfadeMs <= 0f) "Off" else "${(crossfadeMs / 1000f).let { String.format("%.0f", it) }}s",
                    color = if (crossfadeMs <= 0f) Color(0xFFB9B4C3) else AppPalette,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                "Blend the end of a song into the start of the next",
                color = Color(0xFFB9B4C3),
                fontSize = 13.sp,
            )
            Slider(
                value = crossfadeMs,
                onValueChange = { crossfadeMs = it },
                onValueChangeFinished = { setCrossfadeMs(context, crossfadeMs.toInt()) },
                valueRange = 0f..CROSSFADE_MAX_MS.toFloat(),
                steps = (CROSSFADE_MAX_MS / 1000) - 1, // 1-second stops
                colors = SliderDefaults.colors(
                    thumbColor = AppPalette,
                    activeTrackColor = AppPalette,
                    inactiveTrackColor = Color(0xFF333333),
                ),
            )
            Spacer(Modifier.height(12.dp))
            SectionTitle("Music source")
            SourceSettingRow(
                title = "YouTube Music",
                subtitle = if (youtubeConnected) "Connected — explicit and age-restricted playback enabled" else "Login required for explicit and age-restricted songs",
                selected = primarySource == MusicSource.YOUTUBE_MUSIC,
            ) {
                if (youtubeConnected) {
                    primarySource = MusicSource.YOUTUBE_MUSIC
                    setPrimaryMusicSource(context, primarySource)
                } else {
                    navController.navigate(io.github.unidentified24.spock.ui.navigation.Routes.YouTubeLogin.route)
                }
            }
            SourceSettingRow(
                title = "Deezer",
                subtitle = if (deezerConnected) "Connected${if (deezerTier.isNotBlank()) " — $deezerTier" else ""}" else "Not connected",
                selected = primarySource == MusicSource.DEEZER,
            ) {
                if (deezerConnected) {
                    primarySource = MusicSource.DEEZER
                    setPrimaryMusicSource(context, primarySource)
                    io.github.unidentified24.spock.data.preferences.setDeezerEnabled(context, true)
                } else {
                    navController.navigate(io.github.unidentified24.spock.ui.navigation.Routes.DeezerLogin.route)
                }
            }
            Text(
                text = if (youtubeConnected) "Reconnect YouTube Music" else "Log in to YouTube Music",
                color = AppPalette,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { navController.navigate(io.github.unidentified24.spock.ui.navigation.Routes.YouTubeLogin.route) }
                    .padding(vertical = 12.dp),
            )
            Text(
                text = if (deezerConnected) "Reconnect / switch account" else "Log in to Deezer",
                color = AppPalette,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable {
                        navController.navigate(io.github.unidentified24.spock.ui.navigation.Routes.DeezerLogin.route)
                    }
                    .padding(vertical = 14.dp),
            )
            if (deezerConnected) {
                Text(
                    text = "Disconnect Deezer",
                    color = Color(0xFFE57373),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            io.github.unidentified24.spock.data.preferences.clearDeezer(context)
                            navController.navigate(io.github.unidentified24.spock.ui.navigation.Routes.Settings.route) {
                                popUpTo(io.github.unidentified24.spock.ui.navigation.Routes.Settings.route) { inclusive = true }
                            }
                        }
                        .padding(vertical = 12.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("SpotiFLAC (experimental)")
            val sfConnected = io.github.unidentified24.spock.data.preferences.hasSpotiflacSession(context)
            Text(
                text = "Gives access to SpotiFLAC's own FLAC servers. You solve one quick check yourself — no auto-bypass. Experimental: may fail if their servers change.",
                color = Color(0xFFB9B4C3),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 6.dp),
            )
            Text(
                text = if (sfConnected) "Re-verify SpotiFLAC" else "Set up SpotiFLAC verification",
                color = AppPalette,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable {
                        navController.navigate(io.github.unidentified24.spock.ui.navigation.Routes.SpotiflacVerify.route)
                    }
                    .padding(vertical = 14.dp),
            )
            if (sfConnected) {
                Text(
                    text = "Session active ✓",
                    color = Color(0xFF00C7B7),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Soulseek (experimental)")
            var soulseekOn by remember { mutableStateOf(io.github.unidentified24.spock.data.preferences.isSoulseekEnabled(context)) }
            SettingsSwitchRow(
                title = "Soulseek lossless fallback",
                subtitle = "When the FLAC servers miss, fetch FLACs from Soulseek (peer-to-peer). Uses data and shows your IP to peers.",
                checked = soulseekOn,
            ) {
                soulseekOn = it
                io.github.unidentified24.spock.data.preferences.setSoulseekEnabled(context, it)
                // Off: leave the network now and close the listening port.
                if (!it) Thread { io.github.unidentified24.spock.soulseek.SoulseekClient.disconnect() }.start()
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("Account")
            Text(
                text = "Listening Stats",
                color = AppPalette,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable {
                        navController.navigate(io.github.unidentified24.spock.ui.navigation.Routes.Stats.route)
                    }
                    .padding(vertical = 12.dp),
            )
            Text(
                text = "Log out",
                color = Color(0xFFE57373),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable {
                        io.github.unidentified24.spock.data.api.SpotifySession.setSpDc(context, "")
                        io.github.unidentified24.spock.data.api.Api.HomeCache.clear()
                        navController.navigate(io.github.unidentified24.spock.ui.navigation.Routes.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                    .padding(vertical = 14.dp)
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SourceSettingRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .background(if (selected) io.github.unidentified24.spock.ui.theme.GridBackground else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color(0xFFB9B4C3), fontSize = 12.sp)
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = AppPalette, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = AppPalette,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color(0xFFB9B4C3), fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AppPalette,
                uncheckedThumbColor = Color(0xFFB9B4C3),
                uncheckedTrackColor = Color(0xFF333333),
            ),
        )
    }
}

@Composable
private fun QualityPicker(
    title: String,
    selected: StreamQuality,
    onSelect: (StreamQuality) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        StreamQuality.entries.forEach { q ->
            val isSel = q == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onSelect(q) }
                    .background(if (isSel) io.github.unidentified24.spock.ui.theme.GridBackground else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(q.label, color = Color.White, fontSize = 15.sp)
                    Text(q.detail, color = Color(0xFFB9B4C3), fontSize = 12.sp)
                }
                if (isSel) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = AppPalette,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
