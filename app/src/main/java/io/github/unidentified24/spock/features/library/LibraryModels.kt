package io.github.unidentified24.spock.features.library

import io.github.unidentified24.spock.data.entity.SongsModel
import io.github.unidentified24.spock.playback.OfflineFiles
import kotlinx.serialization.Serializable

@Serializable data class PlaylistEntry(val key:String,val song:SongsModel,val addedAt:Long)
@Serializable data class ManagedPlaylist(
    val id:String,val name:String,val entries:List<PlaylistEntry> = emptyList(),val cover:String="",
    val folder:String="",val pinned:Boolean=false,val createdAt:Long=System.currentTimeMillis(),
    val modifiedAt:Long=createdAt,val sourceId:String="",
)
@Serializable data class LibraryShelf(val playlists:List<ManagedPlaylist> = emptyList(),val folders:List<String> = emptyList())

object PlaylistTools {
    fun identity(song:SongsModel)=song.spotifyTrackId.takeIf{it.isNotBlank()}?.let{"spotify:$it"} ?: OfflineFiles.key(song.url).ifBlank{"id:${song.id}"}
    fun duplicates(entries:List<PlaylistEntry>)=entries.groupBy{identity(it.song)}.filter{it.value.size>1}
    fun filtered(entries:List<PlaylistEntry>,query:String,sort:String,duplicatesOnly:Boolean=false):List<PlaylistEntry>{
        val duplicates=if(duplicatesOnly)duplicates(entries).keys else emptySet()
        val filtered=entries.filter{(!duplicatesOnly||identity(it.song) in duplicates)&&(query.isBlank()||listOf(it.song.title,it.song.singer,it.song.album).any{value->value.contains(query,true)})}
        return when(sort){"artist"->filtered.sortedBy{it.song.singer.lowercase()};"title"->filtered.sortedBy{it.song.title.lowercase()};"date"->filtered.sortedByDescending{it.addedAt};"duration"->filtered.sortedBy{it.song.durationMs};else->filtered}
    }
    fun merge(target:ManagedPlaylist,songs:List<SongsModel>,now:Long,allowDuplicates:Boolean=false):ManagedPlaylist {
        val existing=target.entries.map{identity(it.song)}.toMutableSet()
        val additions=songs.filter{allowDuplicates||existing.add(identity(it))}.map{PlaylistEntry(java.util.UUID.randomUUID().toString(),it,now)}
        return target.copy(entries=target.entries+additions,modifiedAt=now)
    }
    fun remove(target:ManagedPlaylist,keys:Set<String>,now:Long)=target.copy(entries=target.entries.filter{it.key !in keys},modifiedAt=now)
    fun deduplicate(target:ManagedPlaylist,now:Long)=target.copy(entries=target.entries.distinctBy{identity(it.song)},modifiedAt=now)
}
