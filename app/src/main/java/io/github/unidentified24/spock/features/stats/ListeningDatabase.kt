package io.github.unidentified24.spock.features.stats

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.*
import io.github.unidentified24.spock.data.entity.SongsModel
import io.github.unidentified24.spock.data.preferences.ListeningSample
import org.json.JSONObject
import java.time.LocalDate

data class ListeningDelta(val profile:String,val date:LocalDate,val song:SongsModel,val ms:Long=0,val plays:Int=0)

object ListeningDatabase {
    private var database:SQLiteDatabase?=null
    @Synchronized private fun db(context:Context):SQLiteDatabase {
        database?.let{return it}
        val helper=object:SQLiteOpenHelper(context.applicationContext,"spock-listening.db",null,1){
            override fun onCreate(db:SQLiteDatabase){db.execSQL("CREATE TABLE listening(profile TEXT NOT NULL,day INTEGER NOT NULL,song_id INTEGER NOT NULL,title TEXT NOT NULL,artist TEXT NOT NULL,album TEXT NOT NULL,ms INTEGER NOT NULL DEFAULT 0,plays INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(profile,day,song_id))")}
            override fun onUpgrade(db:SQLiteDatabase,old:Int,new:Int){}
        }
        helper.setWriteAheadLoggingEnabled(true)
        return helper.writableDatabase.also{database=it}
    }
    @Synchronized fun initialize(context:Context,profile:String){
        val db=db(context);val preferences=context.getSharedPreferences("listening_meter",0)
        if(preferences.getBoolean("sql_migrated_$profile",false))return
        val original=preferences.getString("ledger","{}")!!
        if(original!="{}"){
            val directory=java.io.File(context.filesDir,"recovery").apply{mkdirs()}
            val recovery=java.io.File(directory,"listening-v1-"+profile.replace(Regex("[^a-zA-Z0-9_-]"),"_").take(80)+".json")
            if(!recovery.exists())recovery.writeText(original,Charsets.UTF_8)
        }
        val legacy=JSONObject(original)
        replaceLegacy(context,profile,legacy)
        preferences.edit().putBoolean("sql_migrated_$profile",true).commit()
    }
    @Synchronized fun add(context:Context,rows:List<ListeningDelta>){
        if(rows.isEmpty())return
        val database=db(context);database.beginTransaction()
        try{rows.forEach{row->
            if(row.song.id<0||row.song.title.isBlank())return@forEach
            val insert=ContentValues().apply{put("profile",row.profile);put("day",row.date.toEpochDay());put("song_id",row.song.id);put("title",row.song.title.take(512));put("artist",row.song.singer.take(512));put("album",row.song.album.take(512));put("ms",0);put("plays",0)}
            database.insertWithOnConflict("listening",null,insert,SQLiteDatabase.CONFLICT_IGNORE)
            database.execSQL("UPDATE listening SET ms=MIN(86400000,ms+?),plays=MIN(100000,plays+?),title=?,artist=?,album=? WHERE profile=? AND day=? AND song_id=?",arrayOf(row.ms.coerceIn(0,86400000),row.plays.coerceAtLeast(0),row.song.title.take(512),row.song.singer.take(512),row.song.album.take(512),row.profile,row.date.toEpochDay(),row.song.id))
        };database.setTransactionSuccessful()}finally{database.endTransaction()}
    }
    @Synchronized fun samples(context:Context,profile:String,start:LocalDate=LocalDate.MIN,end:LocalDate=LocalDate.MAX):List<ListeningSample>{
        initialize(context,profile)
        val out=mutableListOf<ListeningSample>()
        db(context).rawQuery("SELECT day,song_id,title,artist,ms,album,plays FROM listening WHERE profile=? AND day>=? AND day<=? ORDER BY day,song_id",arrayOf(profile,start.toEpochDay().toString(),end.toEpochDay().toString())).use{cursor->while(cursor.moveToNext()){
            runCatching{ListeningSample(LocalDate.ofEpochDay(cursor.getLong(0)),cursor.getInt(1),cursor.getString(2),cursor.getString(3),cursor.getLong(4),cursor.getString(5),cursor.getInt(6))}.getOrNull()?.let(out::add)
        }}
        return out
    }
    @Synchronized fun replaceLegacy(context:Context,profile:String,legacy:JSONObject){
        val rows=legacy.keys().asSequence().mapNotNull{key->runCatching{
            val row=legacy.getJSONObject(key);ListeningDelta(profile,LocalDate.parse(row.getString("date")),SongsModel(row.getInt("songId"),row.optString("title"),row.optString("album"),row.optString("artist"),"",""),row.optLong("ms").coerceIn(0,86400000),row.optInt("plays").coerceIn(0,100000))
        }.getOrNull()}.toList()
        replace(context,profile,rows)
    }
    @Synchronized fun replace(context:Context,profile:String,rows:List<ListeningDelta>){
        val database=db(context);database.beginTransaction()
        try{database.delete("listening","profile=?",arrayOf(profile));add(context,rows.filter{it.profile==profile});database.setTransactionSuccessful()}finally{database.endTransaction()}
    }
    @Synchronized fun delete(context:Context,profile:String,songIds:Set<Int> = emptySet(),start:LocalDate=LocalDate.MIN,end:LocalDate=LocalDate.MAX){
        initialize(context,profile)
        val database=db(context)
        if(songIds.isEmpty())database.delete("listening","profile=? AND day>=? AND day<=?",arrayOf(profile,start.toEpochDay().toString(),end.toEpochDay().toString()))
        else{database.beginTransaction();try{songIds.forEach{id->database.delete("listening","profile=? AND song_id=? AND day>=? AND day<=?",arrayOf(profile,id.toString(),start.toEpochDay().toString(),end.toEpochDay().toString()))};database.setTransactionSuccessful()}finally{database.endTransaction()}}
    }
}
