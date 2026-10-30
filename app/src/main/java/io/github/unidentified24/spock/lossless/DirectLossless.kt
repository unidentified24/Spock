package io.github.unidentified24.spock.lossless

import io.github.unidentified24.spock.playback.LookupConnections
import io.github.unidentified24.spock.playback.PlaybackBackoff
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Modern direct-file catalog; legacy hifi-api instances no longer share this protocol. */
object DirectLossless {
    private val health=PlaybackBackoff(1,5*60*1000L)
    private const val BASE="https://tracks.monochrome.st"
    suspend fun resolve(info:LosslessSource.TrackInfo):LosslessSource.FlacTrack?=withContext(Dispatchers.IO){
        if(info.title.isBlank() || !health.available(System.currentTimeMillis()))return@withContext null
        try{
            val q=URLEncoder.encode(info.title+" "+info.artist,"UTF-8")
            val conn=(URL("$BASE/search/tracks?q=$q&limit=8").openConnection() as HttpURLConnection)
            LookupConnections.track(conn)
            val root=try{conn.connectTimeout=3000;conn.readTimeout=3000;JSONObject(conn.inputStream.bufferedReader().use{it.readText()})}finally{conn.disconnect()}
            val tracks=root.optJSONArray("tracks") ?: return@withContext null
            val hits=(0 until tracks.length()).mapNotNull{i->
                val t=tracks.optJSONObject(i) ?: return@mapNotNull null
                if(t.optBoolean("playable",true)==false)return@mapNotNull null
                val artists=t.optJSONArray("artistNames")?.let{a->(0 until a.length()).map{a.optString(it)}}.orEmpty()
                LosslessSource.TidalHit(t.optString("id"),t.optString("title"),artists.firstOrNull().orEmpty(),t.optInt("duration")/1000,t.optString("isrc"))
            }
            val hit=LosslessSource.pickTidalHit(hits,info.isrc,info.title,info.artist,info.durationSec) ?: return@withContext null
            if(!hit.id.matches(Regex("[A-Za-z0-9_-]{1,128}")))return@withContext null
            health.success()
            LosslessSource.FlacTrack("$BASE/track/${hit.id}","Monochrome","")
        }catch(c:CancellationException){throw c}
         catch(_:Exception){LookupConnections.checkActive();health.failure(System.currentTimeMillis());null}
    }
}
