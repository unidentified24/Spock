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
import io.github.unidentified24.spock.di.Downloads
import io.github.unidentified24.spock.ui.components.*
import io.github.unidentified24.spock.ui.navigation.*
import io.github.unidentified24.spock.ui.theme.*
import io.github.unidentified24.spock.ui.viewmodel.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import io.github.unidentified24.spock.features.FeatureSettings
import io.github.unidentified24.spock.features.downloads.*

@Composable
fun DownloadsScreen(navController:NavController) {
    val context=LocalContext.current;var songs by remember {mutableStateOf<List<SongsModel>>(emptyList())}
    val vm:PlayerViewModel=hiltViewModel()
    var pending by remember { mutableStateOf<List<Pair<SongsModel,Int>>>(emptyList()) }
    var failed by remember { mutableStateOf<List<Pair<SongsModel,String>>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<OfflinePlaylist>>(emptyList()) }
    var tasks by remember { mutableStateOf<List<DownloadTask>>(emptyList()) }
    var connection by remember{mutableStateOf(ConnectionState(false,false,false,false))}
    val settings by FeatureSettings.state.collectAsState()
    var menu by remember { mutableStateOf<SongsModel?>(null) }
    LaunchedEffect(Unit) {
        while(true) {
            val snapshot=withContext(Dispatchers.IO) {
                Triple(getDownloadedSongs(context),Downloads.downloadingSnapshot(),Downloads.failedSnapshot()) to getOfflinePlaylists(context)
            }
            songs=snapshot.first.first;pending=snapshot.first.second;failed=snapshot.first.third;playlists=snapshot.second
            tasks=DownloadLedger.snapshot();connection=withContext(Dispatchers.IO){NetworkStatus.read(context)}
            delay(750)
        }
    }
    menu?.let { song -> SongOptionsSheet(song,navController,context,onDismiss={menu=null}) }
    val savedIds=remember(songs){songs.map{it.id}.toSet()}
    LazyColumn(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding(),contentPadding=PaddingValues(bottom=32.dp)) {
        item { StudioHeader(navController,"Always with you.",eyebrow="OFFLINE",back=true) }
        item {
            StudioCard(Modifier.padding(horizontal=20.dp).fillMaxWidth()) {
                Text("${songs.size} saved ${if(songs.size==1) "track" else "tracks"}",color=Color.White,style=MaterialTheme.typography.headlineSmall)
                Text("Saved audio plays without a connection. Downloads continue in the background.",color=TextMuted)
                Button(onClick={playStudioTrack(vm,songs,0,context)},enabled=songs.isNotEmpty()) {Text("Play saved music")}
                TextButton(onClick={navController.navigate("${Routes.Settings.route}?section=downloads")}){Text("Download settings")}
            }
        }
        if(playlists.isNotEmpty()) {
            item {StudioSection("Offline playlists")}
            items(playlists,key={"playlist:${it.id}"}) {playlist->
                StudioMediaRow(playlist.name,"${playlist.songs.count{it.id in savedIds}} of ${playlist.songs.size} tracks saved",playlist.coverUri){navController.navigate(offlinePlaylistRoute(playlist.id))}
            }
        }
        if(pending.isNotEmpty()) {
            item {
                StudioSection("Saving ${pending.size} tracks")
                TextButton(onClick={Downloads.cancelAll()}) {Text("Cancel all downloads")}
            }
            items(pending,key={"pending:${it.first.url}"}) { (song,percent) ->
                val task=tasks.firstOrNull{it.song.id==song.id&&it.pending}
                StudioCard(Modifier.padding(horizontal=20.dp,vertical=5.dp).fillMaxWidth()) {
                    Text(song.title,color=Color.White,style=MaterialTheme.typography.titleSmall)
                    Text(when{
                        task==null->"Adding to queue…"
                        task.runAfter>System.currentTimeMillis()->(if(task.state==DownloadState.RETRY)"Retry around " else "Scheduled for ")+java.time.Instant.ofEpochMilli(task.runAfter).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("EEE HH:mm"))
                        settings.downloads.chargingOnly&&!connection.charging->"Waiting for charging"
                        task.remote&&settings.downloads.wifiOnly&&!connection.wifi->"Waiting for Wi-Fi"
                        task.remote&&!connection.online->"Waiting for a connection"
                        task.state==DownloadState.QUEUED->"Queued"
                        percent==0->"Preparing download…"
                        else->"$percent% downloaded"
                    },color=TextMuted)
                    if(percent==0) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress={percent/100f},modifier=Modifier.fillMaxWidth())
                    Row {
                        TextButton(onClick={Downloads.pause(song.url)}) {Text("Pause")}
                        TextButton(onClick={Downloads.cancel(song.url)}) {Text("Cancel")}
                        if(task?.state!=DownloadState.RUNNING)TextButton(onClick={Downloads.prioritize(song.url)}){Text("Prioritize")}
                    }
                    if(task!=null&&task.runAfter>System.currentTimeMillis())TextButton(onClick={Downloads.resume(song.url,now=true)}){Text("Download now")}
                }
            }
        }
        if(failed.isNotEmpty()) {
            item { StudioSection("Needs another try") }
            items(failed,key={"failed:${it.first.url}"}) { (song,error) ->
                StudioCard(Modifier.padding(horizontal=20.dp,vertical=5.dp).fillMaxWidth()) {
                    Text(song.title,color=Color.White,style=MaterialTheme.typography.titleSmall)
                    Text(error,color=TextMuted,style=MaterialTheme.typography.bodySmall,maxLines=3)
                    Row {
                        TextButton(onClick={Downloads.resume(song.url,now=true)}) {Text("Retry now")}
                        TextButton(onClick={Downloads.dismissFailure(song.url);failed=Downloads.failedSnapshot()}) {Text("Dismiss")}
                    }
                }
            }
        }
        val paused=tasks.filter{it.state==DownloadState.PAUSED}
        if(paused.isNotEmpty()){
            item{StudioSection("Paused")}
            items(paused,key={"paused:${it.song.id}"}){task->StudioCard(Modifier.padding(horizontal=20.dp,vertical=5.dp).fillMaxWidth()){
                Text(task.song.title,color=Color.White)
                Row{TextButton(onClick={Downloads.resume(task.song.url)}){Text("Resume")};TextButton(onClick={Downloads.cancel(task.song.url)}){Text("Cancel")}}
            }}
        }
        item { StudioSection("Ready offline") }
        if(songs.isEmpty()) item {StudioEmpty("Your offline shelf is empty","Open a song’s options and choose Download. Add local files from Collection → Local music.")}
        itemsIndexed(songs,key={i,s->"saved:${s.id}:$i"}) { i,song ->
            StudioTrack(song,i,vm.currentSongId.value==song.id,{playStudioTrack(vm,songs,i,context)},{menu=song})
        }
    }
}
