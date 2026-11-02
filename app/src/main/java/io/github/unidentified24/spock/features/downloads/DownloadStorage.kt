package io.github.unidentified24.spock.features.downloads

import android.content.Context
import io.github.unidentified24.spock.data.entity.SongsModel
import io.github.unidentified24.spock.data.preferences.*
import io.github.unidentified24.spock.di.ListeningState
import io.github.unidentified24.spock.di.Downloads
import io.github.unidentified24.spock.features.FeatureSettings
import io.github.unidentified24.spock.playback.OfflineFiles
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import kotlinx.coroutines.*

object DownloadStorage {
    data class Audit(val checked:Int,val damaged:List<SongsModel>)
    fun digest(file:File):String {
        val hash=MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use{input->val bytes=ByteArray(65536);while(true){val n=input.read(bytes);if(n<0)break;hash.update(bytes,0,n)}}
        return hash.digest().joinToString(""){"%02x".format(it)}
    }
    fun record(context:Context,song:SongsModel,file:File){context.getSharedPreferences("download_integrity",0).edit().putString(song.id.toString(),digest(file)).apply()}
    fun bytes(context:Context)=getDownloadedEntries(context).distinctBy{it.second}.sumOf{File(it.second).length().coerceAtLeast(0)}
    private fun owned(context:Context,path:String):Boolean=runCatching{File(path).canonicalFile.toPath().startsWith(File(context.filesDir,"downloads").canonicalFile.toPath())}.getOrDefault(false)
    fun rarelyPlayed(context:Context):List<SongsModel>{
        val settings=FeatureSettings.value;val cut=LocalDate.now().minusDays(settings.storage.rareAfterDays.toLong())
        val recent=ListeningMeter.samples(context).filter{it.date>=cut}.map{it.songId}.toSet()
        val protected=settings.downloads.smartPlaylists.flatMap{getOfflinePlaylist(context,it)?.songs.orEmpty()}.map{it.id}.toSet()+getLikedSongIds(context)+setOfNotNull(ListeningState.current?.songId?.value)
        val cutoffMillis=cut.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        return getDownloadedEntries(context).filter{(song,path)->owned(context,path)&&song.id !in recent&&song.id !in protected&&File(path).lastModified()<cutoffMillis}.map{it.first}
    }
    @Synchronized fun allowCommit(context:Context,song:SongsModel,file:File):Boolean {
        val limit=FeatureSettings.value.storage.maxDownloadsMiB.toLong()*1048576L
        if(limit<=0)return true
        val replaced=getDownloadedEntries(context).filter{it.first.id==song.id}.sumOf{File(it.second).length()}
        if(bytes(context)-replaced+file.length()<=limit)return true
        if(FeatureSettings.value.storage.removeRare)for(old in rarelyPlayed(context)){
            removeDownload(context,old.id.toString())
            if(bytes(context)-replaced+file.length()<=limit)return true
        }
        return false
    }
    @Synchronized fun finish(context:Context,song:SongsModel,tmp:File,out:File):Boolean {
        if(!allowCommit(context,song,tmp))return false
        val checksum=digest(tmp)
        if(!tmp.renameTo(out))return false
        context.getSharedPreferences("download_integrity",0).edit().putString(song.id.toString(),checksum).apply()
        addDownload(context,song,out.path)
        return true
    }
    suspend fun audit(context:Context):Audit=withContext(Dispatchers.IO){
        val saved=getDownloadedEntries(context);val hashes=context.getSharedPreferences("download_integrity",0)
        val damaged=saved.filter{(song,path)->
            ensureActive();val file=File(path)
            if(!owned(context,path))false
            else if(!OfflineFiles.audioHeader(file))true
            else {val actual=digest(file);val expected=hashes.getString(song.id.toString(),null)
                if(expected==null){hashes.edit().putString(song.id.toString(),actual).apply();false}else expected!=actual}
        }.map{it.first}
        Audit(saved.size,damaged)
    }
    suspend fun repair(context:Context,songs:List<SongsModel>):Int=withContext(Dispatchers.IO){
        val active=ListeningState.current?.songId?.value
        val eligible=songs.filter{it.id!=active}
        eligible.forEach{removeDownload(context,it.id.toString())}
        Downloads.enqueueAll(eligible,context,now=true)
    }
}
