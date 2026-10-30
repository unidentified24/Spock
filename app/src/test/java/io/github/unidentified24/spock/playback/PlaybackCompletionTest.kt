package io.github.unidentified24.spock.playback
import org.junit.Assert.*
import org.junit.Test
class PlaybackCompletionTest {
 @Test fun emptyStreamMustNotAdvance(){assertFalse(PlaybackCompletion.isNaturalEnd(false,0,0,180000))}
 @Test fun previewMustNotAdvance(){assertFalse(PlaybackCompletion.isNaturalEnd(true,30000,30000,180000))}
 @Test fun prematureEndMustNotAdvance(){assertFalse(PlaybackCompletion.isNaturalEnd(true,20000,180000,180000))}
 @Test fun completedRecordingAdvances(){assertTrue(PlaybackCompletion.isNaturalEnd(true,179500,180000,180000))}
 @Test fun completedLocalTrackNeedsNoCatalogDuration(){assertTrue(PlaybackCompletion.isNaturalEnd(true,45000,45000,0))}
}
