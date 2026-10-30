package io.github.unidentified24.spock.soundcloud

import com.metrolist.innertube.SoundCloudExtractor
import io.github.unidentified24.spock.playback.LookupConnections
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

object SoundCloudSource {
    data class Result(val url:String,val page:String,val creator:String,val bitrate:Int,val hls:Boolean)
    private data class Match(val tracks:List<SoundCloudExtractor.Track>,val at:Long)
    private val matches=ConcurrentHashMap<String,Match>()
    private val selected=ConcurrentHashMap<String,SoundCloudExtractor.Track>()
    private val rejected=ConcurrentHashMap<String,Long>()
    fun pageFor(query:String)=selected[query]?.page
    fun reject(query:String){selected.remove(query)?.let{rejected[it.page]=System.currentTimeMillis()+60000}}
    suspend fun resolve(query:String,title:String,artist:String,duration:Int):Result?=withContext(Dispatchers.IO){
        LookupConnections.checkActive()
        if(matches.size>200){matches.clear();selected.clear()}
        if(rejected.size>200)rejected.entries.removeIf{it.value<System.currentTimeMillis()}
        val known=matches[query]?.takeIf{System.currentTimeMillis()-it.at<24*60*60*1000L}?.tracks
        val tracks=known ?: SoundCloudExtractor.search("$title $artist").also{matches[query]=Match(it,System.currentTimeMillis())}
        val track=tracks.filter {
            SoundCloudMatch.accepts(title,artist,duration,it.title,it.creator,it.durationSec)
        }.sortedByDescending{SoundCloudMatch.uploaderMatches(artist,it.creator)}.firstOrNull{(rejected[it.page] ?: 0)<System.currentTimeMillis()} ?: return@withContext null
        selected[query]=track
        LookupConnections.checkActive()
        SoundCloudExtractor.streams(track.page).firstOrNull()?.let{Result(it.url,track.page,track.creator,it.bitrate,it.hls)}
    }
}
