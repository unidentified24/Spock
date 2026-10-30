package io.github.unidentified24.spock.data.entity

import kotlinx.serialization.json.*

data class OfflinePlaylist(val id:String,val name:String,val coverUri:String,val songs:List<SongsModel>) {
    fun missingSongs(saved:Set<Int>)=songs.distinctBy{it.id}.filter{it.id !in saved && it.url.isNotBlank()}
    fun playable(saved:Map<Int,SongsModel>)=songs.mapNotNull{saved[it.id]}
}

object OfflinePlaylistCodec {
    private const val MAX_BYTES=8*1024*1024
    fun encode(playlist:OfflinePlaylist):String {
        require(playlist.id.isNotBlank() && playlist.songs.size<=10000)
        return buildJsonObject {
            put("version",1);put("id",playlist.id);put("name",playlist.name);put("coverUri",playlist.coverUri)
            put("songs",buildJsonArray{playlist.songs.forEach{s->add(buildJsonObject{
                put("id",s.id);put("title",s.title);put("album",s.album);put("singer",s.singer);put("coverUri",s.coverUri)
                put("url",s.url);put("spotifyTrackId",s.spotifyTrackId);put("explicit",s.explicit);put("durationMs",s.durationMs)
            })}})
        }.toString().also{require(it.toByteArray().size<=MAX_BYTES)}
    }
    fun decode(text:String):OfflinePlaylist?=runCatching {
        require(text.toByteArray().size<=MAX_BYTES)
        val root=Json.parseToJsonElement(text).jsonObject
        require(root.getValue("version").jsonPrimitive.int==1)
        val id=root.getValue("id").jsonPrimitive.content.also{require(it.isNotBlank())}
        val rows=root.getValue("songs").jsonArray.also{require(it.size<=10000)}
        OfflinePlaylist(id,root.getValue("name").jsonPrimitive.content,root.getValue("coverUri").jsonPrimitive.content,rows.map{row->
            val o=row.jsonObject
            fun text(key:String)=o.getValue(key).jsonPrimitive.content
            SongsModel(o.getValue("id").jsonPrimitive.int,text("title"),text("album"),text("singer"),text("coverUri"),text("url"),text("spotifyTrackId"),o.getValue("explicit").jsonPrimitive.boolean,o.getValue("durationMs").jsonPrimitive.int)
        })
    }.getOrNull()
}
