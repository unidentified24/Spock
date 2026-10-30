package io.github.unidentified24.spock.playback
import org.junit.Assert.*
import org.junit.Test

class HybridPlanTest {
 @Test fun readyAudioLocksNativeAtTwoSeconds(){val p=HybridPlan();p.select("next");assertEquals(HybridPlan.Route.NATIVE,p.lock(2000,true))}
 @Test fun unavailableAudioLocksBrowser(){val p=HybridPlan();p.select("next");assertEquals(HybridPlan.Route.WEB,p.lock(1999,false))}
 @Test fun lateSourceCannotOverrideBrowserDecision(){val p=HybridPlan();val token=p.select("next");p.lock(2000,false);assertFalse(p.accepts(token,"next"));assertEquals(HybridPlan.Route.WEB,p.lock(10,true))}
 @Test fun nativeDecisionDoesNotFlipAsTimePasses(){val p=HybridPlan();p.select("next");p.lock(2000,true);assertEquals(HybridPlan.Route.NATIVE,p.lock(0,false))}
 @Test fun noDecisionBeforeDeadlineOrWithoutNext(){val p=HybridPlan();assertNull(p.lock(0,true));p.select("next");assertNull(p.lock(2001,true))}
 @Test fun reorderRejectsOldPreparedTrack(){val p=HybridPlan();val old=p.select("a");p.select("b");assertFalse(p.accepts(old,"a"));assertTrue(p.accepts(p.generation,"b"))}
 @Test fun manualSelectionInvalidatesEvenSameTrack(){val p=HybridPlan();val old=p.select("a");p.reset();p.select("a");assertFalse(p.accepts(old,"a"))}
 @Test fun unchangedQueueKeepsPreparation(){val p=HybridPlan();val token=p.select("a");p.select("a");assertTrue(p.accepts(token,"a"))}
}

class WebPlaybackProgressTest {
 private val uri="spotify:track:one"
 @Test fun firstStalePositionDoesNotConfirmPlayback(){val p=WebPlaybackProgress();p.reset(uri);p.update(uri,55000,180000,true,false);assertFalse(p.confirmed)}
 @Test fun pauseButtonWithoutMovingTimeDoesNotConfirm(){val p=WebPlaybackProgress();p.reset(uri);repeat(3){p.update(uri,0,180000,true,false)};assertFalse(p.confirmed)}
 @Test fun wrongTrackCannotConfirmPlayback(){val p=WebPlaybackProgress();p.reset(uri);p.update("spotify:track:other",1000,180000,true,false);assertFalse(p.confirmed)}
 @Test fun advancingTimeConfirmsSelectedTrack(){val p=WebPlaybackProgress();p.reset(uri);p.update(uri,0,180000,true,false);p.update(uri,300,180000,true,false);assertTrue(p.confirmed)}
 @Test fun previewEndDoesNotBecomeNaturalEnd(){val p=WebPlaybackProgress();p.reset(uri);p.update(uri,0,180000,true,false);p.update(uri,30000,180000,true,false);p.update(uri,30000,180000,false,true);assertFalse(p.ended)}
 @Test fun pausedNearEndDoesNotAdvance(){val p=WebPlaybackProgress();p.reset(uri);p.update(uri,0,180000,true,false);p.update(uri,179000,180000,true,false);p.update(uri,179000,180000,false,false);assertFalse(p.ended)}
 @Test fun completedTrackEndsOnce(){val p=WebPlaybackProgress();p.reset(uri);p.update(uri,0,180000,true,false);p.update(uri,179500,180000,true,false);p.update(uri,180000,180000,false,true);assertTrue(p.ended);assertFalse(p.update(uri,180000,180000,false,true))}
 @Test fun resetDropsCompletionOfPreviousRequest(){val p=WebPlaybackProgress();p.reset(uri);p.update(uri,0,180000,true,false);p.update(uri,179500,180000,true,false);p.update(uri,180000,180000,false,true);p.reset(uri);assertFalse(p.ended);assertFalse(p.confirmed)}
}
