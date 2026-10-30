package io.github.unidentified24.spock.soundcloud

import org.junit.Assert.*
import org.junit.Test

class SoundCloudMatchTest {
    @Test fun recognizesCompactArtistNamesAndRejectsDifferentFeaturedEdition(){
        assertTrue(SoundCloudMatch.accepts("Shape of You","Ed Sheeran",233,"Shape of You","EdSheeran",233))
        assertFalse(SoundCloudMatch.accepts("Levitating","Dua Lipa",203,"Levitating (feat. DaBaby)","Dua Lipa",203))
        assertFalse(SoundCloudMatch.accepts("bad guy","Billie Eilish",194,"Bad Guy - Billie Eilish (8D AUDIO)","Someone",194))
    }
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
