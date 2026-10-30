package io.github.unidentified24.spock.ui.screens
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import io.github.unidentified24.spock.data.api.Response
import io.github.unidentified24.spock.data.entity.*
import io.github.unidentified24.spock.data.preferences.*
import io.github.unidentified24.spock.di.SongPlayer
import io.github.unidentified24.spock.ui.components.*
import io.github.unidentified24.spock.ui.navigation.*
import io.github.unidentified24.spock.ui.theme.*
import io.github.unidentified24.spock.ui.viewmodel.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(navController:NavController,section:String="sound") {
    val context=LocalContext.current
    var tab by remember(section){mutableIntStateOf(when(section){"look"->2;"sound"->1;"about"->3;else->0})}
    LazyColumn(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding(),contentPadding=PaddingValues(bottom=180.dp)) {
        item {StudioHeader(navController,"Settings",back=true)}
        item {Row(Modifier.padding(horizontal=20.dp).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("Connections","Sound","Appearance","About").forEachIndexed {i,label->StudioChip(label,tab==i){tab=i}}
        }}
        item {Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            when(tab) {
                0 -> StudioAction("Connected music","Manage Spotify, YouTube, Deezer and FLAC",Icons.Default.GraphicEq){navController.navigate(Routes.Connections.route)}
                1 -> SoundSettings()
                2 -> {
                    Text("Layout",color=TextMuted,style=MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){SpockLayout.entries.forEach {layout->StudioChip(layout.label,currentLayout==layout){currentLayout=layout;context.getSharedPreferences("appearance",0).edit().putString("layout",layout.name).apply()}}}
                    Text("Choose your atmosphere.",color=Color.White,style=MaterialTheme.typography.headlineSmall)
                    SpockLook.entries.forEach {look->
                        Row(Modifier.fillMaxWidth().clip(StudioShape).background(look.surface).border(1.dp,if(currentLook==look)look.accent else Color.Transparent,StudioShape)
                            .clickable{currentLook=look;if(look==SpockLook.ORIGINAL)currentLayout=SpockLayout.CLASSIC;context.getSharedPreferences("appearance",0).edit().putString("theme",look.name).putString("layout",currentLayout.name).apply()}.padding(18.dp),
                            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(look.accent))
                            Text(look.label,color=Color.White,modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                            if(currentLook==look)Icon(Icons.Default.Check,null,tint=look.accent)
                        }
                    }
                    Text("The whole app follows this theme.",color=TextMuted)
                }
                else -> {
                    SpockMark(Modifier.size(96.dp))
                    Text("Spock ${io.github.unidentified24.spock.BuildConfig.VERSION_NAME}",color=Color.White,style=MaterialTheme.typography.headlineMedium)
                    Text("Interface ${io.github.unidentified24.spock.BuildConfig.UI_VERSION}",color=AppPalette)
                    UpdateSettings()
                    TextButton(onClick={
                        val clipboard=context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Spock playback report",io.github.unidentified24.spock.playback.PlaybackDiagnostics.report()+"\nLast app error:\n"+context.getSharedPreferences("diagnostics",0).getString("last_crash","None recorded")))
                        android.widget.Toast.makeText(context,"Playback report copied",android.widget.Toast.LENGTH_SHORT).show()
                    }) {Text("Copy playback report")}
                    StudioAction("Source & credits","Independent, open source · GPL v3",Icons.Default.Code){
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://github.com/unidentified24/Spock")))
                    }
                    Text("Spock rebuilds the interface on an inherited open-source playback base. Upstream credits and licenses remain in the repository.",color=TextMuted,style=MaterialTheme.typography.bodySmall)
                }
            }
        }}
    }
}

@Composable fun SourceSettings(nav:NavController) {
    val context=LocalContext.current
    var yt by remember {mutableStateOf(isYouTubeSourceEnabled(context))}
    var flac by remember {mutableStateOf(isFlacSourceEnabled(context))}
    var dz by remember {mutableStateOf(isDeezerRaceEnabled(context))}
    var instant by remember {mutableStateOf(isWebPlaybackEnabled(context))}
    StudioCard(Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Bolt,null,tint=AppPalette)
        Text("A quick start. A prepared next song.",color=Color.White,style=MaterialTheme.typography.headlineSmall)
        Text("Spock uses your Spotify session for a tapped song, while preparing the next track from enabled sources. Two seconds before the end, an unprepared next track stays on Spotify playback.",color=TextMuted)
        SourceToggle("Spock instant start","Uses your existing Spotify connection. Availability depends on your account and Android System WebView.",instant){instant=it;setWebPlaybackEnabled(context,it)}
    }
    StudioCard(Modifier.fillMaxWidth()) {
        SourceToggle("YouTube Music",if(isYoutubeLoggedIn(context))"Connected" else "Anonymous · connect for account access",yt){yt=it;setYouTubeSourceEnabled(context,it)}
        TextButton(onClick={nav.navigate(Routes.YouTubeLogin.route)}) {Text(if(isYoutubeLoggedIn(context))"Reconnect YouTube" else "Connect YouTube")}
    }
    StudioCard(Modifier.fillMaxWidth()) {
        val connected=getDeezerArl(context)!=null
        SourceToggle("Deezer",if(connected)getDeezerTier(context).ifBlank{"Account connected"} else "Connect your account to enable",dz&&connected){
            if(connected){dz=it;setDeezerEnabled(context,it)}else nav.navigate(Routes.DeezerLogin.route)
        }
        TextButton(onClick={nav.navigate(Routes.DeezerLogin.route)}) {Text(if(connected)"Reconnect Deezer" else "Connect Deezer")}
        if(connected) TextButton(onClick={clearDeezer(context);dz=false}){Text("Disconnect",color=TextMuted)}
    }
    StudioCard(Modifier.fillMaxWidth()) {
        SourceToggle("FLAC","Participates when your sound quality is Lossless",flac){flac=it;setFlacSourceEnabled(context,it)}
        Text("Experimental community services can be unavailable. YouTube and Deezer keep competing.",color=TextMuted,style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={setWifiQuality(context,StreamQuality.LOSSLESS);setFlacSourceEnabled(context,true);flac=true}) {Text("Include FLAC over Wi-Fi")}
        TextButton(onClick={nav.navigate(Routes.SpotiflacVerify.route)}){Text(if(hasSpotiflacSession(context))"Refresh verification" else "Set up FLAC verification")}
    }
    StudioAction("Spotify connection","Your library, playlists and recommendations",Icons.Default.AccountCircle){nav.navigate(Routes.Login.route)}
}

