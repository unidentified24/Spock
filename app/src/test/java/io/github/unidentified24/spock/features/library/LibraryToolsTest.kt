package io.github.unidentified24.spock.features.library

import io.github.unidentified24.spock.data.entity.*
import io.github.unidentified24.spock.features.BoundedInput
import org.junit.Assert.*
import org.junit.Test

class LibraryToolsTest {
    private fun song(id:Int,title:String="Song $id",artist:String="Artist",duration:Int=id*1000)=SongsModel(id,title,"Album",artist,"","file:///$id.wav",durationMs=duration)
    private fun entry(id:Int,at:Long=id.toLong())=PlaylistEntry("entry-$id",song(id),at)
    @Test fun duplicateFinderUsesRecordingIdentityAndKeepsTheFirstOccurrence(){
        val first=entry(1);val duplicate=first.copy(key="copy",song=first.song.copy(title="Renamed"))
        val playlist=ManagedPlaylist("local-1","Set",listOf(first,entry(2),duplicate))
        assertEquals(2,PlaylistTools.duplicates(playlist.entries).values.single().size)
        assertEquals(listOf(first,entry(2)),PlaylistTools.deduplicate(playlist,50).entries)
    }
    @Test fun bulkMergeSkipsExistingRecordingsAndRemovalUsesEntryKeys(){
        val playlist=ManagedPlaylist("local-1","Set",listOf(entry(1)))
        val updated=PlaylistTools.merge(playlist,listOf(song(1),song(2),song(2)),100)
        assertEquals(listOf(1,2),updated.entries.map{it.song.id});assertEquals(100L,updated.entries.last().addedAt)
        assertEquals(listOf(2),PlaylistTools.remove(updated,setOf("entry-1"),150).entries.map{it.song.id})
    }
    @Test fun filtersAndDateDurationSortPreserveTheOriginalPlaylist(){
        val rows=listOf(entry(3,10),entry(1,30),entry(2,20))
        assertEquals(listOf(1,2,3),PlaylistTools.filtered(rows,"","date").map{it.song.id})
        assertEquals(listOf(1,2,3),PlaylistTools.filtered(rows,"","duration").map{it.song.id})
        assertEquals(listOf(2),PlaylistTools.filtered(rows,"Song 2","artist").map{it.song.id})
        assertEquals(listOf(3,1,2),rows.map{it.song.id})
    }
    @Test fun unknownEmptyUrlsDoNotTurnEveryTrackIntoTheSameDuplicate(){
        val rows=listOf(entry(1).copy(song=song(1).copy(url="")),entry(2).copy(song=song(2).copy(url="")))
        assertTrue(PlaylistTools.duplicates(rows).isEmpty())
    }
    @Test fun clearingAndBulkRemovingNeverAccidentallyRemoveTheCurrentTrack(){
        val queue=(1..4).map{song(it)}
        assertEquals(listOf(1,2),QueueTools.clearAfter(queue,2).map{it.id})
        assertEquals(listOf(2),QueueTools.retainCurrent(queue,setOf(1,2,3,4),2).map{it.id})
        assertEquals(listOf(2,3,1,4),QueueTools.move(queue,0,2).map{it.id});assertEquals(queue,QueueTools.move(queue,-1,2))
    }
    @Test fun previousQueuesAreBoundedAndReorderingDoesNotCreateHistorySpam(){
        val initial=QueueShelf(current=listOf(song(1),song(2)),nextIds=setOf(2,99))
        val reordered=QueueTools.remember(initial,listOf(song(2),song(1)),10)
        assertTrue(reordered.recent.isEmpty());assertEquals(setOf(2),reordered.nextIds)
        var shelf=initial
        repeat(30){shelf=QueueTools.remember(shelf,listOf(song(it+5)),it.toLong())}
        assertEquals(12,shelf.recent.size);assertEquals(33,shelf.recent.first().songs.single().id)
    }
    @Test fun offlinePlaylistRoundTripKeepsExtendedMetadataAndReadsOldFiles(){
        val track=song(1).copy(addedAt=1234,albumId="album",artistIds=listOf("artist"),genres=listOf("rock"),year=1999,trackNumber=3,albumTrackCount=10)
        val playlist=OfflinePlaylist("local-1","Trip","",listOf(track,track))
        assertEquals(playlist,OfflinePlaylistCodec.decode(OfflinePlaylistCodec.encode(playlist)))
        val old="""{"version":1,"id":"legacy","name":"Old","coverUri":"","songs":[{"id":1,"title":"A","album":"","singer":"B","coverUri":"","url":"query","spotifyTrackId":"","explicit":false,"durationMs":0}]}"""
        assertEquals(0L,OfflinePlaylistCodec.decode(old)!!.songs.single().addedAt)
    }
    @Test fun boundedReadsAcceptTheExactLimitAndRejectEvenOneExtraByte(){
        assertArrayEquals(byteArrayOf(1,2,3),BoundedInput.read(byteArrayOf(1,2,3).inputStream(),3))
        try{BoundedInput.read(byteArrayOf(1,2,3,4).inputStream(),3);fail("Accepted oversized input")}catch(_:java.io.IOException){}
    }
}
