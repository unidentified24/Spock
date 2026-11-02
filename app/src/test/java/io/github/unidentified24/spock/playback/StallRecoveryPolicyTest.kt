package io.github.unidentified24.spock.playback

import org.junit.Assert.*
import org.junit.Test

class StallRecoveryPolicyTest {
    private fun check(
        wanted:Boolean=true,native:Boolean=true,crossfade:Boolean=false,ready:Boolean=true,
        ended:Boolean=false,buffering:Boolean=true,playing:Boolean=false,elapsed:Long=12_000,
    )=StallRecoveryPolicy.shouldRecover(wanted,native,crossfade,ready,ended,buffering,playing,elapsed)

    @Test fun recoversBufferingThatOutlastsTheGraceWindow(){
        assertFalse(check(elapsed=11_999));assertTrue(check(elapsed=12_000))
    }
    @Test fun neverOverridesUserOrAudioFocusPause(){
        assertFalse(check(ready=false,elapsed=60_000));assertFalse(check(wanted=false,elapsed=60_000))
    }
    @Test fun ignoresOtherEnginesCrossfadesAndNaturalEnds(){
        assertFalse(check(native=false));assertFalse(check(crossfade=true));assertFalse(check(ended=true))
    }
    @Test fun frozenPlayingPositionIsAlsoEligibleForSingleRecovery(){
        assertTrue(check(buffering=false,playing=true,elapsed=20_000))
        assertFalse(check(buffering=false,playing=false,elapsed=20_000))
    }
}
