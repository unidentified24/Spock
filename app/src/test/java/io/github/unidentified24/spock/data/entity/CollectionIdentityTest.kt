package io.github.unidentified24.spock.data.entity
import org.junit.Assert.*
import org.junit.Test
class CollectionIdentityTest {
 @Test fun duplicatePlaylistIdsCannotDuplicateRows(){val p=LibraryEntry("123","Test","","",true);assertEquals(1,CollectionIdentity.entries(listOf(p,p.copy(name="New title"))).size)}
 @Test fun albumsAndPlaylistsStaySeparate(){val p=LibraryEntry("123","Test","","",true);assertEquals(2,CollectionIdentity.entries(listOf(p,p.copy(isPlaylist=false))).size)}
 @Test fun artistsWithoutIdsUseNames(){assertEquals(2,CollectionIdentity.artists(listOf(ArtistsModel("One",""),ArtistsModel("Two",""),ArtistsModel("One",""))).size)}
 @Test fun offlineEmptyLibraryIsValid(){assertTrue(CollectionIdentity.entries(emptyList()).isEmpty())}
}
