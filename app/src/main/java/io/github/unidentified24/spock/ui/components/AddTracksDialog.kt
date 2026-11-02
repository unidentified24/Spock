package io.github.unidentified24.spock.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.unidentified24.spock.data.entity.SongsModel
import io.github.unidentified24.spock.data.api.*
import io.github.unidentified24.spock.features.library.*
import kotlinx.coroutines.*

@Composable fun AddTracksDialog(songs:List<SongsModel>,dismiss:()->Unit){
    val context=LocalContext.current.applicationContext;val scope=rememberCoroutineScope()
    val shelf by LibraryStore.state.collectAsState()
    var remote by remember{mutableStateOf<List<io.github.unidentified24.spock.data.entity.LibraryEntry>>(emptyList())}
    var destination by remember{mutableStateOf("new-local")};var name by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)};var message by remember{mutableStateOf("")}
    LaunchedEffect(Unit){withContext(Dispatchers.IO){try{Api(context).getLibrary().collect{if(it is Response.Success)remote=it.data.filter{entry->entry.isPlaylist&&entry.spotifyId !in setOf(Api.LIKED_SONGS_ID,Api.DOWNLOADS_ID)}}}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){}}}
    val choices=listOf("new-local" to "New Spock playlist")+shelf.playlists.filter{it.sourceId.isBlank()}.map{it.id to it.name}+listOf("new-spotify" to "New Spotify playlist")+remote.map{"spotify:"+it.spotifyId to "Spotify · ${it.name}"}
    AlertDialog(onDismissRequest={if(!busy)dismiss()},title={Text("Save ${songs.size} tracks")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        PreferenceChoice("Playlist",destination,choices){destination=it;message=""}
        if(destination.startsWith("new-"))OutlinedTextField(name,{name=it.take(100)},singleLine=true,label={Text("Playlist name")},modifier=Modifier.fillMaxWidth())
        if(message.isNotBlank())Text(message,style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(enabled=!busy&&songs.isNotEmpty()&&(!destination.startsWith("new-")||name.isNotBlank()),onClick={busy=true;message="";scope.launch{
        try{
            val result=withContext(Dispatchers.IO){
                when{
                    destination=="new-local"->{LibraryStore.create(name,songs);LibraryStore.flush();true}
                    destination.startsWith("local-")->{LibraryStore.update(destination){PlaylistTools.merge(it,songs,System.currentTimeMillis())};LibraryStore.flush();true}
                    songs.any{it.spotifyTrackId.isBlank()}->{message="Choose a Spock playlist for on-device tracks.";false}
                    else->{
                        val id=if(destination=="new-spotify"){
                            if(!SpotifyTokenProvider.ensureToken(context))null else com.metrolist.spotify.Spotify.createPlaylist(name.trim()).getOrNull()?.id
                        }else destination.removePrefix("spotify:")
                        if(id.isNullOrBlank()){message="Couldn't create the Spotify playlist. Check your connection.";false}
                        else{val result=SpotifySync.addMany(context,id,songs.map{it.spotifyTrackId});if(!result.complete)message="${result.changed} of ${result.total} tracks added. You may need to reconnect Spotify or choose a playlist you can edit.";result.complete}
                    }
                }
            }
            if(result){android.widget.Toast.makeText(context,"Tracks saved to playlist",android.widget.Toast.LENGTH_SHORT).show();dismiss()}
        }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){message="Couldn't save these tracks. Check your connection and try again."}finally{busy=false}
    }}){Text(if(busy)"Saving…" else "Save")}},dismissButton={TextButton(enabled=!busy,onClick=dismiss){Text("Cancel")}})
}
