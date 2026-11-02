package io.github.unidentified24.spock.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.unidentified24.spock.data.api.*
import io.github.unidentified24.spock.data.entity.*
import io.github.unidentified24.spock.data.preferences.*
import io.github.unidentified24.spock.di.ListeningState
import io.github.unidentified24.spock.features.*
import io.github.unidentified24.spock.features.discovery.*
import io.github.unidentified24.spock.features.downloads.*
import io.github.unidentified24.spock.ui.components.*
import io.github.unidentified24.spock.ui.navigation.*
import io.github.unidentified24.spock.ui.theme.*
import kotlinx.coroutines.*

@OptIn(ExperimentalLayoutApi::class)
@Composable fun DiscoveryScreen(nav:NavController,initialKind:String="forgotten"){
    val context=LocalContext.current.applicationContext;val scope=rememberCoroutineScope();val options by FeatureSettings.state.collectAsState()
    val history by DiscoveryHistory.state.collectAsState()
    var kind by remember(initialKind){mutableStateOf(initialKind.takeIf{it in setOf("forgotten","artists","filtered","offline","similar")} ?: "forgotten")};var artistQuery by remember{mutableStateOf("")}
    var artists by remember{mutableStateOf<List<ArtistsModel>>(emptyList())};var selected by remember{mutableStateOf<Map<String,String>>(emptyMap())}
    var excluded by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var message by remember{mutableStateOf("")}
    LaunchedEffect(artistQuery,options.downloads.offlineOnly,options.discovery.offlineMixes){
        val localOnly=options.downloads.offlineOnly||options.discovery.offlineMixes
        artists=withContext(Dispatchers.IO){(if(localOnly)getDownloadedSongs(context)else TrackCatalog.read(context)).flatMap{song->
            if(song.artistNames.isNotEmpty())song.artistNames.mapIndexedNotNull{index,name->val id=song.artistIds.getOrNull(index);if(id!=null)ArtistsModel(name,song.coverUri,id)else if(localOnly)ArtistsModel(name,song.coverUri,"name:"+name)else null}
            else if(localOnly)listOf(ArtistsModel(song.singer,song.coverUri,"name:"+song.singer))else emptyList()
        }.filter{artistQuery.isBlank()||it.name.contains(artistQuery,true)}.distinctBy{it.id}.take(20)}
        if(artistQuery.length>=2&&!localOnly){delay(300);try{val remote=withContext(Dispatchers.IO){Api(context).searchArtistsForMix(artistQuery)};artists=(remote+artists).distinctBy{it.id}}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){}}
    }
    fun generate(){busy=true;message="";scope.launch{
        try{
            val mix=withContext(Dispatchers.IO){withTimeout(90000){
                val rules=FeatureSettings.value;val downloaded=getDownloadedSongs(context);val localOnly=kind=="offline"||rules.discovery.offlineMixes||rules.downloads.offlineOnly
                var catalog=(TrackCatalog.read(context)+getLocalSongs(context)+downloaded).distinctBy{it.id}
                val rows=ListeningMeter.samples(context);val api=Api(context)
                val candidates=when(kind){
                    "artists"->if(localOnly)downloaded.filter{song->song.artistIds.any{it in selected}||song.artistNames.any{name->name in selected.values}||song.singer in selected.values}else api.artistMix(selected.keys.toList())
                    "offline"->downloaded.shuffled()
                    "forgotten"->{
                        if(!localOnly&&SpotifySession.spDc(context).isNotBlank()){val liked=SmartOffline.full(api.getLikedSongs());TrackCatalog.store(context,liked);catalog=(liked+catalog).distinctBy{it.id}}
                        DiscoveryPolicy.forgotten(catalog,getLikedSongIds(context),rows,java.time.LocalDate.now())
                    }
                    "filtered"->if(localOnly)downloaded.shuffled()else api.discoveryTracks(rules.discovery.genre,rules.discovery.decade)
                    else->{val seeds=(ListeningState.current?.queue?.value.orEmpty()+catalog).map{it.spotifyTrackId}.filter{it.isNotBlank()}.distinct().take(5);if(localOnly)downloaded.shuffled()else api.getRecommendations(seeds)}
                }
                val eligible=if(localOnly)candidates.filter{song->downloaded.any{it.id==song.id}}else candidates
                val filtered=DiscoveryPolicy.filter(eligible,rules.discovery,rows,java.time.LocalDate.now(),rules.privacy.explicitFilter,applyGenre=localOnly||kind!="filtered").take(100)
                if(filtered.isEmpty())null else DiscoveryHistory.save(when(kind){"artists"->selected.values.take(2).joinToString(" + ")+" mix";"offline"->"Downloaded discoveries";"filtered"->listOf(rules.discovery.genre,rules.discovery.decade.takeIf{it>0}?.let{"${it}s"}.orEmpty()).filter{it.isNotBlank()}.joinToString(" · ")+" mix";"similar"->"Similar sounds";else->"Forgotten favorites"},filtered,localOnly)
            }}
            if(mix==null)message="No tracks matched. Try broader filters, save more music, or check your Spotify connection."
            else nav.navigate(discoveryMixRoute(mix.id))
        }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){message="Couldn't make this mix. Your saved music is still available."}finally{busy=false}
    }}
    LazyColumn(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding(),contentPadding=PaddingValues(bottom=32.dp)){
        item{StudioHeader(nav,"Find your next favorite.",back=true)}
        item{Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            PreferenceChoice("Make a mix",kind,listOf("forgotten" to "Forgotten favorites","artists" to "Selected artists","filtered" to "Genre & decade","offline" to "Downloaded music","similar" to "Similar sounds & albums")){kind=it}
            if(kind=="artists"){
                OutlinedTextField(artistQuery,{artistQuery=it.take(120)},label={Text("Find artists")},singleLine=true,modifier=Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){selected.forEach{(id,name)->StudioChip("$name ×",true){selected=selected-id}}}
                artists.forEach{artist->TextButton(enabled=artist.id in selected||selected.size<5,onClick={selected=if(artist.id in selected)selected-artist.id else selected+(artist.id to artist.name)}){Text(artist.name+(if(artist.id in selected)" ✓" else ""))}}
            }
            PreferenceChoice("Genre",options.discovery.genre,listOf("" to "Any genre")+listOf("pop","rock","hip-hop","electronic","jazz","classical","indie","r&b","country","metal","folk","ambient").map{it to it.replaceFirstChar{c->c.uppercase()}}){value->FeatureSettings.update{it.copy(discovery=it.discovery.copy(genre=value))}}
            PreferenceChoice("Decade",options.discovery.decade.toString(),listOf("0" to "Any decade")+(2020 downTo 1950 step 10).map{it.toString() to "${it}s"}){value->FeatureSettings.update{it.copy(discovery=it.discovery.copy(decade=value.toInt()))}}
            PreferenceChoice("Avoid recent repeats",options.discovery.avoidRepeatDays.toString(),listOf(0,1,3,7,14,30,90).map{it.toString() to if(it==0)"Off" else "$it days"}){value->FeatureSettings.update{it.copy(discovery=it.discovery.copy(avoidRepeatDays=value.toInt()))}}
            PreferenceSwitch("Use downloaded music only",options.discovery.offlineMixes){value->FeatureSettings.update{it.copy(discovery=it.discovery.copy(offlineMixes=value))}}
            Button(enabled=!busy&&(kind!="artists"||selected.isNotEmpty()),onClick=::generate){Text(if(busy)"Making your mix…" else "Make mix")}
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(message.isNotBlank())Text(message,color=TextMuted)
            StudioCard(Modifier.fillMaxWidth()){
                Text("Artists to leave out",style=MaterialTheme.typography.titleLarge)
                OutlinedTextField(excluded,{excluded=it.take(128)},label={Text("Artist name")},singleLine=true,modifier=Modifier.fillMaxWidth())
                TextButton(enabled=excluded.isNotBlank(),onClick={val value=excluded.trim();FeatureSettings.update{it.copy(discovery=it.discovery.copy(excludedArtists=it.discovery.excludedArtists+value))};excluded=""}){Text("Exclude artist")}
                options.discovery.excludedArtists.forEach{name->TextButton(onClick={FeatureSettings.update{it.copy(discovery=it.discovery.copy(excludedArtists=it.discovery.excludedArtists-name))}}){Text("$name · remove")}}
            }
        }}
        if(history.isNotEmpty())item{StudioSection("Your discovery history")}
        items(history,key={it.id}){mix->
            StudioMediaRow(mix.name,"${mix.songs.size} tracks · "+java.text.DateFormat.getDateInstance().format(java.util.Date(mix.createdAt)),mix.songs.firstOrNull()?.coverUri.orEmpty()){nav.navigate(discoveryMixRoute(mix.id))}
            TextButton(onClick={scope.launch(Dispatchers.IO){DiscoveryHistory.remove(mix.id)}},modifier=Modifier.padding(horizontal=20.dp)){Text("Remove from history")}
        }
    }
}

