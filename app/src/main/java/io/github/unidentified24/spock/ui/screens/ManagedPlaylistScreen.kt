package io.github.unidentified24.spock.ui.screens

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import io.github.unidentified24.spock.features.library.*
import io.github.unidentified24.spock.data.entity.OfflinePlaylist
import io.github.unidentified24.spock.data.preferences.*
import io.github.unidentified24.spock.ui.components.*

@Composable fun ManagedPlaylistScreen(nav:NavController,id:String){
    val shelf by LibraryStore.state.collectAsState();val playlist=shelf.playlists.firstOrNull{it.id==id}
    val context=LocalContext.current.applicationContext
    var rename by remember{mutableStateOf(false)};var name by remember(id){mutableStateOf("")};var remove by remember{mutableStateOf(false)}
    var adding by remember{mutableStateOf(false)}
    if(adding)TrackPickerDialog({adding=false}){songs->LibraryStore.update(id){PlaylistTools.merge(it,songs,System.currentTimeMillis())}}
    if(playlist==null){StudioCollection(nav,"Playlist","","",emptyList(),emptyTitle="Playlist no longer exists",emptyDescription="Go back to Collection to find your music.");return}
    val songs=playlist.entries.map{it.song.copy(addedAt=it.addedAt)}
    StudioCollection(nav,playlist.name,playlist.folder.ifBlank{"Spock playlist"},playlist.cover.ifBlank{songs.firstOrNull()?.coverUri.orEmpty()},songs,
        extra={
            CollectionAppearance(playlist)
            TextButton(onClick={adding=true}){Text("Add tracks")}
            PlaylistDownloads(OfflinePlaylist(playlist.id,playlist.name,playlist.cover,songs),nav)
            TextButton(onClick={name=playlist.name;rename=true}){Text("Rename playlist")}
            TextButton(onClick={remove=true}){Text("Delete playlist")}
        },onBulkRemove={selected->val ids=selected.map{it.id}.toSet();LibraryStore.update(id){p->p.copy(entries=p.entries.filter{it.song.id !in ids})};true},
        onDeduplicate={LibraryStore.update(id){PlaylistTools.deduplicate(it,System.currentTimeMillis())};true})
    if(rename)AlertDialog(onDismissRequest={rename=false},title={Text("Rename playlist")},text={OutlinedTextField(name,{name=it.take(100)},singleLine=true,label={Text("Name")})},confirmButton={TextButton(enabled=name.isNotBlank(),onClick={LibraryStore.update(id){it.copy(name=name.trim())};rename=false}){Text("Save")}},dismissButton={TextButton(onClick={rename=false}){Text("Cancel")}})
    if(remove)AlertDialog(onDismissRequest={remove=false},title={Text("Delete this playlist?")},text={Text("Its saved audio stays on your device.")},confirmButton={TextButton(onClick={LibraryStore.remove(id);forgetOfflinePlaylist(context,id);remove=false;nav.navigateUp()}){Text("Delete")}},dismissButton={TextButton(onClick={remove=false}){Text("Keep")}})
}
