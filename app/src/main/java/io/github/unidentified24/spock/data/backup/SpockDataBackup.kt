package io.github.unidentified24.spock.data.backup

import android.content.Context
import android.net.Uri
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import io.github.unidentified24.spock.data.preferences.ListeningMeter

object SpockDataBackup {
    data class Prepared(val values:Map<String,Map<String,JsonElement>>) {val items:Int get()=values.values.sumOf{it.size}}
    suspend fun export(context:Context,uri:Uri)=withContext(Dispatchers.IO){
        ListeningMeter.flush(context)
        context.getSharedPreferences("listening_meter",0).edit().putString("ledger",ListeningMeter.exportLedger(context)).commit()
        val data=SpockDataCodec.sections.associateWith{section->context.getSharedPreferences(section,0).all.mapNotNull{(key,value)->
            if(!SpockDataCodec.allowed(section,key))null else when(value){
                is String->if(section=="spock_features"&&key=="options")runCatching{
                    val options=io.github.unidentified24.spock.features.FeatureSettings.json.decodeFromString<io.github.unidentified24.spock.features.AppOptions>(value)
                    key to JsonPrimitive(io.github.unidentified24.spock.features.FeatureSettings.json.encodeToString(io.github.unidentified24.spock.features.FeatureSettings.portable(options)))
                }.getOrNull() else key to JsonPrimitive(value)
                is Boolean->key to JsonPrimitive(value);is Int->key to JsonPrimitive(value);else->null
            }
        }.toMap()}
        io.github.unidentified24.spock.features.library.LibraryStore.flush()
        val shelf=portableShelf(io.github.unidentified24.spock.features.library.LibraryStore.state.value)
        val bytes=SpockDataCodec.encode(data+mapOf("library_shelf" to mapOf("shelf" to JsonPrimitive(io.github.unidentified24.spock.features.FeatureSettings.json.encodeToString(shelf))))).toByteArray(Charsets.UTF_8)
        checkNotNull(context.contentResolver.openOutputStream(uri,"wt")){"Couldn't create backup"}.use{it.write(bytes)}
    }
    suspend fun prepare(context:Context,uri:Uri):Prepared=withContext(Dispatchers.IO){
        val bytes=checkNotNull(context.contentResolver.openInputStream(uri)){"Couldn't open backup"}.use{stream->
            val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true){val count=stream.read(buffer);if(count<0)break;require(out.size()+count<=SpockDataCodec.MAX_BYTES){"Backup is too large"};out.write(buffer,0,count)};out.toByteArray()
        }
        Prepared(SpockDataCodec.decode(bytes.toString(Charsets.UTF_8)))
    }
    suspend fun restore(context:Context,backup:Prepared,sections:Set<String> = backup.values.keys,merge:Boolean=false)=withContext(Dispatchers.IO){
        val selected=sections.intersect(backup.values.keys)
        require(selected.isNotEmpty()){"Select at least one section to restore"}
        val incoming=backup.values.filterKeys{it in selected}.mapValues{(section,values)->
            if(section !in setOf("spock_features","library_shelf"))values else values.mapValues{(key,value)->
                if(section=="spock_features"&&key=="options") {
                    val parsed=io.github.unidentified24.spock.features.FeatureSettings.json.decodeFromString<io.github.unidentified24.spock.features.AppOptions>(value.jsonPrimitive.content)
                    JsonPrimitive(io.github.unidentified24.spock.features.FeatureSettings.json.encodeToString(io.github.unidentified24.spock.features.FeatureSettings.portable(parsed)))
                }else if(section=="library_shelf"&&key=="shelf"){
                    val parsed=io.github.unidentified24.spock.features.FeatureSettings.json.decodeFromString<io.github.unidentified24.spock.features.library.LibraryShelf>(value.jsonPrimitive.content)
                    JsonPrimitive(io.github.unidentified24.spock.features.FeatureSettings.json.encodeToString(portableShelf(parsed)))
                }else value
            }
        }.toMutableMap()
        if(merge&&"library_shelf" in incoming){
            io.github.unidentified24.spock.features.library.LibraryStore.flush()
            val old=io.github.unidentified24.spock.features.library.LibraryStore.state.value
            val added=io.github.unidentified24.spock.features.FeatureSettings.json.decodeFromString<io.github.unidentified24.spock.features.library.LibraryShelf>(incoming.getValue("library_shelf").getValue("shelf").jsonPrimitive.content)
            val playlists=(old.playlists.filterNot{current->added.playlists.any{it.id==current.id}}+added.playlists).distinctBy{it.id}
            val merged=portableShelf(old.copy(playlists=playlists,folders=(old.folders+added.folders).distinct()))
            incoming["library_shelf"]=mapOf("shelf" to JsonPrimitive(io.github.unidentified24.spock.features.FeatureSettings.json.encodeToString(merged)))
        }
        if(merge && "listening_meter" in incoming){
            ListeningMeter.flush(context)
            val old=runCatching{Json.parseToJsonElement(ListeningMeter.exportLedger(context)).jsonObject}.getOrDefault(JsonObject(emptyMap()))
            val section=incoming["listening_meter"].orEmpty().toMutableMap()
            val key="ledger";val freshRaw=(section[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val fresh=runCatching{Json.parseToJsonElement(freshRaw).jsonObject}.getOrDefault(JsonObject(emptyMap()))
            val combined=old.toMutableMap()
            fresh.forEach{(id,value)->
                val before=old[id]?.jsonObject;val after=value.jsonObject
                if(before==null)combined[id]=value else combined[id]=buildJsonObject{
                    (before.keys+after.keys).forEach{field->
                        val a=before[field];val b=after[field]
                        if(field in setOf("ms","plays"))put(field,JsonPrimitive(((a?.jsonPrimitive?.longOrNull ?: 0)+(b?.jsonPrimitive?.longOrNull ?: 0)).coerceAtLeast(0)))
                        else put(field,b ?: a ?: JsonNull)
                    }
                }
            }
            section[key]=JsonPrimitive(JsonObject(combined).toString());incoming["listening_meter"]=section
        }
        val applySelected={
            val old=backup.values.keys.associateWith{context.getSharedPreferences(it,0).all.toMap()}
            try {
                incoming.forEach{(section,values)->
                    val pref=context.getSharedPreferences(section,0);val editor=pref.edit()
                    if(!merge)pref.all.keys.filter{SpockDataCodec.allowed(section,it)}.forEach{editor.remove(it)}
                    values.forEach{(key,value)->val v=value.jsonPrimitive
                        when{v.isString->editor.putString(key,v.content);v.booleanOrNull!=null->editor.putBoolean(key,v.boolean);else->editor.putInt(key,v.int)}
                    }
                    check(editor.commit()){"Couldn't save restored data"}
                }
            }catch(error:Exception){
                old.forEach{(section,values)->context.getSharedPreferences(section,0).edit().clear().apply{values.forEach{(k,v)->putOriginal(k,v)}}.commit()}
                throw error
            }
        }
        if("listening_meter" in selected)ListeningMeter.replaceFromBackup(context,applySelected) else applySelected()
        if("library_shelf" in selected)io.github.unidentified24.spock.features.library.LibraryStore.reload()
    }

    private fun portableShelf(shelf:io.github.unidentified24.spock.features.library.LibraryShelf):io.github.unidentified24.spock.features.library.LibraryShelf=
        shelf.copy(playlists=shelf.playlists.take(500).mapNotNull{playlist->
            val entries=playlist.entries.take(20_000).mapNotNull{entry->
                val song=entry.song
                if(!song.spotifyTrackId.matches(Regex("[A-Za-z0-9]{22}")))null else entry.copy(song=song.copy(
                    url=io.github.unidentified24.spock.di.SongPlayer.buildSpotifyPlayQuery(song.spotifyTrackId,song.title,song.singer),
                    coverUri=song.coverUri.takeIf{it.startsWith("https://")}.orEmpty(),
                ))
            }
            playlist.copy(cover=playlist.cover.takeIf{it.startsWith("https://")}.orEmpty(),entries=entries)
        },folders=shelf.folders.take(200).map{it.take(100)})
    private fun SharedPreferences.Editor.putOriginal(k:String,v:Any?){when(v){is String->putString(k,v);is Boolean->putBoolean(k,v);is Int->putInt(k,v);is Long->putLong(k,v);is Float->putFloat(k,v);is Set<*>->putStringSet(k,v.filterIsInstance<String>().toSet())}}
}
