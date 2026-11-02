package io.github.unidentified24.spock.playback

import org.junit.Assert.*
import org.junit.Test

class ContinuousPlaybackPolicyTest {
    @Test fun requestsRadioInsidePreparationWindowOnly(){
        fun eligible(remaining:Long)=ContinuousPlaybackPolicy.shouldPrefetchRadio(true,false,false,false,remaining)
        assertFalse(eligible(60_001))
        assertTrue(eligible(60_000))
        assertTrue(eligible(1))
        assertFalse(eligible(0))
    }

    @Test fun neverPrefetchesWhenPlaybackIsNotExpectedOrOffline(){
        assertFalse(ContinuousPlaybackPolicy.shouldPrefetchRadio(false,false,false,false,15_000))
        assertFalse(ContinuousPlaybackPolicy.shouldPrefetchRadio(true,true,false,false,15_000))
        assertFalse(ContinuousPlaybackPolicy.shouldPrefetchRadio(true,false,true,false,15_000))
        assertFalse(ContinuousPlaybackPolicy.shouldPrefetchRadio(true,false,false,true,15_000))
    }
}
