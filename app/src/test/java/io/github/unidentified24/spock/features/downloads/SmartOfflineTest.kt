package io.github.unidentified24.spock.features.downloads

import io.github.unidentified24.spock.data.entity.SongsModel
import io.github.unidentified24.spock.data.api.Response
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Test

class SmartOfflineTest {
    private fun song(id:Int)=SongsModel(id,"Song $id","","Artist","","query$id")
    @Test fun favoritesAndTopPlayedAreCombinedWithoutDuplicatesOrUnplayedGuesses(){
        val tracks=(1..5).map(::song)
        val chosen=SmartOffline.choose(tracks+song(2),setOf(2,4),mapOf(1 to 100L,2 to 300L,3 to 0L,5 to 200L),true,2)
        assertEquals(setOf(2,4,5),chosen.map{it.id}.toSet());assertEquals(3,chosen.size)
    }
    @Test fun incompleteRefreshNeverReplacesTheLastCompletePlaylist()=runBlocking {
        try{SmartOffline.full(flowOf(Response.Success(listOf(song(1))),Response.Error("page two failed")));fail("Accepted partial playlist")}
        catch(_:IllegalStateException){}
        assertEquals(listOf(song(1),song(2)),SmartOffline.full(flowOf(Response.Success(listOf(song(1))),Response.Success(listOf(song(1),song(2))))))
    }
    @Test fun offlineModeBlocksRemoteJobsButStillAllowsLocalCopies(){
        val remote=DownloadTask(song(1));val rules=io.github.unidentified24.spock.features.DownloadOptions(offlineOnly=true)
        assertFalse(DownloadPolicy.canRun(remote,rules,true,true,true,Long.MAX_VALUE))
        assertTrue(DownloadPolicy.canRun(remote.copy(song=song(1).copy(url="content://audio/1")),rules,false,false,true,Long.MAX_VALUE))
    }
    @Test fun integrityDigestDetectsChangedBytesEvenWhenLengthIsUnchanged(){
        val file=java.io.File.createTempFile("spock-integrity",".audio")
        try{file.writeText("original audio");val first=DownloadStorage.digest(file);file.writeText("modified audio");assertNotEquals(first,DownloadStorage.digest(file));assertEquals(64,first.length)}finally{file.delete()}
    }
}
