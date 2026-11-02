package io.github.unidentified24.spock.data.preferences

import android.content.Context
import android.os.SystemClock
import io.github.unidentified24.spock.data.entity.SongsModel
import io.github.unidentified24.spock.features.FeatureSettings
import io.github.unidentified24.spock.features.SessionIdentity
import io.github.unidentified24.spock.features.stats.*
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

data class ListeningSample @JvmOverloads constructor(val date:LocalDate,val songId:Int,val title:String,val artist:String,val ms:Long,val album:String="",val plays:Int=0)

object ListeningMeter {
    private var clock=PlaybackClock()
    private var lastFlush=0L
    private val pending=linkedMapOf<String,ListeningDelta>()
    val saveError=kotlinx.coroutines.flow.MutableStateFlow("")
    private val writer=java.util.concurrent.Executors.newSingleThreadExecutor{task->Thread(task,"spock-statistics").apply{isDaemon=true}}
    private fun key(profile:String,date:LocalDate,id:Int)="$profile:$date:$id"
    @Synchronized fun tick(context:Context,playing:Boolean,entry:HistoryEntry){
        val now=SystemClock.elapsedRealtime();val profile=SessionIdentity.id
        val active=playing&&!FeatureSettings.value.privacy.privateListening&&entry.songId>=0
        val delta=clock.sample(now,active,"$profile:${entry.songId}")
        if(delta>0){
            val date=LocalDate.now();val key=key(profile,date,entry.songId)
            val song=SongsModel(entry.songId,entry.title,entry.album,entry.singer,entry.image,"",durationMs=entry.durationMs)
            val previous=pending[key]
            pending[key]=ListeningDelta(profile,date,song,(previous?.ms ?: 0)+delta,previous?.plays ?: 0)
        }
        if((!active||now-lastFlush>=5000)&&pending.isNotEmpty())enqueue(context)
    }
    @Synchronized fun heard(context:Context,song:SongsModel){
        if(FeatureSettings.value.privacy.privateListening||song.id<0)return
        val profile=SessionIdentity.id;val date=LocalDate.now();val key=key(profile,date,song.id);val previous=pending[key]
        pending[key]=ListeningDelta(profile,date,song,previous?.ms ?: 0,(previous?.plays ?: 0)+1)
    }
    @Synchronized private fun enqueue(context:Context){
        if(pending.isEmpty())return
        val rows=pending.values.toList();pending.clear();lastFlush=SystemClock.elapsedRealtime()
        val app=context.applicationContext
        writer.submit{
            try{rows.map{it.profile}.distinct().forEach{ListeningDatabase.initialize(app,it)};ListeningDatabase.add(app,rows);saveError.value=""}
            catch(_:Exception){synchronized(this){rows.forEach{row->val key=key(row.profile,row.date,row.song.id);val current=pending[key];pending[key]=row.copy(ms=(row.ms+(current?.ms ?: 0)).coerceAtMost(86400000),plays=row.plays+(current?.plays ?: 0))}};saveError.value="Listening statistics couldn't be saved. Check your device's free space."}
        }
    }
    fun flush(context:Context){enqueue(context);writer.submit{}.get();if(saveError.value.isNotBlank())throw java.io.IOException(saveError.value)}
    fun samples(context:Context):List<ListeningSample> = try{flush(context);ListeningDatabase.samples(context,SessionIdentity.id)}catch(cancelled:java.util.concurrent.CancellationException){throw cancelled}catch(_:Exception){saveError.value="Listening statistics couldn't be loaded. Your saved data has been kept.";emptyList()}
    @Synchronized fun resetClock(){clock=PlaybackClock()}
    fun delete(context:Context,songIds:Set<Int> = emptySet(),start:LocalDate=LocalDate.MIN,end:LocalDate=LocalDate.MAX){flush(context);ListeningDatabase.delete(context,SessionIdentity.id,songIds,start,end);resetClock()}
    fun exportLedger(context:Context):String {
        val data=JSONObject();samples(context).forEach{row->data.put("${row.date}:${row.songId}",JSONObject().put("date",row.date.toString()).put("songId",row.songId).put("title",row.title).put("artist",row.artist).put("album",row.album).put("ms",row.ms).put("plays",row.plays))}
        return data.toString()
    }
    fun replaceFromBackup(context:Context,write:()->Unit){
        flush(context);write()
        val data=runCatching{JSONObject(context.getSharedPreferences("listening_meter",0).getString("ledger","{}")!!)}.getOrDefault(JSONObject())
        ListeningDatabase.replaceLegacy(context,SessionIdentity.id,data);resetClock()
    }
    fun milliseconds(context:Context)=samples(context).sumOf{it.ms}
    fun songMilliseconds(context:Context,songId:Int)=samples(context).filter{it.songId==songId}.sumOf{it.ms}
    fun songs(context:Context)=samples(context).groupBy{it.title+" · "+it.artist}.map{(name,rows)->name to rows.sumOf{it.ms}}.sortedByDescending{it.second}.take(20)
    fun stats(context:Context):Map<String,Int>{
        val now=LocalDate.now();val rows=samples(context)
        return mapOf("today" to now,"thisWeek" to now.with(java.time.DayOfWeek.MONDAY),"thisMonth" to now.withDayOfMonth(1),"allTime" to LocalDate.MIN).mapValues{(_,start)->(rows.filter{it.date>=start}.sumOf{it.ms}/60000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()}
    }
    fun top(context:Context,field:String,limit:Int):List<Pair<String,Int>> = samples(context).groupBy{if(field=="album")it.album else it.artist}.filterKeys{it.isNotBlank()}.map{(name,rows)->name to (rows.sumOf{it.ms}/60000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()}.sortedByDescending{it.second}.take(limit)
    fun days(context:Context)=samples(context).groupBy{it.date.dayOfWeek.getDisplayName(TextStyle.SHORT,Locale.US)}.mapValues{(_,rows)->(rows.sumOf{it.ms}/60000).toInt()}
}
