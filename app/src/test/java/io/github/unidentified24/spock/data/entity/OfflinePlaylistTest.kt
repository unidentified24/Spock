package io.github.unidentified24.spock.data.entity

import org.junit.Assert.*
import org.junit.Test

class OfflinePlaylistTest {
    private val a=SongsModel(1,"First","Album","Artist","cover-a","query-a","spotify-a",true,200000)
    private val b=SongsModel(2,"Second","Album","Artist","cover-b","query-b","spotify-b",false,180000)
    private val playlist=OfflinePlaylist("playlist-id","A list","cover",listOf(b,a,b))

    @Test fun persistedPlaylistKeepsOrderDuplicatesAndMatchingMetadata(){
        val restored=OfflinePlaylistCodec.decode(OfflinePlaylistCodec.encode(playlist))
        assertEquals(playlist,restored)
        assertEquals(listOf(2,1,2),restored!!.songs.map{it.id})
        assertTrue(restored.songs[1].explicit)
        assertEquals(200000,restored.songs[1].durationMs)
    }
    @Test fun retryOnlyIncludesMissingUniqueFiles(){
        assertEquals(listOf(b),playlist.missingSongs(setOf(1)))
        assertTrue(playlist.missingSongs(setOf(1,2)).isEmpty())
        assertEquals(listOf(b,a),playlist.missingSongs(emptySet()))
    }
    @Test fun offlineQueueUsesSavedFileIdentityAndKeepsPlaylistOrder(){
        val savedA=a.copy(url="saved-query-a")
        assertEquals(listOf(savedA),playlist.playable(mapOf(1 to savedA)))
        assertEquals(listOf(b,savedA,b),playlist.playable(mapOf(1 to savedA,2 to b)))
    }
    @Test fun corruptOrUnsupportedRecordsCannotBreakTheOfflineLibrary(){
        assertNull(OfflinePlaylistCodec.decode("not json"))
        assertNull(OfflinePlaylistCodec.decode(OfflinePlaylistCodec.encode(playlist).replace("\"version\":1","\"version\":2")))
        assertNull(OfflinePlaylistCodec.decode("""{"version":1,"id":"","name":"x","coverUri":"","songs":[]}"""))
        assertNull(OfflinePlaylistCodec.decode(OfflinePlaylistCodec.encode(playlist).replace("\"durationMs\":180000","\"durationMs\":\"bad\"")))
    }
}
