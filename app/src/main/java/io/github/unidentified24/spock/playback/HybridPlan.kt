package io.github.unidentified24.spock.playback

/** Queue identity and the two-second decision are independent of either audio engine. */
class HybridPlan {
    enum class Route { NATIVE, WEB }
    var generation=0L; private set
    var next:String?=null; private set
    var route:Route?=null; private set
    fun select(query:String?):Long {
        if(query!=next){generation++;next=query;route=null}
        return generation
    }
    fun accepts(token:Long,query:String)=token==generation && query==next && route==null
    fun lock(remainingMs:Long,nativeReady:Boolean):Route? {
        if(next!=null && remainingMs in 0..2000 && route==null)
            route=if(nativeReady)Route.NATIVE else Route.WEB
        return route
    }
    fun reset(){generation++;next=null;route=null}
}

/** Browser controls saying 'Pause' alone are not evidence of advancing audio. */
class WebPlaybackProgress {
    var expectedUri=""; private set
    var confirmed=false; private set
    var ended=false; private set
    private var lastPosition=-1L
    private var lastDuration=0L
    fun reset(uri:String){expectedUri=uri;confirmed=false;ended=false;lastPosition=-1;lastDuration=0}
    fun update(uri:String,position:Long,duration:Long,playing:Boolean,mediaEnded:Boolean):Boolean {
        if(ended)return false
        if(uri!=expectedUri) {
            if(confirmed && lastDuration>0 && lastPosition>=lastDuration-1500)ended=true
            return false
        }
        if(playing && lastPosition>=0 && position>lastPosition+100)confirmed=true
        if(duration>0)lastDuration=duration
        if(confirmed && lastDuration>0 && maxOf(lastPosition,position)>=lastDuration-1500 &&
            (mediaEnded || (!playing && position>=lastDuration-100) || position+1500<lastPosition))ended=true
        lastPosition=maxOf(lastPosition,position)
        return true
    }
}
