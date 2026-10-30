package io.github.unidentified24.spock.playback

import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.source.TrackGroupArray
import org.junit.Assert.*
import org.junit.Test

class ReadAheadLoadControlTest {
    @Test fun realMedia3CallbacksDoNotRecurseAndPromotionOpensReadAhead(){
        val plan=BufferPlan.forWifi(false)
        val load=ReadAheadLoadControl(plan,false)
        load.onPrepared()
        load.onTracksSelected(Timeline.EMPTY,LoadControl.EMPTY_MEDIA_PERIOD_ID,emptyArray(),TrackGroupArray.EMPTY,emptyArray())
        assertTrue(load.shouldStartPlayback(Timeline.EMPTY,LoadControl.EMPTY_MEDIA_PERIOD_ID,1000000,1f,false,C.TIME_UNSET))
        assertFalse(load.shouldContinueLoading(0,plan.nextMs*1000L,1f))
        load.active=true
        assertTrue(load.shouldContinueLoading(0,plan.nextMs*1000L,1f))
        load.onStopped();load.onReleased()
    }
}
