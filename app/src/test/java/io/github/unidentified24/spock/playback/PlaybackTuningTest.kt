package io.github.unidentified24.spock.playback

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PlaybackTuningTest {
    @Test fun bufferedNativeDoesNotWaitForSlowBrowser()=runBlocking {
        val browserStarted=CompletableDeferred<Unit>()
        var browserStopped=false
        val result=withTimeout(1000){firstPlayable(listOf(
            StreamLookup{browserStarted.await();"Buffered native"},
            StreamLookup<String>{browserStarted.complete(Unit);try{delay(14000);"Spotify"}finally{browserStopped=true}},
        ))}
        assertEquals("Buffered native",result)
        assertTrue(browserStopped)
    }
    @Test fun outagesBackOffAndRecoverWithoutPermanentlyDisablingProvider(){
        val health=PlaybackBackoff(2,1000)
        health.failure(100);assertTrue(health.available(101))
        health.failure(200);assertFalse(health.available(1199));assertTrue(health.available(1200))
        health.failure(1300);health.success();health.failure(1400)
        assertTrue(health.available(1401))
    }
    @Test fun nextTrackIsBoundedWhileWifiGetsMoreForegroundReadAhead(){
        val wifi=BufferPlan.forWifi(true);val cell=BufferPlan.forWifi(false)
        for(plan in listOf(wifi,cell)){
            assertTrue(plan.startMs<plan.nextMs)
            assertTrue(plan.nextMs<plan.minMs)
            assertTrue(plan.minMs<=plan.maxMs)
            assertTrue(plan.nextBytes<plan.maxBytes)
        }
        assertTrue(wifi.maxMs>cell.maxMs);assertTrue(wifi.nextBytes>cell.nextBytes)
    }
}
