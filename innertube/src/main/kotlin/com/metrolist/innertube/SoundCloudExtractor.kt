package com.metrolist.innertube

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** Public SoundCloud playback through the already bundled NewPipe extractor. No hosted proxy or app secret. */
object SoundCloudExtractor {
    data class Track(val page:String,val title:String,val creator:String,val durationSec:Long)
    data class Audio(val url:String,val bitrate:Int,val hls:Boolean)
    fun search(query:String):List<Track> {
        NewPipeExtractor.init()
        val service=NewPipe.getService("SoundCloud")
        val extractor=service.getSearchExtractor(service.searchQHFactory.fromQuery(query,listOf("tracks"),""))
        extractor.fetchPage()
        return extractor.initialPage.items.filterIsInstance<StreamInfoItem>().take(10).map {
            Track(it.url,it.name,it.uploaderName,it.duration)
        }
    }
    fun streams(page:String):List<Audio> {
        NewPipeExtractor.init()
        val info=NewPipe.getService("SoundCloud").getStreamExtractor(page)
        info.fetchPage()
        return info.audioStreams.map { Audio(it.content,it.averageBitrate,it.deliveryMethod.name=="HLS") }
            .filter{it.url.startsWith("https://")}.sortedWith(compareBy<Audio>{it.hls}.thenByDescending{it.bitrate})
    }
}
