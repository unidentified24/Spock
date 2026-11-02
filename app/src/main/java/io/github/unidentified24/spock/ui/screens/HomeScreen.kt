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


private data class HomeData(val catalog:List<SongsModel> = emptyList(),val rows:List<ListeningSample> = emptyList(),val last:SongsModel?=null,val saved:Set<Int> = emptySet())

@Composable fun HomeScreen(navController:NavController) {
    val context=LocalContext.current.applicationContext
    val options by io.github.unidentified24.spock.features.FeatureSettings.state.collectAsState()
    val shelf by io.github.unidentified24.spock.features.library.LibraryStore.state.collectAsState()
    val player:PlayerViewModel=hiltViewModel();var data by remember{mutableStateOf(HomeData())};var customize by remember{mutableStateOf(false)}
    val offline=options.downloads.offlineOnly
    val feed=if(offline)null else {val vm:HomeViewModel=hiltViewModel();val response by vm.home.collectAsState();(response as? Response.Success)?.data}
    LaunchedEffect(shelf.playlists,offline){while(true){
        data=withContext(Dispatchers.IO){
            val downloaded=getDownloadedSongs(context)
            HomeData((io.github.unidentified24.spock.features.downloads.TrackCatalog.read(context)+shelf.playlists.flatMap{p->p.entries.map{it.song.copy(addedAt=it.addedAt)}}+getLocalSongs(context)+downloaded).groupBy{it.id}.map{(_,songs)->songs.maxBy{it.addedAt}},ListeningMeter.samples(context),loadLastPlayback(context)?.first,downloaded.map{it.id}.toSet())
        };delay(15000)
    }}
    if(customize)HomeCustomizer{customize=false}
    val fresh=remember(data,offline){data.catalog.filter{it.addedAt>0&&(!offline||it.id in data.saved||it.url.startsWith("file:")||it.url.startsWith("content:"))}.sortedByDescending{it.addedAt}.take(8)}
    val unfinished=remember(data){io.github.unidentified24.spock.features.stats.ListeningInsights.albums(data.rows,data.catalog).filter{it.total>0&&it.heard<it.total}.take(8)}
    fun shortcut(id:String){navController.navigate(when(id){"collection"->Routes.Library.route;"local"->Routes.LocalFiles.route;"mixes"->discoveryRoute();else->id}){launchSingleTop=true}}
    LazyColumn(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding(),contentPadding=PaddingValues(bottom=32.dp)) {
        item{StudioHeader(navController,if(offline)"Your music,\nanywhere." else if(currentLayout==SpockLayout.EDITORIAL)"The daily\nlistening edition." else "A sound of\nyour own.")}
        item{TextButton(onClick={customize=true},modifier=Modifier.padding(horizontal=20.dp)){Text("Customize home")}}
        for(section in options.home.order.filter{it !in options.home.hidden})when(section){
            "continue"->data.last?.let{last->item{
                StudioSection("Continue listening")
                StudioMediaRow(last.title,last.singer,last.coverUri){
                    if(player.currentSongId.value==last.id&&player.currentSongTitle.value.isNotBlank())navController.navigate(Routes.Player.route)
                    else{playStudioTrack(player,listOf(last),0,context);navController.navigate(Routes.Player.route)}
                }
            }}
            "pinned"->{
                val pins=shelf.playlists.filter{it.pinned}
                if(pins.isNotEmpty())item{StudioSection("Pinned playlists")}
                items(pins,key={"pinned:"+it.id}){playlist->StudioMediaRow(playlist.name,playlist.folder.ifBlank{"Pinned for you"},playlist.cover.ifBlank{playlist.entries.firstOrNull()?.song?.coverUri.orEmpty()}){navController.navigate(if(playlist.sourceId.isBlank())managedPlaylistRoute(playlist.id)else playlistRoute(playlist.sourceId,playlist.name))}}
            }
            "shortcuts"->if(options.home.shortcuts.isNotEmpty())item{
                StudioCard(Modifier.padding(horizontal=20.dp).fillMaxWidth()){
                    Text("IN YOUR ORBIT",color=AppPalette,style=MaterialTheme.typography.labelSmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){options.home.shortcuts.forEach{id->StudioChip(homeShortcutNames[id] ?: id){shortcut(id)}}}
                }
            }
            "fresh"->if(fresh.isNotEmpty()){
                item{StudioSection("Recently added")}
                itemsIndexed(fresh,key={i,song->"fresh:${song.id}:$i"}){index,song->StudioTrack(song,index,player.currentSongId.value==song.id,{playStudioTrack(player,fresh,index,context)},{navController.navigate(Routes.Library.route)})}
            }
            "forgotten"->item{Column(Modifier.padding(horizontal=20.dp,vertical=10.dp)){
                StudioAction("Forgotten favorites",if(offline)"Rediscover your saved favorites" else "Good songs deserve another turn",Icons.Default.AutoAwesome){navController.navigate(discoveryRoute("forgotten"))}
            }}
            "unfinished"->if(unfinished.isNotEmpty()){
                item{StudioSection("Albums to come back to")}
                items(unfinished,key={"unfinished:"+it.key}){album->StudioMediaRow(album.name,"${album.heard} of ${album.total} tracks heard",album.cover){navController.navigate(albumRoute(album.name,album.artist))}}
            }
            "recommendations"->if(offline)item{Column(Modifier.padding(horizontal=20.dp,vertical=10.dp)){
                StudioAction("An offline mix","A new order for your downloaded music",Icons.Default.Shuffle){navController.navigate(discoveryRoute("offline"))}
            }}else if(feed!=null){
                itemsIndexed(feed.sections,key={index,section->"feed:$index:${section.title}"}){index,section->
                    StudioSection(section.title.ifBlank{"Back in rotation"})
                    LazyRow(contentPadding=PaddingValues(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        itemsIndexed(section.items,key={i,item->"$i:${item.name}"}){_,item->
                            Column(Modifier.width(when(currentLayout){SpockLayout.GALLERY->280.dp;SpockLayout.EDITORIAL->if(index==0)320.dp else 220.dp;SpockLayout.MINIMAL->132.dp;else->if(index==0)214.dp else 156.dp}).clip(StudioShape).background(GridBackground).clickable{
                                when(item){is HomeItem.Album->navController.navigate(albumRoute(item.name,item.artists.ifBlank{item.subtitle}));is HomeItem.Artist->navController.navigate(artistRoute(item.name,item.id));is HomeItem.Playlist->if(item.id.isNotBlank())navController.navigate(playlistRoute(item.id,item.name))}
                            }.padding(10.dp)){
                                StudioCover(item.imageUrl,Modifier.fillMaxWidth().aspectRatio(when(currentLayout){SpockLayout.GALLERY->.8f;SpockLayout.EDITORIAL->1.6f;else->if(index==0)1.25f else 1f}))
                                Text(item.name,color=Color.White,style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=12.dp))
                                Text(when(item){is HomeItem.Album->item.subtitle;is HomeItem.Playlist->item.subtitle;is HomeItem.Artist->"Artist"},color=TextMuted,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }else item{Column(Modifier.padding(horizontal=20.dp,vertical=10.dp)){StudioAction("Find your next favorite","Make a mix or search your music",Icons.Default.Explore){navController.navigate(discoveryRoute("similar"))}}}
        }
    }
}
