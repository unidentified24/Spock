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
import kotlinx.coroutines.delay

@Composable
fun PlaylistScreen(navController:NavController,playlistId:String,playlistName:String="") {
    val vm:PlaylistViewModel=hiltViewModel();val response by vm.songs.collectAsState();val info by vm.playlist.collectAsState()
    val allLoaded by vm.allTracksLoaded.collectAsState()
    LaunchedEffect(playlistId){vm.loadPlaylist(playlistId)}
    val context=LocalContext.current.applicationContext
    var cached by remember(playlistId){mutableStateOf<OfflinePlaylist?>(null)}
    var savedSongs by remember(playlistId){mutableStateOf<Map<Int,SongsModel>>(emptyMap())}
    LaunchedEffect(playlistId){withContext(Dispatchers.IO){getOfflinePlaylist(context,playlistId) to getDownloadedSongs(context).associateBy{it.id}}.let{cached=it.first;savedSongs=it.second}}
    val online=(response as? Response.Success)?.data
    val songs=online ?: cached?.playable(savedSongs).orEmpty();val model=(info as? Response.Success)?.data
    val name=model?.name?.takeIf{it.isNotBlank()} ?: playlistName.ifBlank{cached?.name ?: "Playlist"}
    val cover=model?.coverUri?.takeIf{it.isNotBlank()} ?: cached?.coverUri ?: songs.firstOrNull()?.coverUri.orEmpty()
    val source=online ?: cached?.songs.orEmpty()
    StudioCollection(navController,name,
        model?.artists?.ifBlank {"Playlist"} ?: "Playlist",cover,songs,response is Response.Loading&&cached==null,
        extra={
            PlaylistDownloads(OfflinePlaylist(playlistId,name,cover,source),navController,loading=!allLoaded&&(online!=null||cached==null))
            if(response is Response.Error)TextButton(onClick={vm.loadPlaylist(playlistId,force=true)}){Text("Refresh playlist")}
        })
}
