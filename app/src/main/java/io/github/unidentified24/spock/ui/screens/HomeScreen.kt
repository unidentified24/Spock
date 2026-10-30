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
fun HomeScreen(navController:NavController) {
    val vm:HomeViewModel=hiltViewModel()
    val home by vm.home.collectAsState(); val albums by vm.albums.collectAsState(); val artists by vm.artists.collectAsState()
    val feed=(home as? Response.Success)?.data
    val releases=(albums as? Response.Success)?.data.orEmpty()
    val creators=(artists as? Response.Success)?.data.orEmpty()
    LazyColumn(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding(),contentPadding=PaddingValues(bottom=180.dp)) {
        item { StudioHeader(navController,"A sound of\nyour own.") }
        item {
            val context=LocalContext.current
            var help by remember{mutableStateOf(!context.getSharedPreferences("onboarding",0).getBoolean("navigation22",false))}
            if(help)StudioCard(Modifier.padding(20.dp).fillMaxWidth()) {
                Text("Your shortcuts",color=Color.White,style=MaterialTheme.typography.titleMedium)
                Text("Account opens settings and listening stats. Collection holds playlists, downloads and local files. Choose Player view while listening to switch artwork.",color=TextMuted)
                Row {
                    TextButton(onClick={navController.navigate(Routes.Connections.route)}){Text("Connect music")}
                    TextButton(onClick={help=false;context.getSharedPreferences("onboarding",0).edit().putBoolean("navigation22",true).apply()}){Text("Got it")}
                }
            }
        }
        item {
            StudioCard(Modifier.padding(horizontal=20.dp).fillMaxWidth()) {
                Text("IN YOUR ORBIT",color=AppPalette,style=MaterialTheme.typography.labelSmall)
                Text("Pick up where you left off.",color=Color.White,style=MaterialTheme.typography.headlineSmall)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    StudioChip("Collection") { navController.navigate(Routes.Library.route) }
                    StudioChip("History") { navController.navigate(Routes.History.route) }
                    StudioChip("Local") { navController.navigate(Routes.LocalFiles.route) }
                }
            }
        }
        if(feed!=null) itemsIndexed(feed.sections) { index,section ->
            StudioSection(section.title.ifBlank { "Back in rotation" },if(index==0) "Your collection, rediscovered." else null)
            LazyRow(contentPadding=PaddingValues(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                items(section.items) { item ->
                    Column(Modifier.width(if(index==0) 214.dp else 156.dp).clip(StudioShape).background(GridBackground).clickable {
                        when(item) {
                            is HomeItem.Album -> navController.navigate(albumRoute(item.name,item.artists.ifBlank { item.subtitle }))
                            is HomeItem.Artist -> navController.navigate(artistRoute(item.name,item.id))
                            is HomeItem.Playlist -> if(item.id.isNotBlank()) navController.navigate(playlistRoute(item.id,item.name))
                        }
                    }.padding(10.dp)) {
                        StudioCover(item.imageUrl,Modifier.fillMaxWidth().aspectRatio(if(index==0) 1.25f else 1f))
                        Text(item.name,color=Color.White,style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=12.dp))
                        Text(when(item){is HomeItem.Album->item.subtitle;is HomeItem.Playlist->item.subtitle;is HomeItem.Artist->"Artist"},color=TextMuted,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if(feed==null) {
            item { StudioSection("Fresh arrivals") }
            if(home is Response.Loading && releases.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(24.dp),contentAlignment=Alignment.Center) { CircularProgressIndicator() } }
            items(releases) { album -> StudioMediaRow(album.name,album.artists,album.coverUri) { navController.navigate(albumRoute(album.name,album.artists)) } }
            items(creators) { artist -> StudioMediaRow(artist.name,"Artist",artist.coverUri) { navController.navigate(artistRoute(artist.name,artist.id)) } }
            if(home is Response.Error && releases.isEmpty()) item { StudioEmpty("Your music is a connection away","Open Account to check your Spotify connection. Downloads and local files are always in Collection.") }
        }
    }
}
