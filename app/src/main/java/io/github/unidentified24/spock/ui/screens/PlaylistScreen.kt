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
    LaunchedEffect(playlistId){vm.loadPlaylist(playlistId)}
    val songs=(response as? Response.Success)?.data.orEmpty();val model=(info as? Response.Success)?.data
    StudioCollection(navController,model?.name?.ifBlank {playlistName} ?: playlistName,
        model?.artists?.ifBlank {"A collection from your Spotify library"} ?: "Playlist",model?.coverUri ?: songs.firstOrNull()?.coverUri.orEmpty(),songs,response is Response.Loading)
}
