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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(navController:NavController,section:String="sound") {
    val context=LocalContext.current
    var tab by remember(section){mutableIntStateOf(when(section){"look"->2;"sound"->1;"downloads"->3;"data"->4;"about"->5;else->0})}
    LazyColumn(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding(),contentPadding=PaddingValues(bottom=32.dp)) {
        item {StudioHeader(navController,"Settings",back=true)}
        item {Row(Modifier.padding(horizontal=20.dp).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("Connections","Sound","Appearance","Downloads","Data","About").forEachIndexed {i,label->StudioChip(label,tab==i){tab=i}}
        }}
        item {Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            when(tab) {
                0 -> StudioAction("Connected music","Manage Spotify, YouTube, Deezer and FLAC",Icons.Default.GraphicEq){navController.navigate(Routes.Connections.route)}
                1 -> SoundSettings()
                2 -> AppearancePage()
                3 -> DownloadSettings()
                4 -> {PrivacySettings();BackupSettings()}
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
                    StudioAction("Releases","APK downloads and release notes",Icons.Default.OpenInNew){context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://github.com/unidentified24/Spock/releases")))}
                }
            }
        }}
    }
}

@Composable private fun PrivacySettings(){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val options=io.github.unidentified24.spock.features.FeatureSettings.state.collectAsState().value.privacy
    var confirmClear by remember{mutableStateOf(false)}
    StudioCard(Modifier.fillMaxWidth()){
        Text("Privacy on this device",color=Color.White,style=MaterialTheme.typography.titleLarge)
        SourceToggle("Private listening","Don't save listening history, queue snapshots or statistics",options.privateListening){value->io.github.unidentified24.spock.features.FeatureSettings.update{it.copy(privacy=it.privacy.copy(privateListening=value))}}
        SourceToggle("Filter explicit tracks","Hide tracks marked explicit from supported mixes and playback",options.explicitFilter){value->io.github.unidentified24.spock.features.FeatureSettings.update{it.copy(privacy=it.privacy.copy(explicitFilter=value))}}
        TextButton(onClick={confirmClear=true}){Text("Clear all listening history",color=Color.White)}
    }
    if(confirmClear)AlertDialog(onDismissRequest={confirmClear=false},title={Text("Clear listening history?")},text={Text("This removes saved listening time, play counts and recap data from this profile. It cannot be undone.")},confirmButton={TextButton(onClick={confirmClear=false;scope.launch{runCatching{withContext(Dispatchers.IO){io.github.unidentified24.spock.data.preferences.ListeningMeter.delete(context)}}.onSuccess{android.widget.Toast.makeText(context,"Listening history cleared",android.widget.Toast.LENGTH_SHORT).show()}.onFailure{android.widget.Toast.makeText(context,"History couldn't be cleared",android.widget.Toast.LENGTH_SHORT).show()}}}){Text("Clear history")}},dismissButton={TextButton(onClick={confirmClear=false}){Text("Cancel")}})
}

