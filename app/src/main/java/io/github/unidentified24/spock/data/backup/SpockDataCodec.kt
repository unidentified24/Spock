package io.github.unidentified24.spock.data.backup

import kotlinx.serialization.json.*

/** Portable preferences only. Credentials, URIs, audio files and device caches are never accepted. */
object SpockDataCodec {
    const val MAX_BYTES=10*1024*1024
    private val settings=mapOf("stream_quality_wifi" to "s","stream_quality_cellular" to "s","download_quality" to "s","preload_enabled" to "b","crossfade_duration_ms" to "i","crossfade_dj_mode" to "b","spock_hybrid_playback" to "b","video_fallback_enabled" to "b","library_grid_view" to "b","lyric_translate_lang" to "s","source_youtube" to "b","source_flac" to "b","soundcloud_enabled" to "b","flac_wifi_requested" to "b")
    private val fixed=mapOf("appearance" to mapOf("theme" to "s","layout" to "s","player_view" to "s"),"settings_prefs" to settings,"ListeningHistory" to mapOf("entries" to "s"),"listening_meter" to mapOf("ledger" to "s"),"LibraryCache" to mapOf("entries" to "s","followedArtists" to "s"))
    val sections=fixed.keys+setOf("LikedSongs","LikedAlbums","FollowedArtists")
    fun allowed(section:String,key:String)=if(section in setOf("LikedSongs","LikedAlbums","FollowedArtists"))key.isNotBlank()&&key.length<=512&&key.none{it.isISOControl()} else fixed[section]?.containsKey(key)==true
    private fun valid(section:String,key:String,v:JsonElement):Boolean {
        if(!allowed(section,key)||v !is JsonPrimitive||v is JsonNull)return false
        val type=fixed[section]?.get(key) ?: "s"
        if(type=="b")return !v.isString&&v.booleanOrNull!=null
        if(type=="i")return !v.isString&&v.intOrNull?.let{it in 0..12000}==true
        if(!v.isString||v.content.length>MAX_BYTES/2)return false
        val value=v.content
        if(section=="settings_prefs")return when(key){"lyric_translate_lang"->value.length<=12;else->value in setOf("LOW","NORMAL","HIGH","LOSSLESS")}
        if(section=="appearance")return when(key){"layout"->value in setOf("STUDIO","COMPACT","CLASSIC");"player_view"->value in setOf("ARTWORK","DISC","WAVES","SPECTRUM","ORBIT");else->value.length<=32}
        if(key=="entries"||key=="followedArtists")return runCatching{Json.parseToJsonElement(value) is JsonArray}.getOrDefault(false)
        if(section=="listening_meter")return runCatching{
            val rows=Json.parseToJsonElement(value).jsonObject
            rows.values.all {row-> val o=row.jsonObject;java.time.LocalDate.parse(o.getValue("date").jsonPrimitive.content);o.getValue("ms").jsonPrimitive.long in 0..86_400_000L&&o.getValue("songId").jsonPrimitive.intOrNull!=null }
        }.getOrDefault(false)
        return true
    }
    fun encode(values:Map<String,Map<String,JsonElement>>):String {
        val portable=values.filterKeys{it in sections}.mapValues{(section,map)->JsonObject(map.filter{(k,v)->valid(section,k,v)})}
        return JsonObject(mapOf("format" to JsonPrimitive("spockdata"),"version" to JsonPrimitive(1),"data" to JsonObject(portable))).toString().also{require(it.toByteArray().size<=MAX_BYTES){"Backup is too large"}}
    }
    fun decode(text:String):Map<String,Map<String,JsonElement>> {
        require(text.toByteArray().size<=MAX_BYTES){"Backup is too large"}
        val root=Json.parseToJsonElement(text).jsonObject
        require(root["format"]?.jsonPrimitive?.content=="spockdata"){"Choose a Spock backup"}
        require(root["version"]?.jsonPrimitive?.intOrNull==1){"Unsupported backup version"}
        val data=root.getValue("data").jsonObject
        require(data.isNotEmpty()&&data.keys.all{it in sections}){"Unsupported backup section"}
        return data.mapValues{(section,value)->value.jsonObject.also{map->require(map.size<=50000&&map.all{(k,v)->valid(section,k,v)}){"Invalid backup data"}}}
    }
}
