package io.github.unidentified24.spock.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import io.github.unidentified24.spock.data.entity.OfflinePlaylist
import io.github.unidentified24.spock.data.preferences.*
import io.github.unidentified24.spock.di.Downloads
import io.github.unidentified24.spock.ui.navigation.*
import io.github.unidentified24.spock.ui.theme.TextMuted
import kotlinx.coroutines.*
import io.github.unidentified24.spock.features.downloads.DownloadPolicy
import io.github.unidentified24.spock.features.FeatureSettings
import io.github.unidentified24.spock.features.downloads.SmartOffline

@Composable fun PlaylistDownloads(playlist:OfflinePlaylist,nav:NavController,offlineView:Boolean=false,loading:Boolean=false) {
    val context=LocalContext.current.applicationContext;val scope=rememberCoroutineScope()
    val preferences by FeatureSettings.state.collectAsState()
    var saved by remember(playlist.id){mutableStateOf<Set<Int>>(emptySet())}
    var active by remember(playlist.id){mutableStateOf<Set<String>>(emptySet())}
    var failures by remember(playlist.id){mutableIntStateOf(0)}
    var confirm by remember(playlist.id){mutableStateOf(false)}
    var starting by remember(playlist.id){mutableStateOf(false)}
    var message by remember(playlist.id){mutableStateOf("")}
    LaunchedEffect(playlist.id,playlist.songs) {
        val queries=playlist.songs.map{it.url}.toSet()
        while(isActive){
            saved=withContext(Dispatchers.IO){getDownloadedSongs(context).map{it.id}.toSet()}
            active=Downloads.downloadingSnapshot().map{it.first.url}.filter{it in queries}.toSet()
            failures=Downloads.failedSnapshot().count{it.first.url in queries}
            delay(750)
        }
    }
    val remaining=playlist.missingSongs(saved)
    val ready=playlist.songs.count{it.id in saved}
    val complete=playlist.songs.isNotEmpty()&&ready==playlist.songs.size
    val estimate=remember(remaining,getDownloadQuality(context)){remaining.map{DownloadPolicy.estimateBytes(it,getDownloadQuality(context).name)}.let{ranges->ranges.sumOf{it.first}..ranges.sumOf{it.last}}}
    if(confirm) ConfirmDownloadAllDialog(remaining.size,{confirm=false}) {
        starting=true;message=""
        scope.launch {
            try {
                withContext(Dispatchers.IO){saveOfflinePlaylist(context,playlist)}
                val missing=withContext(Dispatchers.IO){playlist.missingSongs(getDownloadedSongs(context).map{it.id}.toSet())}
                Downloads.enqueueAll(missing,context)
                message=if(missing.isEmpty())"Ready offline" else "Added to Downloads"
            }catch(cancelled:CancellationException){throw cancelled}
             catch(_:Exception){message="Couldn't save this playlist. Please try again."}
            finally{starting=false}
        }
    }
    if(!complete||!offlineView)Button(onClick={
        if(active.isNotEmpty())nav.navigate(Routes.Downloads.route)
        else if(complete){
            starting=true
            scope.launch {
                try{withContext(Dispatchers.IO){saveOfflinePlaylist(context,playlist)};nav.navigate(offlinePlaylistRoute(playlist.id)){launchSingleTop=true}}
                catch(cancelled:CancellationException){throw cancelled}
                catch(_:Exception){message="Couldn't save this playlist. Please try again."}
                finally{starting=false}
            }
        } else confirm=true
    },enabled=!loading&&!starting&&playlist.songs.isNotEmpty()&&(complete||remaining.isNotEmpty()||active.isNotEmpty()),modifier=Modifier.fillMaxWidth()) {
        Icon(if(complete)Icons.Default.OfflinePin else Icons.Default.Download,null)
        Text(when{loading->" Loading full playlist…";starting->" Saving…";active.isNotEmpty()->" View download progress";complete->" Open offline playlist";ready>0->" Download remaining tracks";else->" Download playlist"})
    }
    if(ready>0||active.isNotEmpty()||failures>0)Text("$ready of ${playlist.songs.size} saved"+(if(active.isNotEmpty())" · ${active.size} downloading" else "")+(if(failures>0)" · $failures need retry" else ""),color=TextMuted,style=MaterialTheme.typography.bodySmall)
    if(remaining.isNotEmpty()&&active.isEmpty()&&!loading)Text("Estimated download: ${estimate.first/1048576}–${(estimate.last+1048575)/1048576} MB",color=TextMuted,style=MaterialTheme.typography.bodySmall)
    if(active.isNotEmpty())TextButton(onClick={active.forEach(Downloads::cancel)}){Text("Cancel playlist downloads")}
    if(!loading&&playlist.songs.isNotEmpty())PreferenceSwitch("Keep this playlist up to date",playlist.id in preferences.downloads.smartPlaylists){enabled->
        scope.launch {
            withContext(Dispatchers.IO){saveOfflinePlaylist(context,playlist)}
            FeatureSettings.update{it.copy(downloads=it.downloads.copy(smartPlaylists=if(enabled)it.downloads.smartPlaylists+playlist.id else it.downloads.smartPlaylists-playlist.id))}
            SmartOffline.schedule(context,now=enabled)
        }
    }
    if(message.isNotBlank())Text(message,color=TextMuted,style=MaterialTheme.typography.bodySmall)
}
