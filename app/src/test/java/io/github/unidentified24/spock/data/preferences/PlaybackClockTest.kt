package io.github.unidentified24.spock.data.preferences

import org.junit.Assert.*
import org.junit.Test

class PlaybackClockTest {
    @Test fun countsElapsedPlaybackAndExcludesPauseBufferingAndSuspension() {
        val clock = PlaybackClock()
        assertEquals(0L, clock.sample(0, true, "song"))
        assertEquals(250L, clock.sample(250, true, "song"))
        assertEquals(0L, clock.sample(500, false, "song"))
        assertEquals(0L, clock.sample(750, true, "song"))
        assertEquals(250L, clock.sample(1000, true, "song"))
        assertEquals(0L, clock.sample(9000, true, "song"))
    }
    @Test fun changingTrackDoesNotCreditPreviousTrackTime() {
        val clock = PlaybackClock()
        clock.sample(0, true, "a")
        assertEquals(0L, clock.sample(250, true, "b"))
        assertEquals(250L, clock.sample(500, true, "b"))
    }
}
