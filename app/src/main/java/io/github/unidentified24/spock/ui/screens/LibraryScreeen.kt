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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(navController:NavController) {
    val vm:LibraryViewModel=hiltViewModel(); val response by vm.entries.collectAsState();val artists by vm.followedArtists.collectAsState()
    var query by rememberSaveableStudio();var filter by remember { mutableIntStateOf(0) }
    val shelf by io.github.unidentified24.spock.features.library.LibraryStore.state.collectAsState()
    val saveError by io.github.unidentified24.spock.features.library.LibraryStore.saveError.collectAsState()
    val appearances=remember(shelf){shelf.playlists.associateBy{it.id}}
    var folder by remember{mutableStateOf("*")}
    var creating by remember{mutableStateOf(false)};var playlistName by remember{mutableStateOf("")}
    val local=shelf.playlists.filter{it.sourceId.isBlank()&&(folder=="*"||it.folder==folder)&&(query.isBlank()||it.name.contains(query,true))}.sortedByDescending{it.pinned}
    val entries=CollectionIdentity.entries((response as? Response.Success)?.data.orEmpty()).filter{it.spotifyId !in setOf("liked-songs","downloaded-offline")}
    val filtered=remember(entries,query,filter,folder,shelf) { entries.filter { (folder=="*"||appearances[it.spotifyId]?.folder.orEmpty()==folder)&&(filter==0 || it.isPlaylist==(filter==1)) && (it.name.contains(query,true)||it.subtitle.contains(query,true)) }.sortedByDescending{entry->appearances[entry.spotifyId]?.pinned==true} }
    if(creating)AlertDialog(onDismissRequest={creating=false},title={Text("New playlist")},text={OutlinedTextField(playlistName,{playlistName=it.take(100)},label={Text("Name")},singleLine=true)},confirmButton={TextButton(enabled=playlistName.isNotBlank(),onClick={val playlist=io.github.unidentified24.spock.features.library.LibraryStore.create(playlistName,folder=folder.takeIf{it!="*"}.orEmpty());creating=false;navController.navigate(managedPlaylistRoute(playlist.id))}){Text("Create")}},dismissButton={TextButton(onClick={creating=false}){Text("Cancel")}})
    LazyColumn(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding(),contentPadding=PaddingValues(bottom=32.dp)) {
        item { StudioHeader(navController,"Your collection.") }
        if(saveError.isNotBlank())item{Text(saveError,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(20.dp))}
        item {
            Column(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                StudioAction("Favorites","The tracks you keep close",Icons.Default.FavoriteBorder) { navController.navigate(Routes.Liked.route) }
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { StudioShortcut("Downloads",Icons.Default.Download) { navController.navigate(Routes.Downloads.route) } }
                    Box(Modifier.weight(1f)) { StudioShortcut("On device",Icons.Default.FolderOpen) { navController.navigate(Routes.LocalFiles.route) } }
                }
                Row{TextButton(onClick={playlistName="";creating=true}){Text("New playlist")};TextButton(onClick={vm.load()},enabled=response !is Response.Loading){Text("Refresh")}}
                if(shelf.folders.isNotEmpty())PreferenceChoice("Folder",folder,listOf("*" to "All folders","" to "Unfiled")+shelf.folders.map{it to it}){folder=it}
                OutlinedTextField(query,{query=it},label={Text("Find in your collection")},singleLine=true,shape=StudioShape,modifier=Modifier.fillMaxWidth(),leadingIcon={Icon(Icons.Default.Search,null)})
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("All","Playlists","Albums","Artists").forEachIndexed { i,label -> StudioChip(label,filter==i) {filter=i} }
                }
            }
        }
        if(filter in 0..1&&local.isNotEmpty()){
            item{StudioSection("Spock playlists")}
            items(local,key={"local:"+it.id}){playlist->StudioMediaRow((if(playlist.pinned)"Pinned · " else "")+playlist.name,"${playlist.entries.size} tracks"+playlist.folder.takeIf{it.isNotBlank()}?.let{" · $it"}.orEmpty(),playlist.cover.ifBlank{playlist.entries.firstOrNull()?.song?.coverUri.orEmpty()}){navController.navigate(managedPlaylistRoute(playlist.id))}}
        }
        if(filter!=3) itemsIndexed(filtered,key={ i,e -> "collection:$i:${e.spotifyId}" }) { _,e ->
            StudioMediaRow((if(appearances[e.spotifyId]?.pinned==true)"Pinned · " else "")+e.name,e.subtitle,appearances[e.spotifyId]?.cover?.takeIf{it.isNotBlank()} ?: e.coverUri) {
                when(e.spotifyId) {
                    "liked-songs" -> navController.navigate(Routes.Liked.route)
                    "downloaded-offline" -> navController.navigate(Routes.Downloads.route)
                    else -> if(e.isPlaylist) navController.navigate(playlistRoute(e.spotifyId,e.name)) else navController.navigate(albumRoute(e.name,e.artists))
                }
            }
        }
        if(filter==3) itemsIndexed(CollectionIdentity.artists(artists).filter {it.name.contains(query,true)},key={i,a->"artist:$i:${a.id}"}) { _,a -> StudioMediaRow(a.name,"Following",a.coverUri) { navController.navigate(artistRoute(a.name,a.id)) } }
        if(response is Response.Error) item {StudioEmpty("Your online collection couldn't connect","Offline downloads and local music are available above. Reconnect Spotify from Connected music if needed.")}
        if(response is Response.Loading) item { Box(Modifier.fillMaxWidth().padding(30.dp),contentAlignment=Alignment.Center) {CircularProgressIndicator()} }
        item { Spacer(Modifier.height(10.dp)) }
    }
}
@Composable private fun rememberSaveableStudio() = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
