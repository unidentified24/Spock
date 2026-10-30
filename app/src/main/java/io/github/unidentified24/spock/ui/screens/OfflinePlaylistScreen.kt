package io.github.unidentified24.spock.ui.screens

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import io.github.unidentified24.spock.data.entity.*
import io.github.unidentified24.spock.data.preferences.*
import io.github.unidentified24.spock.ui.components.*
import kotlinx.coroutines.*

@Composable fun OfflinePlaylistScreen(nav:NavController,id:String) {
    val context=LocalContext.current.applicationContext
    var playlist by remember(id){mutableStateOf<OfflinePlaylist?>(null)}
    var songs by remember(id){mutableStateOf<List<SongsModel>>(emptyList())}
    var loading by remember(id){mutableStateOf(true)}
    var remove by remember{mutableStateOf(false)}
    LaunchedEffect(id){while(isActive){
        val snapshot=withContext(Dispatchers.IO){val p=getOfflinePlaylist(context,id);p to p?.playable(getDownloadedSongs(context).associateBy{it.id}).orEmpty()}
        playlist=snapshot.first;songs=snapshot.second;loading=false;delay(1000)
    }}
    StudioCollection(nav,playlist?.name ?: "Offline playlist","${songs.size} of ${playlist?.songs?.size ?: 0} tracks saved",playlist?.coverUri.orEmpty(),songs,loading,
        extra={playlist?.let{PlaylistDownloads(it,nav,offlineView=true);TextButton(onClick={remove=true}){Text("Remove playlist from Downloads")}}},
        emptyTitle="No saved tracks yet",emptyDescription="Download the playlist above. Saved tracks appear here as they finish.")
    if(remove)AlertDialog(onDismissRequest={remove=false},title={Text("Remove offline playlist?")},text={Text("This removes the playlist from Downloads. Its songs stay saved, including any shared with other playlists.")},confirmButton={TextButton(onClick={forgetOfflinePlaylist(context,id);remove=false;nav.navigateUp()}){Text("Remove playlist")}},dismissButton={TextButton(onClick={remove=false}){Text("Keep")}})
}