@Composable fun SourceSettings(nav:NavController) {
    val context=LocalContext.current
    var yt by remember {mutableStateOf(isYouTubeSourceEnabled(context))}
    var flac by remember {mutableStateOf(isFlacSourceEnabled(context))}
    var wifiFlac by remember {mutableStateOf(isFlacWifiRequested(context))}
    var dz by remember {mutableStateOf(isDeezerRaceEnabled(context))}
    var sc by remember {mutableStateOf(isSoundCloudEnabled(context))}
    var instant by remember {mutableStateOf(isWebPlaybackEnabled(context))}
    StudioCard(Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Bolt,null,tint=AppPalette)
        SourceToggle("Spock instant start","Spotify playback",instant){instant=it;setWebPlaybackEnabled(context,it)}
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
        TextButton(onClick={enableFlacOverWifi(context);flac=true;wifiFlac=true}) {Text(if(wifiFlac&&flac)"FLAC included over Wi-Fi ✓" else "Include FLAC over Wi-Fi")}
        if(wifiFlac&&flac)Text("Enabled on Wi-Fi, including metered Wi-Fi. Mobile data keeps its own quality setting.",color=TextMuted,style=MaterialTheme.typography.bodySmall)
        Text(io.github.unidentified24.spock.lossless.LosslessRegistry.lastPublicResult,color=TextMuted,style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={nav.navigate(Routes.SpotiflacVerify.route)}){Text(if(hasSpotiflacSession(context))"Refresh verification" else "Set up FLAC verification")}
    }
    StudioCard(Modifier.fillMaxWidth()) {
        SourceToggle("SoundCloud","Public playable tracks · no account setup",sc){sc=it;setSoundCloudEnabled(context,it)}
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
    val sound=io.github.unidentified24.spock.features.FeatureSettings.state.collectAsState().value.sound
    fun saveSound(value:io.github.unidentified24.spock.features.SoundOptions){io.github.unidentified24.spock.features.FeatureSettings.update{it.copy(sound=value)};SongPlayer.applySoundOptions()}
    var speed by remember{mutableFloatStateOf(sound.speed)};var bands by remember{mutableStateOf(sound.equalizer)}
    LaunchedEffect(sound.speed){speed=sound.speed};LaunchedEffect(sound.equalizer){bands=sound.equalizer}
    StudioCard(Modifier.fillMaxWidth()){
        Text("Sound shaping",color=Color.White,style=MaterialTheme.typography.titleLarge)
        SourceToggle("Native-stream equalizer","Five-band EQ for Spock's native audio engine",sound.enabled){saveSound(sound.copy(enabled=it))}
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            listOf("Flat","Bass","Vocal","Treble").forEach{preset->FilterChip(selected=sound.preset==preset,onClick={
                val values=when(preset){"Bass"->listOf(600,300,0,-100,100);"Vocal"->listOf(-200,100,400,300,-100);"Treble"->listOf(-100,0,100,300,600);else->listOf(0,0,0,0,0)}
                bands=values;saveSound(sound.copy(preset=preset,equalizer=values,enabled=preset!="Flat"))
            },label={Text(preset)})}
        }
        bands.take(5).forEachIndexed{index,level->Column{
            Text(listOf("Low","Low-mid","Mid","High-mid","High").getOrElse(index){"Band ${index+1}"}+" · ${level/100f} dB",color=TextMuted,style=MaterialTheme.typography.bodySmall)
            Slider(level/100f,{value->bands=bands.toMutableList().also{it[index]=(value*100).toInt()}},valueRange=-12f..12f,onValueChangeFinished={saveSound(sound.copy(equalizer=bands,preset="Custom",enabled=true))})
        }}
        Text("Native playback speed · ${"%.2f".format(speed)}×",color=Color.White)
        Slider(speed,{speed=it},valueRange=.5f..2f,onValueChangeFinished={saveSound(sound.copy(speed=speed))})
        SourceToggle("Keep pitch while changing speed","Avoids raising or lowering the song's pitch",sound.preservePitch){saveSound(sound.copy(preservePitch=it))}
    }
    val lyricOptions=io.github.unidentified24.spock.features.FeatureSettings.state.collectAsState().value.lyrics
    StudioCard(Modifier.fillMaxWidth()) {
        SourceToggle("Save lyrics offline","Keep lyrics you open on this device",lyricOptions.cache){value->io.github.unidentified24.spock.data.api.LyricsApi.setPersistentCache(context,value)}
        SourceToggle("Side-by-side translation","Show original and translated lines in two columns",lyricOptions.bilingual){value->io.github.unidentified24.spock.features.FeatureSettings.update{it.copy(lyrics=it.lyrics.copy(bilingual=value))}}
        PreferenceChoice("Lyrics layout",lyricOptions.layout,listOf("CENTER" to "Centered","COMPACT" to "Compact","LEFT" to "Left aligned")){value->io.github.unidentified24.spock.features.FeatureSettings.update{it.copy(lyrics=it.lyrics.copy(layout=value))}}
        Text("Lyrics text size · ${"%.0f".format(lyricOptions.textScale*100)}%",color=Color.White)
        Slider(lyricOptions.textScale,{value->io.github.unidentified24.spock.features.FeatureSettings.update{it.copy(lyrics=it.lyrics.copy(textScale=value))}},valueRange=.8f..1.6f)
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
    val updateOptions=io.github.unidentified24.spock.features.FeatureSettings.state.collectAsState().value.updates
    var message by remember{mutableStateOf("Check for a signed update")}
    var busy by remember{mutableStateOf(false)}
    var showHistory by remember{mutableStateOf(false)}
    var history by remember{mutableStateOf(emptyList<io.github.unidentified24.spock.data.update.UpdateChecker.UpdateInfo>())}
    var available by remember{mutableStateOf<io.github.unidentified24.spock.data.update.UpdateChecker.UpdateInfo?>(null)}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        listOf("STABLE" to "Stable","BETA" to "Beta").forEach{(id,label)->FilterChip(selected=updateOptions.channel==id,onClick={io.github.unidentified24.spock.features.FeatureSettings.update{it.copy(updates=it.updates.copy(channel=id))};available=null},label={Text(label)})}
    }
    SourceToggle("Check for updates automatically","Check the chosen channel when the update prompt opens",updateOptions.automaticChecks){enabled->io.github.unidentified24.spock.features.FeatureSettings.update{it.copy(updates=it.updates.copy(automaticChecks=enabled))}}
    TextButton(onClick={busy=true;scope.launch{history=withContext(Dispatchers.IO){io.github.unidentified24.spock.data.update.UpdateChecker.fetchHistory(updateOptions.channel)};showHistory=true;busy=false}}){Text("Release history")}
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
    if(showHistory)AlertDialog(onDismissRequest={showHistory=false},title={Text("${if(updateOptions.channel=="BETA")"Beta and stable" else "Stable"} releases")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState())){
        if(history.isEmpty())Text(io.github.unidentified24.spock.data.update.UpdateChecker.lastMessage,color=TextMuted)
        history.forEach{release->Column(Modifier.fillMaxWidth().clickable{android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(release.pageUrl)).also{context.startActivity(it)}}.padding(vertical=10.dp)){
            Text("Spock ${release.version}${if(release.prerelease)" · beta" else ""}",color=Color.White,style=MaterialTheme.typography.titleMedium)
            Text(release.publishedAt.take(10),color=TextMuted,style=MaterialTheme.typography.labelSmall)
            Text(release.notes.ifBlank{"Release notes"},color=TextMuted,maxLines=4,overflow=TextOverflow.Ellipsis)
        }}
    }},confirmButton={TextButton(onClick={showHistory=false}){Text("Done")}})
}
