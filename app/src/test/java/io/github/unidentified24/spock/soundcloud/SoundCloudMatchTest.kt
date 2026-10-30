package io.github.unidentified24.spock.soundcloud

import org.junit.Assert.*
import org.junit.Test

class SoundCloudMatchTest {
    @Test fun acceptsArtistUploadAndArtistInTitle(){
        assertTrue(SoundCloudMatch.accepts("Unity","TheFatRat",249,"Unity","TheFatRat",249))
        assertTrue(SoundCloudMatch.accepts("Unity","TheFatRat",249,"TheFatRat - Unity","NCS",250))
    }
    @Test fun rejectsCoversRemixesWrongArtistsAndPreviews(){
        assertFalse(SoundCloudMatch.accepts("Unity","TheFatRat",249,"Unity (remix)","TheFatRat",249))
        assertFalse(SoundCloudMatch.accepts("Unity","TheFatRat",249,"Unity","Another artist",249))
        assertFalse(SoundCloudMatch.accepts("Unity","TheFatRat",249,"Unity","TheFatRat",30))
        assertFalse(SoundCloudMatch.accepts("Unity","TheFatRat",249,"Unity","TheFatRat",0))
    }
    @Test fun originalMixRequestedIsAllowedButDifferentSongIsNot(){
        assertTrue(SoundCloudMatch.accepts("Unity (remix)","TheFatRat",249,"Unity Remix","TheFatRat",249))
        assertFalse(SoundCloudMatch.accepts("Unity","TheFatRat",249,"Monody","TheFatRat",249))
    }
}
