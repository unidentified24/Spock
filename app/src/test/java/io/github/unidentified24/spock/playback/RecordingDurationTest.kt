package io.github.unidentified24.spock.playback
import org.junit.Assert.*
import org.junit.Test
class RecordingDurationTest {
    @Test fun rejectsTheLongerBlindingLightsVersionAndShortPreviews(){
        assertFalse(RecordingDuration.matches(200040,237461))
        assertFalse(RecordingDuration.matches(200040,30000))
        assertTrue(RecordingDuration.matches(200040,200401))
        assertFalse(RecordingDuration.matches(200040,-1))
    }
}
