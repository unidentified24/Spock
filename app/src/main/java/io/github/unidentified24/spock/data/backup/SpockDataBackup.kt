package io.github.unidentified24.spock.data.backup

import android.content.Context
import android.net.Uri
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import io.github.unidentified24.spock.data.preferences.ListeningMeter

object SpockDataBackup {
    data class Prepared(val values:Map<String,Map<String,JsonElement>>) {val items:Int get()=values.values.sumOf{it.size}}
    suspend fun export(context:Context,uri:Uri)=withContext(Dispatchers.IO){
        ListeningMeter.flush(context)
        val data=SpockDataCodec.sections.associateWith{section->context.getSharedPreferences(section,0).all.mapNotNull{(key,value)->
            if(!SpockDataCodec.allowed(section,key))null else when(value){is String->key to JsonPrimitive(value);is Boolean->key to JsonPrimitive(value);is Int->key to JsonPrimitive(value);else->null}
        }.toMap()}
        val bytes=SpockDataCodec.encode(data).toByteArray(Charsets.UTF_8)
        checkNotNull(context.contentResolver.openOutputStream(uri,"wt")){"Couldn't create backup"}.use{it.write(bytes)}
    }
    suspend fun prepare(context:Context,uri:Uri):Prepared=withContext(Dispatchers.IO){
        val bytes=checkNotNull(context.contentResolver.openInputStream(uri)){"Couldn't open backup"}.use{stream->
            val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true){val count=stream.read(buffer);if(count<0)break;require(out.size()+count<=SpockDataCodec.MAX_BYTES){"Backup is too large"};out.write(buffer,0,count)};out.toByteArray()
        }
        Prepared(SpockDataCodec.decode(bytes.toString(Charsets.UTF_8)))
    }
    suspend fun restore(context:Context,backup:Prepared)=withContext(Dispatchers.IO){
        ListeningMeter.replaceFromBackup(context){
            val old=backup.values.keys.associateWith{context.getSharedPreferences(it,0).all.toMap()}
            try {
                backup.values.forEach{(section,values)->
                    val pref=context.getSharedPreferences(section,0);val editor=pref.edit()
                    pref.all.keys.filter{SpockDataCodec.allowed(section,it)}.forEach{editor.remove(it)}
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
    }
    private fun SharedPreferences.Editor.putOriginal(k:String,v:Any?){when(v){is String->putString(k,v);is Boolean->putBoolean(k,v);is Int->putInt(k,v);is Long->putLong(k,v);is Float->putFloat(k,v);is Set<*>->putStringSet(k,v.filterIsInstance<String>().toSet())}}
}
