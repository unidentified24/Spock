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
fun ArtistScreen(navController:NavController,artistName:String,artistId:String="") {
    val vm:ArtistViewModel=hiltViewModel();val response by vm.overview.collectAsState();val followed by vm.followedArtistIds.collectAsState()
    LaunchedEffect(artistName,artistId){vm.loadArtistOverview(artistName,artistId)}
    val data=(response as? Response.Success)?.data;val songs=data?.topTracks?.map{it.song}.orEmpty()
    val context=LocalContext.current
    var following by remember(data?.id,followed) {mutableStateOf(data?.id in followed)}
    var menu by remember {mutableStateOf<SongsModel?>(null)};val player:PlayerViewModel=hiltViewModel()
    menu?.let {SongOptionsSheet(it,navController,context,onDismiss={menu=null})}
    LazyColumn(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding(),contentPadding=PaddingValues(bottom=32.dp)) {
        item {StudioHeader(navController,data?.name ?: artistName,back=true)}
        item {StudioCard(Modifier.padding(horizontal=20.dp).fillMaxWidth()) {
            StudioCover(data?.headerImage?.ifBlank {data.avatarImage}.orEmpty(),Modifier.fillMaxWidth().aspectRatio(1.7f))
            Text("ARTIST SPOTLIGHT",color=AppPalette,style=MaterialTheme.typography.labelSmall)
            data?.monthlyListeners?.let {Text("${java.text.NumberFormat.getIntegerInstance().format(it)} monthly listeners",color=TextMuted)}
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Button(onClick={playStudioTrack(player,songs,0,context)},enabled=songs.isNotEmpty(),shape=RoundedCornerShape(14.dp)){Icon(Icons.Default.PlayArrow,null);Text("Listen")}
                OutlinedButton(onClick={following=!following;io.github.unidentified24.spock.data.api.SpotifySync.setArtistFollowed(context,data?.id.orEmpty(),following)},shape=RoundedCornerShape(14.dp)){Text(if(following)"Following" else "Follow")}
            }
        }}
        item {StudioSection("Essential listening")}
        itemsIndexed(songs) {i,s->StudioTrack(s,i,player.currentSongId.value==s.id,{playStudioTrack(player,songs,i,context)},{menu=s})}
        if(response is Response.Loading) item {Box(Modifier.fillMaxWidth().padding(30.dp),contentAlignment=Alignment.Center){CircularProgressIndicator()}}
        item {StudioSection("The record shelf")}
        items(data?.popularReleases.orEmpty()) {a->StudioMediaRow(a.name,a.time,a.coverUri){navController.navigate(albumRoute(a.name,a.artists))}}
        if(!data?.biography.isNullOrBlank()) item {StudioCard(Modifier.padding(20.dp).fillMaxWidth()) {
            Text("BEHIND THE MUSIC",color=AppPalette,style=MaterialTheme.typography.labelSmall)
            Text(android.text.Html.fromHtml(data!!.biography,android.text.Html.FROM_HTML_MODE_LEGACY).toString(),color=TextMuted,style=MaterialTheme.typography.bodyMedium)
        }}
        if(!data?.relatedArtists.isNullOrEmpty()) item{StudioSection("In the same orbit")}
        items(data?.relatedArtists.orEmpty()) {a->StudioMediaRow(a.name,"Artist",a.coverUri){navController.navigate(artistRoute(a.name,a.id))}}
    }
}
@Composable fun ArtistReleasesScreen(navController:NavController,artistName:String) = ArtistScreen(navController,artistName)