@Composable private fun SourceToggle(title:String,subtitle:String,on:Boolean,change:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {Text(title,color=Color.White,style=MaterialTheme.typography.titleMedium);Text(subtitle,color=TextMuted,style=MaterialTheme.typography.bodySmall)}
        Switch(on,change)
    }
}

@Composable private fun SoundSettings() {
    val context=LocalContext.current
    var wifi by remember {mutableStateOf(getWifiQuality(context))};var cell by remember {mutableStateOf(getCellularQuality(context))};var dl by remember {mutableStateOf(getDownloadQuality(context))}
    var fade by remember {mutableFloatStateOf(getCrossfadeMs(context).toFloat())};var preload by remember {mutableStateOf(isPreloadEnabled(context))}
    var videos by remember {mutableStateOf(isVideoFallbackEnabled(context))};var language by remember {mutableStateOf(getLyricTranslateLang(context))}
    QualityControl("Wi-Fi",wifi){wifi=it;setWifiQuality(context,it)}
    QualityControl("Mobile data",cell){cell=it;setCellularQuality(context,it)}
    QualityControl("Downloads",dl){dl=it;setDownloadQuality(context,it)}
    StudioCard(Modifier.fillMaxWidth()) {
        Text("Crossfade · ${if(fade==0f)"Off" else "${fade.toInt()/1000}s"}",color=Color.White,style=MaterialTheme.typography.titleMedium)
        Slider(fade,{fade=it},valueRange=0f..CROSSFADE_MAX_MS.toFloat(),steps=11,onValueChangeFinished={setCrossfadeMs(context,fade.toInt())})
        SourceToggle("Prepare next track","Cache the next YouTube match while music plays",preload){preload=it;setPreloadEnabled(context,it)}
        SourceToggle("Video fallback","Try regular videos if Music has no playable match",videos){videos=it;setVideoFallbackEnabled(context,it)}
    }
    OutlinedTextField(language,{language=it.trim().take(12);setLyricTranslateLang(context,language)},label={Text("Lyrics language code")},supportingText={Text("For example en, fr, ja. Empty follows your phone.")},shape=StudioShape,modifier=Modifier.fillMaxWidth())
}
@Composable private fun QualityControl(title:String,value:StreamQuality,select:(StreamQuality)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    StudioCard(Modifier.fillMaxWidth()) {
        Text(title,color=TextMuted,style=MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().clickable{expanded=!expanded},horizontalArrangement=Arrangement.SpaceBetween) {
            Text(value.label,color=Color.White,style=MaterialTheme.typography.titleLarge);Icon(Icons.Default.ExpandMore,null,tint=AppPalette)
        }
        Text(value.detail,color=TextMuted,style=MaterialTheme.typography.bodySmall)
        if(expanded) StreamQuality.entries.forEach {q->TextButton(onClick={select(q);expanded=false}){Text(q.label+(if(q==value)" ✓" else ""))}}
    }
}
@Composable private fun UpdateSettings() {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var message by remember{mutableStateOf("Check for a signed update")}
    var busy by remember{mutableStateOf(false)}
    var available by remember{mutableStateOf<io.github.unidentified24.spock.data.update.UpdateChecker.UpdateInfo?>(null)}
    Button(onClick={busy=true;scope.launch {
        available=io.github.unidentified24.spock.data.update.UpdateChecker.check(context,manual=true)
        message=io.github.unidentified24.spock.data.update.UpdateChecker.lastMessage
        busy=false
    }},enabled=!busy,shape=RoundedCornerShape(14.dp)){Text(if(busy)"Please wait…" else "Check for updates")}
    available?.let {info->
        OutlinedButton(onClick={busy=true;scope.launch{
            message="Downloading and verifying ${info.version}…"
            runCatching{io.github.unidentified24.spock.data.update.ApkUpdater.install(context,info)}
                .onSuccess{message="Ready for Android to confirm installation."}
                .onFailure{message=it.message ?: "Update couldn't be installed."}
            busy=false
        }},enabled=!busy){Text("Download and install ${info.version}")}
    }
    Text(message,color=TextMuted,style=MaterialTheme.typography.bodySmall)
}
