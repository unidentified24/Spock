package io.github.unidentified24.spock.playback

data class BufferPlan(val minMs:Int,val maxMs:Int,val startMs:Int,val rebufferMs:Int,val maxBytes:Int,val nextMs:Int,val nextBytes:Int,val chunkBytes:Long) {
    companion object {
        fun forWifi(wifi:Boolean)=if(wifi) BufferPlan(20000,60000,350,1200,16*1024*1024,8000,2*1024*1024,4L*1024*1024)
            else BufferPlan(10000,25000,500,1500,4*1024*1024,3000,512*1024,2L*1024*1024)
    }
}

/** Failed browser attempts do not impose the same delay on every subsequent song. */
class PlaybackBackoff(private val threshold:Int=2,private val cooldownMs:Long=60000) {
    private var failures=0
    private var until=0L
    @Synchronized fun available(now:Long)=now>=until
    @Synchronized fun failure(now:Long){if(++failures>=threshold){until=now+cooldownMs;failures=0}}
    @Synchronized fun success(){failures=0;until=0}
}
