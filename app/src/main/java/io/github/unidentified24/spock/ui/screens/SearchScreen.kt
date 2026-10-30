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
fun SearchScreen(navController:NavController) {
    val vm:SearchViewModel=hiltViewModel(); val response by vm.results.collectAsState()
    val result=(response as? Response.Success)?.data ?: SearchResults()
    val context=LocalContext.current; var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }; var menu by remember { mutableStateOf<SongsModel?>(null) }
    LaunchedEffect(query) { vm.search(query) }
    menu?.let {SongOptionsSheet(it,navController,context,onDismiss={menu=null})}
    LazyColumn(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding(),contentPadding=PaddingValues(bottom=180.dp)) {
        item { StudioHeader(navController,"Follow your curiosity.") }
        item {
            Column(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(query,{query=it},placeholder={Text("Songs, artists, albums…")},singleLine=true,shape=StudioShape,modifier=Modifier.fillMaxWidth(),
                    leadingIcon={Icon(Icons.Default.Search,null)},trailingIcon={if(query.isNotEmpty()) IconButton(onClick={query=""}) {Icon(Icons.Default.Close,"Clear search")}})
                if(query.isNotBlank()) Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("All","Tracks","Artists","Albums","Podcasts").forEachIndexed {i,label->StudioChip(label,tab==i){tab=i}}
                }
            }
        }
        if(query.isBlank()) {
            item { StudioSection("Find a mood","Choose a direction. See where it takes you.") }
            items(listOf("Focus" to "focus","After hours" to "chill","Turn it up" to "workout","New horizons" to "indie","Pop culture" to "pop","Deep cuts" to "jazz")) { (label,key) ->
                Box(Modifier.padding(horizontal=20.dp,vertical=5.dp)) {StudioAction(label,"Explore playlists",Icons.Default.NorthEast) {navController.navigate(categoryRoute(key,label))}}
            }
        } else {
            if(response is Response.Loading) item {LinearProgressIndicator(Modifier.fillMaxWidth().padding(20.dp),color=AppPalette)}
            if(tab==0||tab==1) {
                if(result.songs.isNotEmpty()) item {StudioSection("Tracks")}
                itemsIndexed(result.songs,key={i,s->"song:${s.id}:$i"}) {i,s->StudioTrack(s,i,vm.currentSongId.value==s.id,{
                    vm.startRadioFromSong(s);vm.updateSongState(s.coverUri,s.title,s.singer,true,s.id,0,s.album);SongPlayer.playSong(s.url,context)
                    addRecentItem(context,RecentItem(type="song",key=s.spotifyTrackId.ifBlank{s.id.toString()},name=s.title,singer=s.singer,image=s.coverUri,songId=s.id,songAlbum=s.album,songUrl=s.url,spotifyTrackId=s.spotifyTrackId,explicit=s.explicit,durationMs=s.durationMs))
                },{menu=s})}
            }
            if(tab==0||tab==2) items(result.artists) {a->StudioMediaRow(a.name,"Artist",a.coverUri){navController.navigate(artistRoute(a.name,a.id))}}
            if(tab==0||tab==3) items(result.albums) {a->StudioMediaRow(a.name,a.artists,a.coverUri){navController.navigate(albumRoute(a.name,a.artists))}}
            if(tab==0||tab==4) {
                items(result.shows) {s->StudioMediaRow(s.name,"Podcast",s.coverUri){navController.navigate(showRoute(s.id,s.name))}}
                itemsIndexed(result.episodes) {i,s->StudioTrack(s,i,false,{
                    vm.updateQueue(result.episodes);vm.updateSongState(s.coverUri,s.title,s.singer,true,s.id,i,s.album);SongPlayer.playSong(s.url,context)
                },{menu=s})}
            }
            if(response is Response.Error) item {StudioEmpty("Search couldn't connect","Check your network or reconnect Spotify from Account.")}
            else if(response is Response.Success && result==SearchResults()) item {StudioEmpty("No matches yet","Try a song title and artist together.")}
        }
    }
}
