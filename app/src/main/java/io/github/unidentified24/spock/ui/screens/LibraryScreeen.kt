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
fun LibraryScreen(navController:NavController) {
    val vm:LibraryViewModel=hiltViewModel(); val response by vm.entries.collectAsState();val artists by vm.followedArtists.collectAsState()
    var query by rememberSaveableStudio();var filter by remember { mutableIntStateOf(0) }
    val entries=(response as? Response.Success)?.data.orEmpty()
    val filtered=remember(entries,query,filter) { entries.filter { (filter==0 || it.isPlaylist==(filter==1)) && (it.name.contains(query,true)||it.subtitle.contains(query,true)) } }
    LazyColumn(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding(),contentPadding=PaddingValues(bottom=180.dp)) {
        item { StudioHeader(navController,"Your collection.") }
        item {
            Column(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                StudioAction("Favorites","The tracks you keep close",Icons.Default.FavoriteBorder) { navController.navigate(Routes.Liked.route) }
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { StudioAction("Offline","Downloads",Icons.Default.Download) { navController.navigate(Routes.Downloads.route) } }
                    Box(Modifier.weight(1f)) { StudioAction("On device","Local music",Icons.Default.FolderOpen) { navController.navigate(Routes.LocalFiles.route) } }
                }
                OutlinedTextField(query,{query=it},label={Text("Find in your collection")},singleLine=true,shape=StudioShape,modifier=Modifier.fillMaxWidth(),leadingIcon={Icon(Icons.Default.Search,null)})
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("All","Playlists","Albums","Artists").forEachIndexed { i,label -> StudioChip(label,filter==i) {filter=i} }
                }
            }
        }
        if(filter!=3) items(filtered,key={ it.spotifyId+it.name }) { e ->
            StudioMediaRow(e.name,e.subtitle,e.coverUri) {
                when(e.spotifyId) {
                    "liked-songs" -> navController.navigate(Routes.Liked.route)
                    "downloaded-offline" -> navController.navigate(Routes.Downloads.route)
                    else -> if(e.isPlaylist) navController.navigate(playlistRoute(e.spotifyId,e.name)) else navController.navigate(albumRoute(e.name,e.artists))
                }
            }
        }
        if(filter==3) items(artists.filter {it.name.contains(query,true)},key={it.id}) { a -> StudioMediaRow(a.name,"Following",a.coverUri) { navController.navigate(artistRoute(a.name,a.id)) } }
        if(response is Response.Loading) item { Box(Modifier.fillMaxWidth().padding(30.dp),contentAlignment=Alignment.Center) {CircularProgressIndicator()} }
        item { Spacer(Modifier.height(10.dp)) }
    }
}
@Composable private fun rememberSaveableStudio() = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
