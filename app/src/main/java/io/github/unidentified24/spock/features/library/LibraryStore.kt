package io.github.unidentified24.spock.features.library

import android.content.Context
import io.github.unidentified24.spock.data.entity.SongsModel
import io.github.unidentified24.spock.features.FeatureSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.*

object LibraryStore {
    private var preferences:android.content.SharedPreferences?=null
    val state=MutableStateFlow(LibraryShelf())
    val saveError=MutableStateFlow("")
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val diskLock=Any()
    @Volatile private var revision=0L
    @Synchronized fun initialize(context:Context){if(preferences==null){preferences=context.applicationContext.getSharedPreferences("library_shelf",0);reload()}}
    @Synchronized fun reload(){state.value=runCatching{FeatureSettings.json.decodeFromString<LibraryShelf>(preferences?.getString("shelf","{}") ?: "{}")}.getOrDefault(LibraryShelf())}
    @Synchronized private fun save(shelf:LibraryShelf){
        val prefs=checkNotNull(preferences);val ticket=++revision;state.value=shelf
        scope.launch{synchronized(diskLock){
            if(ticket!=revision)return@synchronized
            val ok=runCatching{prefs.edit().putString("shelf",FeatureSettings.json.encodeToString(shelf)).commit()}.getOrDefault(false)
            saveError.value=if(ok)"" else "Playlist changes couldn't be saved. Check your device's free space."
        }}
    }
    suspend fun flush()=withContext(Dispatchers.IO){synchronized(diskLock){val prefs=checkNotNull(preferences);check(prefs.edit().putString("shelf",FeatureSettings.json.encodeToString(state.value)).commit())}}
    @Synchronized fun create(name:String,songs:List<SongsModel> = emptyList(),folder:String=""):ManagedPlaylist {
        require(name.isNotBlank());val now=System.currentTimeMillis()
        val value=PlaylistTools.merge(ManagedPlaylist("local-"+java.util.UUID.randomUUID(),name.trim().take(100),folder=folder),songs,now)
        save(state.value.copy(playlists=state.value.playlists+value));return value
    }
    @Synchronized fun update(id:String,edit:(ManagedPlaylist)->ManagedPlaylist){save(state.value.copy(playlists=state.value.playlists.map{if(it.id==id)edit(it).copy(id=it.id,modifiedAt=System.currentTimeMillis())else it}))}
    @Synchronized fun remove(id:String){save(state.value.copy(playlists=state.value.playlists.filter{it.id!=id}))}
    @Synchronized fun folder(name:String){val value=name.trim().take(100);require(value.isNotBlank());save(state.value.copy(folders=(state.value.folders+value).distinct()))}
    @Synchronized fun removeFolder(name:String){save(state.value.copy(folders=state.value.folders-name,playlists=state.value.playlists.map{if(it.folder==name)it.copy(folder="")else it}))}
    @Synchronized fun rememberSource(id:String,name:String,cover:String,songs:List<SongsModel>):ManagedPlaylist {
        val old=state.value.playlists.firstOrNull{it.id==id}
        val now=System.currentTimeMillis();val stamps=old?.entries.orEmpty().associateBy{PlaylistTools.identity(it.song)}
        val entries=songs.map{song->val previous=stamps[PlaylistTools.identity(song)];PlaylistEntry(java.util.UUID.randomUUID().toString(),song,song.addedAt.takeIf{it>0} ?: previous?.addedAt ?: now)}
        val value=(old ?: ManagedPlaylist(id,name,sourceId=id)).copy(name=name,entries=entries,cover=old?.cover?.takeIf{it.isNotBlank()} ?: cover,modifiedAt=now)
        save(state.value.copy(playlists=state.value.playlists.filter{it.id!=id}+value));return value
    }
}
