package io.github.unidentified24.spock.features.library

import android.content.Context
import io.github.unidentified24.spock.data.entity.SongsModel
import io.github.unidentified24.spock.features.FeatureSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable data class QueueSnapshot(val songs:List<SongsModel>,val time:Long=System.currentTimeMillis())
@Serializable data class QueueShelf(val current:List<SongsModel> = emptyList(),val recent:List<QueueSnapshot> = emptyList(),val nextIds:Set<Int> = emptySet())
object QueueTools {
    fun move(songs:List<SongsModel>,from:Int,to:Int):List<SongsModel>{if(from !in songs.indices||to !in songs.indices||from==to)return songs;return songs.toMutableList().apply{add(to,removeAt(from))}}
    fun clearAfter(songs:List<SongsModel>,current:Int):List<SongsModel>{val index=songs.indexOfFirst{it.id==current};return if(index>=0)songs.take(index+1)else emptyList()}
    fun retainCurrent(songs:List<SongsModel>,removed:Set<Int>,current:Int)=songs.filter{it.id !in removed||it.id==current}
    fun remember(shelf:QueueShelf,songs:List<SongsModel>,now:Long):QueueShelf {
        val previous=shelf.current
        val changed=previous.map{it.id}.toSet()!=songs.map{it.id}.toSet()
        val candidates=if(changed&&previous.isNotEmpty())(listOf(QueueSnapshot(previous,now))+shelf.recent).distinctBy{row->row.songs.map{it.id}}.take(12)else shelf.recent
        var tracks=songs.size
        val recent=candidates.takeWhile{snapshot->(tracks+snapshot.songs.size<=20000).also{if(it)tracks+=snapshot.songs.size}}
        return shelf.copy(current=songs,recent=recent,nextIds=shelf.nextIds.intersect(songs.map{it.id}.toSet()))
    }
}
object QueueArchive {
    private var prefs:android.content.SharedPreferences?=null
    val state=MutableStateFlow(QueueShelf())
    val saveError=MutableStateFlow("")
    private sealed interface Change {data class Songs(val songs:List<SongsModel>):Change;data class Next(val id:Int,val enabled:Boolean):Change;data class Flush(val done:CompletableDeferred<Unit>):Change}
    private val channel=Channel<Change>(Channel.UNLIMITED)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val persistence=Channel<Unit>(Channel.CONFLATED)
    private val diskLock=Any()
    @Synchronized fun initialize(context:Context){if(prefs!=null)return
        prefs=context.applicationContext.getSharedPreferences("queue_archive",0)
        state.value=runCatching{FeatureSettings.json.decodeFromString<QueueShelf>(prefs!!.getString("queue","{}")!!)}.getOrDefault(QueueShelf())
        scope.launch{for(change in channel)when(change){is Change.Songs->record(change.songs);is Change.Next->setNext(change.id,change.enabled);is Change.Flush->{if(persist())change.done.complete(Unit)else change.done.completeExceptionally(java.io.IOException("Couldn't save queue"))}}}
        scope.launch{for(signal in persistence){delay(180);persist()}}
    }
    @Synchronized private fun save(value:QueueShelf){state.value=value;persistence.trySend(Unit)}
    private fun persist():Boolean=synchronized(diskLock){
        val ok=runCatching{checkNotNull(prefs).edit().putString("queue",FeatureSettings.json.encodeToString(state.value)).commit()}.getOrDefault(false)
        saveError.value=if(ok)"" else "Your queue couldn't be saved. Check your device's free space."
        ok
    }
    suspend fun flush(){val done=CompletableDeferred<Unit>();channel.send(Change.Flush(done));done.await()}
    @Synchronized private fun record(songs:List<SongsModel>){save(QueueTools.remember(state.value,songs.take(10000),System.currentTimeMillis()))}
    fun offer(songs:List<SongsModel>){if(FeatureSettings.value.privacy.privateListening)return;channel.trySend(Change.Songs(songs))}
    fun next(id:Int,enabled:Boolean){if(FeatureSettings.value.privacy.privateListening)return;channel.trySend(Change.Next(id,enabled))}
    @Synchronized private fun setNext(id:Int,enabled:Boolean){save(state.value.copy(nextIds=if(enabled)state.value.nextIds+id else state.value.nextIds-id))}
}
