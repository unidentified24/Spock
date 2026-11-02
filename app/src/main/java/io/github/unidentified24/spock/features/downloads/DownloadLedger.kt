package io.github.unidentified24.spock.features.downloads

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.unidentified24.spock.data.entity.SongsModel
import io.github.unidentified24.spock.features.FeatureSettings
import io.github.unidentified24.spock.features.SessionIdentity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString

object DownloadLedger {
    private lateinit var db:SQLiteDatabase
    private val records=linkedMapOf<String,DownloadTask>()
    val changes=MutableStateFlow(0L)
    @Synchronized fun initialize(context:Context){
        if(::db.isInitialized)return
        val helper=object:SQLiteOpenHelper(context.applicationContext,"spock-downloads.db",null,1){
            override fun onCreate(db:SQLiteDatabase){db.execSQL("CREATE TABLE tasks(task_key TEXT PRIMARY KEY, body TEXT NOT NULL)")}
            override fun onUpgrade(db:SQLiteDatabase,old:Int,new:Int){}
        }
        helper.setWriteAheadLoggingEnabled(true);db=helper.writableDatabase
        db.rawQuery("SELECT task_key,body FROM tasks",null).use{cursor->while(cursor.moveToNext()){
            runCatching{FeatureSettings.json.decodeFromString<DownloadTask>(cursor.getString(1))}.getOrNull()?.let{records[cursor.getString(0)]=it}
        }}
    }
    private fun key(id:Int,profile:String)=profile+":"+id
    private fun <T> transaction(block:()->T):T {
        val previous=LinkedHashMap(records);db.beginTransaction()
        return try{val value=block();db.setTransactionSuccessful();value}catch(error:Exception){records.clear();records.putAll(previous);throw error}finally{db.endTransaction()}
    }
    @Synchronized fun snapshot(profile:String=SessionIdentity.id)=records.values.filter{it.profileId==profile}.toList()
    @Synchronized fun get(id:Int,profile:String=SessionIdentity.id)=records[key(id,profile)]
    @Synchronized private fun write(task:DownloadTask){
        val values=ContentValues().apply{put("task_key",key(task.song.id,task.profileId));put("body",FeatureSettings.json.encodeToString(task))}
        check(db.insertWithOnConflict("tasks",null,values,SQLiteDatabase.CONFLICT_REPLACE)!=-1L)
        records[key(task.song.id,task.profileId)]=task;changes.value++
    }
    @Synchronized fun enqueue(songs:List<SongsModel>,runAfter:Long=0,priority:Int=0,profile:String=SessionIdentity.id):Int {
        return transaction {var count=0
        songs.distinctBy{it.id}.filter{it.url.isNotBlank()}.forEach{song->
            val previous=records[key(song.id,profile)]
            if(previous?.pending!=true){write(DownloadTask(song,priority=priority,runAfter=runAfter,profileId=profile));count++}
        };count}
    }
    @Synchronized fun change(id:Int,profile:String=SessionIdentity.id,update:(DownloadTask)->DownloadTask){records[key(id,profile)]?.let{write(update(it))}}
    @Synchronized fun begin(id:Int,profile:String=SessionIdentity.id):DownloadTask? {
        val task=records[key(id,profile)] ?: return null
        if(task.state !in setOf(DownloadState.QUEUED,DownloadState.RETRY)||task.runAfter>System.currentTimeMillis())return null
        return task.copy(state=DownloadState.RUNNING,attempts=task.attempts+1,error="").also(::write)
    }
    @Synchronized fun recover(profile:String=SessionIdentity.id){snapshot(profile).filter{it.state==DownloadState.RUNNING}.forEach{write(it.copy(state=DownloadState.QUEUED,attempts=(it.attempts-1).coerceAtLeast(0)))}}
    @Synchronized fun pause(query:String){snapshot().filter{it.song.url==query&&it.pending}.forEach{write(it.copy(state=DownloadState.PAUSED))}}
    @Synchronized fun cancel(query:String){snapshot().filter{it.song.url==query&&it.pending||it.song.url==query&&it.state==DownloadState.PAUSED}.forEach{write(it.copy(state=DownloadState.CANCELLED,error=""))}}
    @Synchronized fun resume(query:String,now:Boolean=false){snapshot().filter{it.song.url==query&&it.state in setOf(DownloadState.PAUSED,DownloadState.FAILED,DownloadState.CANCELLED,DownloadState.RETRY,DownloadState.QUEUED)}.forEach{write(it.copy(state=DownloadState.QUEUED,runAfter=if(now)0 else it.runAfter,attempts=0,error=""))}}
    @Synchronized fun prioritize(query:String){snapshot().filter{it.song.url==query}.forEach{write(it.copy(priority=100,runAfter=0))}}
    @Synchronized fun dismiss(query:String){snapshot().filter{it.song.url==query&&it.state in setOf(DownloadState.FAILED,DownloadState.CANCELLED,DownloadState.COMPLETE)}.forEach{records.remove(key(it.song.id,it.profileId));db.delete("tasks","task_key=?",arrayOf(key(it.song.id,it.profileId)))};changes.value++}
    @Synchronized fun cancelAll(){transaction{snapshot().filter{it.pending||it.state==DownloadState.PAUSED}.forEach{write(it.copy(state=DownloadState.CANCELLED))}}}
    @Synchronized fun pauseAll(){transaction{snapshot().filter{it.pending}.forEach{write(it.copy(state=DownloadState.PAUSED))}}}
    @Synchronized fun resumeAll(){transaction{snapshot().filter{it.state in setOf(DownloadState.PAUSED,DownloadState.FAILED)}.forEach{write(it.copy(state=DownloadState.QUEUED,error="",attempts=0))}}}
    @Synchronized fun reschedule(time:Long){transaction{snapshot().filter{it.state in setOf(DownloadState.QUEUED,DownloadState.RETRY)}.forEach{write(it.copy(runAfter=time))}}}
    @Synchronized fun progress(query:String,percent:Int,received:Long=0,total:Long=-1,profile:String=SessionIdentity.id){snapshot(profile).firstOrNull{it.song.url==query&&it.state==DownloadState.RUNNING}?.let{write(it.copy(percent=percent.coerceIn(0,99),receivedBytes=maxOf(received,it.receivedBytes),totalBytes=if(total>0)total else it.totalBytes))}}
}
