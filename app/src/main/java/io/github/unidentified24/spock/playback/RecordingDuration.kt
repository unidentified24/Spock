package io.github.unidentified24.spock.playback

object RecordingDuration {
    fun matches(expectedMs:Long,actualMs:Long)=expectedMs<=0 ||
        (actualMs>0 && kotlin.math.abs(actualMs-expectedMs)<=maxOf(3000L,(expectedMs*.03).toLong()))
}
