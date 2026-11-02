package io.github.unidentified24.spock.features.downloads

import android.content.Context
import androidx.work.*
import io.github.unidentified24.spock.data.api.Api
import io.github.unidentified24.spock.data.api.Response
import io.github.unidentified24.spock.data.entity.*
import io.github.unidentified24.spock.data.preferences.*
import io.github.unidentified24.spock.di.Downloads
import io.github.unidentified24.spock.features.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import java.util.concurrent.TimeUnit

object TrackCatalog {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    @Synchronized fun read(context:Context):List<SongsModel> = runCatching {
        FeatureSettings.json.decodeFromString<List<SongsModel>>(context.getSharedPreferences("track_catalog",0).getString("songs","[]")!!)
    }.getOrDefault(emptyList())
    @Synchronized fun store(context:Context,songs:List<SongsModel>){
        if(songs.isEmpty())return
        val previous=read(context).associateBy{it.id}
        val enriched=songs.map{song->val old=previous[song.id];if(old==null)song else song.copy(albumId=song.albumId.ifBlank{old.albumId},artistIds=song.artistIds.ifEmpty{old.artistIds},artistNames=song.artistNames.ifEmpty{old.artistNames},genres=song.genres.ifEmpty{old.genres},year=song.year.takeIf{it>0} ?: old.year,albumTrackCount=song.albumTrackCount.takeIf{it>0} ?: old.albumTrackCount)}
        val known=(enriched+previous.values).distinctBy{it.id}.take(15000)
        context.getSharedPreferences("track_catalog",0).edit().putString("songs",FeatureSettings.json.encodeToString(known)).apply()
    }
    fun offer(context:Context,songs:List<SongsModel>){if(FeatureSettings.value.privacy.privateListening)return;scope.launch{store(context,songs)}}
}

object SmartOffline {
    private const val DAILY="spock-smart-offline-daily"
    private const val ONCE="spock-smart-offline-now"
    private val favoriteScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    fun favoriteAdded(context:Context,song:SongsModel){
        if(!FeatureSettings.value.downloads.favoriteDownloads || FeatureSettings.value.privacy.privateListening || FeatureSettings.value.downloads.offlineOnly)return
        favoriteScope.launch { runCatching { Downloads.enqueueAll(listOf(song),context.applicationContext,now=true,priority=2) } }
    }
    fun schedule(context:Context,now:Boolean=false){
        val rules=FeatureSettings.value.downloads
        val manager=WorkManager.getInstance(context)
        if(rules.smartPlaylists.isEmpty()&&!rules.favoriteDownloads&&rules.mostPlayedCount==0){manager.cancelUniqueWork(DAILY);manager.cancelUniqueWork(ONCE);return}
        val constraints=DownloadScheduler.networkConstraints(rules.wifiOnly,rules.chargingOnly)
        val data=workDataOf("profile" to SessionIdentity.id)
        if(now)manager.enqueueUniqueWork(ONCE,ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<SmartOfflineWorker>().setConstraints(constraints).setInputData(data).build())
        val delay=DownloadPolicy.nextWindow(System.currentTimeMillis(),rules.smartHour*60)-System.currentTimeMillis()
        manager.enqueueUniquePeriodicWork(DAILY,ExistingPeriodicWorkPolicy.UPDATE,PeriodicWorkRequestBuilder<SmartOfflineWorker>(24,TimeUnit.HOURS).setConstraints(constraints).setInitialDelay(delay.coerceAtLeast(1000),TimeUnit.MILLISECONDS).setInputData(data).build())
    }
    suspend fun full(flow:Flow<Response<List<SongsModel>>>):List<SongsModel>{
        var latest:List<SongsModel>?=null
        flow.collect{when(it){is Response.Success->latest=it.data;is Response.Error->throw IllegalStateException("Couldn't refresh the complete playlist");else->Unit}}
        return latest ?: throw IllegalStateException("Couldn't refresh this playlist")
    }
    fun choose(catalog:List<SongsModel>,liked:Set<Int>,totals:Map<Int,Long>,favorites:Boolean,mostPlayed:Int):List<SongsModel>{
        val top=totals.filterValues{it>0}.entries.sortedWith(compareByDescending<Map.Entry<Int,Long>>{it.value}.thenBy{it.key}).take(mostPlayed).map{it.key}.toSet()
        return catalog.distinctBy{it.id}.filter{it.id in top||(favorites&&it.id in liked)}
    }
}

class SmartOfflineWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
    override suspend fun doWork():Result=withContext(Dispatchers.IO){
        val profile=inputData.getString("profile") ?: SessionIdentity.id
        if(profile!=SessionIdentity.id)return@withContext Result.success()
        val rules=FeatureSettings.value.downloads
        if(rules.offlineOnly)return@withContext Result.success()
        val report=applicationContext.getSharedPreferences("smart_offline_status",0)
        try{
            val api=Api(applicationContext);val selected=mutableListOf<SongsModel>()
            for(id in rules.smartPlaylists){
                ensureActive();if(profile!=SessionIdentity.id)return@withContext Result.success()
                val cached=getOfflinePlaylist(applicationContext,id) ?: continue
                val local=io.github.unidentified24.spock.features.library.LibraryStore.state.value.playlists.firstOrNull{it.id==id&&it.sourceId.isBlank()}
                val songs=if(local!=null)local.entries.map{it.song}else if(id.startsWith("local-")||id.startsWith("pack-"))cached.songs else withTimeout(90_000){SmartOffline.full(api.getPlaylistSongs(id))}
                if(profile!=SessionIdentity.id)return@withContext Result.success()
                saveOfflinePlaylist(applicationContext,cached.copy(songs=songs));selected+=songs
            }
            if(rules.favoriteDownloads&&io.github.unidentified24.spock.data.api.SpotifySession.spDc(applicationContext).isNotBlank()){
                val favorites=withTimeout(90_000){SmartOffline.full(api.getLikedSongs())};TrackCatalog.store(applicationContext,favorites)
            }
            val catalog=(TrackCatalog.read(applicationContext)+getOfflinePlaylists(applicationContext).flatMap{it.songs}+getLocalSongs(applicationContext)).distinctBy{it.id}
            val totals=ListeningMeter.samples(applicationContext).groupBy{it.songId}.mapValues{(_,rows)->rows.sumOf{it.ms}}
            selected+=SmartOffline.choose(catalog,getLikedSongIds(applicationContext),totals,rules.favoriteDownloads,rules.mostPlayedCount)
            if(profile!=SessionIdentity.id)return@withContext Result.success()
            Downloads.enqueueAll(selected,applicationContext)
            report.edit().putLong("checked",System.currentTimeMillis()).putString("error","").apply();Result.success()
        }catch(cancelled:CancellationException){throw cancelled}
        catch(_:Exception){report.edit().putString("error","Some music couldn't be refreshed. Saved tracks are still available.").apply();Result.retry()}
    }
}
