package io.github.unidentified24.spock.soundcloud

import com.metrolist.innertube.SoundCloudExtractor
import io.github.unidentified24.spock.playback.LookupConnections
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

object SoundCloudSource {
    data class Result(val url:String,val page:String,val creator:String,val bitrate:Int,val hls:Boolean)
    private data class Match(val track:SoundCloudExtractor.Track,val at:Long)
    private val matches=ConcurrentHashMap<String,Match>()
    fun pageFor(query:String)=matches[query]?.track?.page
    suspend fun resolve(query:String,title:String,artist:String,duration:Int):Result?=withContext(Dispatchers.IO){
        LookupConnections.checkActive()
        if(matches.size>200)matches.clear()
        val known=matches[query]?.takeIf{System.currentTimeMillis()-it.at<24*60*60*1000L}?.track
        val track=known ?: SoundCloudExtractor.search("$title $artist").firstOrNull {
            SoundCloudMatch.accepts(title,artist,duration,it.title,it.creator,it.durationSec)
        }?.also{matches[query]=Match(it,System.currentTimeMillis())} ?: return@withContext null
        LookupConnections.checkActive()
        SoundCloudExtractor.streams(track.page).firstOrNull()?.let{Result(it.url,track.page,track.creator,it.bitrate,it.hls)}
    }
}
