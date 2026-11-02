package io.github.unidentified24.spock.data.entity

import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

data class OfflinePlaylist(val id:String,val name:String,val coverUri:String,val songs:List<SongsModel>) {
    fun missingSongs(saved:Set<Int>)=songs.distinctBy{it.id}.filter{it.id !in saved && it.url.isNotBlank()}
    fun playable(saved:Map<Int,SongsModel>)=songs.mapNotNull{saved[it.id]}
}

object OfflinePlaylistCodec {
    private val codec=Json{ignoreUnknownKeys=true}
    private const val MAX_BYTES=8*1024*1024
    fun encode(playlist:OfflinePlaylist):String {
        require(playlist.id.isNotBlank() && playlist.songs.size<=10000)
        return buildJsonObject {
            put("version",1);put("id",playlist.id);put("name",playlist.name);put("coverUri",playlist.coverUri)
            put("songs",buildJsonArray{playlist.songs.forEach{s->add(codec.encodeToJsonElement(s))}})
        }.toString().also{require(it.toByteArray().size<=MAX_BYTES)}
    }
    fun decode(text:String):OfflinePlaylist?=runCatching {
        require(text.toByteArray().size<=MAX_BYTES)
        val root=Json.parseToJsonElement(text).jsonObject
        require(root.getValue("version").jsonPrimitive.int==1)
        val id=root.getValue("id").jsonPrimitive.content.also{require(it.isNotBlank())}
        val rows=root.getValue("songs").jsonArray.also{require(it.size<=10000)}
        OfflinePlaylist(id,root.getValue("name").jsonPrimitive.content,root.getValue("coverUri").jsonPrimitive.content,rows.map{row->codec.decodeFromJsonElement<SongsModel>(row)}
        )
    }.getOrNull()
}