@Composable fun DiscoveryMixScreen(nav:NavController,id:String){
    val history by DiscoveryHistory.state.collectAsState();val mix=history.firstOrNull{it.id==id};val context=LocalContext.current.applicationContext
    var saved by remember{mutableStateOf<Set<Int>>(emptySet())};var albums by remember{mutableStateOf(false)}
    LaunchedEffect(id){saved=withContext(Dispatchers.IO){getDownloadedSongs(context).map{it.id}.toSet()}}
    val songs=if(mix?.downloadedOnly==true)mix.songs.filter{it.id in saved}else mix?.songs.orEmpty()
    StudioCollection(nav,mix?.name ?: "Mix","Made for your music",songs.firstOrNull()?.coverUri.orEmpty(),songs,
        extra={PlaylistDownloads(OfflinePlaylist("pack-mix-$id",mix?.name ?: "Mix",songs.firstOrNull()?.coverUri.orEmpty(),songs),nav);TextButton(onClick={albums=!albums}){Text("Explore similar albums")};if(albums)songs.filter{it.album.isNotBlank()}.distinctBy{it.albumId.ifBlank{it.album+it.singer}}.take(12).forEach{song->TextButton(onClick={nav.navigate(albumRoute(song.album,song.singer))}){Text(song.album)}}},
        emptyTitle="No tracks available",emptyDescription="Create another mix or download these tracks again.")
}
