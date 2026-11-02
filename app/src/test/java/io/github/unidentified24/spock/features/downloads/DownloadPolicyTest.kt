package io.github.unidentified24.spock.features.downloads

import io.github.unidentified24.spock.data.entity.SongsModel
import io.github.unidentified24.spock.features.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class DownloadPolicyTest {
    private fun task(id:Int,priority:Int=0,at:Long=0)=DownloadTask(SongsModel(id,"x","","a","","search x"),priority=priority,requestedAt=at)
    @Test fun priorityDoesNotRunPausedOrFutureJobsAndKeepsFifoWithinPriority(){
        val a=task(1,0,1);val b=task(2,50,2);val c=task(3,50,3)
        assertEquals(listOf(b,c,a),DownloadPolicy.ordered(listOf(c,a,b,task(4,100).copy(state=DownloadState.PAUSED),task(5,100).copy(runAfter=200)),100))
    }
    @Test fun wifiAndChargingRulesNeverBlockOfflineFileCopiesWithoutNetwork(){
        val rules=DownloadOptions(wifiOnly=true,chargingOnly=true)
        assertFalse(DownloadPolicy.canRun(task(1),rules,false,true,true,0))
        assertFalse(DownloadPolicy.canRun(task(1),rules,true,true,false,0))
        assertTrue(DownloadPolicy.canRun(task(1).copy(song=task(1).song.copy(url="content://music/1")),rules,false,false,true,0))
        assertTrue(DownloadPolicy.canRun(task(1),rules,true,true,true,0))
    }
    @Test fun nextScheduleKeepsConfiguredLocalTimeAcrossDaylightSavingGap(){
        val zone=ZoneId.of("America/Toronto")
        val now=ZonedDateTime.of(2026,3,8,4,0,0,0,zone)
        val next=Instant.ofEpochMilli(DownloadPolicy.nextWindow(now.toInstant().toEpochMilli(),150,zone)).atZone(zone)
        assertEquals(LocalDate.of(2026,3,9),next.toLocalDate());assertEquals(LocalTime.of(2,30),next.toLocalTime())
    }
    @Test fun retryDelayIsBoundedAndEstimatesRespectDurationAndQuality(){
        assertEquals(15000L,DownloadPolicy.retryAt(0,1));assertEquals(480000L,DownloadPolicy.retryAt(0,100))
        val song=task(1).song.copy(durationMs=200000)
        assertTrue(DownloadPolicy.estimateBytes(song,"LOSSLESS").first>DownloadPolicy.estimateBytes(song,"HIGH").last)
        assertTrue(DownloadPolicy.estimateBytes(song.copy(durationMs=400000),"HIGH").first==2*DownloadPolicy.estimateBytes(song,"HIGH").first)
    }
    @Test fun importedOptionsCannotCreateInvalidAudioOrUnboundedWorkerCounts(){
        val value=AppOptions(downloads=DownloadOptions(parallel=999,retryLimit=-4),sound=SoundOptions(speed=Float.NaN,balance=4f,gainDb=Float.POSITIVE_INFINITY)).normalized()
        assertEquals(3,value.downloads.parallel);assertEquals(0,value.downloads.retryLimit)
        assertEquals(1f,value.sound.speed,0f);assertEquals(1f,value.sound.balance,0f);assertEquals(0f,value.sound.gainDb,0f)
    }
    @Test fun portableSettingsNeverCarryDeviceFolderPermissions(){
        val original=AppOptions(storage=StorageOptions(downloadFolder="content://private/tree"),backups=BackupOptions(folder="content://backup"),local=LocalOptions(folders=setOf("content://music")))
        val portable=FeatureSettings.portable(original)
        assertEquals("",portable.storage.downloadFolder);assertEquals("",portable.backups.folder);assertTrue(portable.local.folders.isEmpty())
    }
}
