package io.github.unidentified24.spock.features.downloads

import io.github.unidentified24.spock.data.entity.SongsModel
import io.github.unidentified24.spock.features.DownloadOptions
import kotlinx.serialization.Serializable
import java.time.*

@Serializable enum class DownloadState {QUEUED,RUNNING,RETRY,PAUSED,FAILED,COMPLETE,CANCELLED}
@Serializable data class DownloadTask(
    val song:SongsModel,val state:DownloadState=DownloadState.QUEUED,val priority:Int=0,
    val requestedAt:Long=System.currentTimeMillis(),val runAfter:Long=0,val attempts:Int=0,
    val percent:Int=0,val receivedBytes:Long=0,val totalBytes:Long=-1,val error:String="",
    val profileId:String="default",
) {
    val pending get()=state in setOf(DownloadState.QUEUED,DownloadState.RETRY,DownloadState.RUNNING)
    val remote get()=!(song.url.startsWith("file://")||song.url.startsWith("content://"))
}
object DownloadPolicy {
    fun nextWindow(now:Long,minute:Int,zone:ZoneId=ZoneId.systemDefault()):Long {
        if(minute !in 0..1439)return now
        val local=Instant.ofEpochMilli(now).atZone(zone)
        val today=local.toLocalDate().atTime(minute/60,minute%60).atZone(zone)
        return (if(today.toInstant().toEpochMilli()>now)today else local.toLocalDate().plusDays(1).atTime(minute/60,minute%60).atZone(zone)).toInstant().toEpochMilli()
    }
    fun ordered(tasks:List<DownloadTask>,now:Long)=tasks.filter{it.state in setOf(DownloadState.QUEUED,DownloadState.RETRY)&&it.runAfter<=now}
        .sortedWith(compareByDescending<DownloadTask>{it.priority}.thenBy{it.requestedAt}.thenBy{it.song.id})
    fun retryAt(now:Long,attempt:Int)=now+(15_000L shl (attempt-1).coerceIn(0,5))
    fun estimateBytes(song:SongsModel,quality:String):LongRange {
        val seconds=(song.durationMs.takeIf{it>0} ?: 210000)/1000L
        val rates=when(quality){"LOW"->64_000L..96_000L;"NORMAL"->112_000L..160_000L;"LOSSLESS"->650_000L..1_500_000L;else->160_000L..256_000L}
        return seconds*rates.first/8..seconds*rates.last/8
    }
    fun canRun(task:DownloadTask,rules:DownloadOptions,wifi:Boolean,online:Boolean,charging:Boolean,now:Long)=
        task.runAfter<=now&&(!rules.chargingOnly||charging)&&(!task.remote||(!rules.offlineOnly&&online&&(!rules.wifiOnly||wifi)))
}
