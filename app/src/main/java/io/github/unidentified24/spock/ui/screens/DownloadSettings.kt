package io.github.unidentified24.spock.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.unidentified24.spock.features.FeatureSettings
import io.github.unidentified24.spock.features.downloads.*
import io.github.unidentified24.spock.ui.components.*
import kotlinx.coroutines.*
import io.github.unidentified24.spock.data.entity.OfflinePlaylist
import io.github.unidentified24.spock.data.preferences.*
import io.github.unidentified24.spock.di.Downloads
import io.github.unidentified24.spock.di.ListeningState
import androidx.compose.ui.unit.dp

@Composable fun DownloadSettings(){
    val options by FeatureSettings.state.collectAsState();val rules=options.downloads
    val context=LocalContext.current.applicationContext;val scope=rememberCoroutineScope()
    fun restart(){scope.launch(Dispatchers.IO){DownloadScheduler.kick(context,replace=true)}}
    StudioCard(Modifier.fillMaxWidth()){
        Text("When to download",style=MaterialTheme.typography.headlineSmall)
        PreferenceSwitch("Wi-Fi only",rules.wifiOnly){v->FeatureSettings.update{it.copy(downloads=it.downloads.copy(wifiOnly=v))};restart()}
        PreferenceSwitch("Only while charging",rules.chargingOnly){v->FeatureSettings.update{it.copy(downloads=it.downloads.copy(chargingOnly=v))};restart()}
        PreferenceSwitch("Schedule downloads",rules.scheduleMinute>=0){v->
            val minute=if(v)22*60 else -1;FeatureSettings.update{it.copy(downloads=it.downloads.copy(scheduleMinute=minute))}
            scope.launch(Dispatchers.IO){DownloadLedger.reschedule(DownloadPolicy.nextWindow(System.currentTimeMillis(),minute));DownloadScheduler.kick(context)}
        }
        if(rules.scheduleMinute>=0)PreferenceTime("Start around",rules.scheduleMinute){minute->FeatureSettings.update{it.copy(downloads=it.downloads.copy(scheduleMinute=minute))};scope.launch(Dispatchers.IO){DownloadLedger.reschedule(DownloadPolicy.nextWindow(System.currentTimeMillis(),minute));DownloadScheduler.kick(context)}}
        PreferenceSlider("Transfers at once",rules.parallel.toFloat(),1f..3f,1,{it.toInt().toString()}){v->FeatureSettings.update{it.copy(downloads=it.downloads.copy(parallel=v.toInt()))}}
    }
    StudioCard(Modifier.fillMaxWidth()){
        Text("Recovery",style=MaterialTheme.typography.headlineSmall)
        PreferenceSwitch("Retry interrupted downloads",rules.automaticRetry){v->FeatureSettings.update{it.copy(downloads=it.downloads.copy(automaticRetry=v))}}
        if(rules.automaticRetry)PreferenceSlider("Extra attempts",rules.retryLimit.toFloat(),0f..8f,7,{it.toInt().toString()}){v->FeatureSettings.update{it.copy(downloads=it.downloads.copy(retryLimit=v.toInt()))}}
        Text("Saved jobs resume when Android allows them to run. Download conditions and schedules still apply.",style=MaterialTheme.typography.bodySmall)
    }
    SmartOfflineSettings()
}

@Composable private fun SmartOfflineSettings(){
    val options by FeatureSettings.state.collectAsState();val context=LocalContext.current.applicationContext;val scope=rememberCoroutineScope()
    var busy by remember{mutableStateOf(false)};var message by remember{mutableStateOf("")}
    var audit by remember{mutableStateOf<DownloadStorage.Audit?>(null)}
    var storageInfo by remember{mutableStateOf<Triple<Long,Long,Long>?>(null)}
    var playlistUsage by remember{mutableStateOf(emptyList<Pair<String,Long>>())}
    var pack by remember{mutableStateOf(false)};var name by remember{mutableStateOf("")};var source by remember{mutableStateOf("queue")}
    LaunchedEffect(Unit){val result=withContext(Dispatchers.IO){
        fun bytes(root:java.io.File):Long=if(!root.exists())0L else if(root.isFile)root.length() else root.listFiles()?.sumOf(::bytes) ?: 0L
        val entries=io.github.unidentified24.spock.data.preferences.getDownloadedEntries(context).distinctBy{it.second}
        val downloads=entries.sumOf{java.io.File(it.second).length().coerceAtLeast(0)}
        val bySong=entries.groupBy{it.first.id}.mapValues{(_,items)->items.sumOf{java.io.File(it.second).length().coerceAtLeast(0)}}
        val usage=io.github.unidentified24.spock.data.preferences.getOfflinePlaylists(context).map{playlist->playlist.name to playlist.songs.distinctBy{it.id}.sumOf{bySong[it.id] ?: 0L}}
            .filter{it.second>0}.sortedByDescending{it.second}
        Triple(Triple(downloads,bytes(java.io.File(context.cacheDir,"media")),android.os.StatFs(context.filesDir.path).availableBytes),usage,Unit)
    };storageInfo=result.first;playlistUsage=result.second}
    fun size(bytes:Long):String=when{bytes>=1024L*1024*1024->"%.1f GB".format(bytes/1024.0/1024/1024);bytes>=1024L*1024->"%.0f MB".format(bytes/1024.0/1024);else->"${bytes/1024} KB"}
    fun changed(){SmartOffline.schedule(context,now=true)}
    StudioCard(Modifier.fillMaxWidth()){
        Text("Smart offline",style=MaterialTheme.typography.headlineSmall)
        PreferenceSwitch("Offline only",options.downloads.offlineOnly,"Play saved tracks and local files without finding online streams."){value->FeatureSettings.update{it.copy(downloads=it.downloads.copy(offlineOnly=value))};scope.launch(Dispatchers.IO){DownloadScheduler.kick(context,replace=true)}}
        PreferenceSwitch("Download new favorites",options.downloads.favoriteDownloads){value->FeatureSettings.update{it.copy(downloads=it.downloads.copy(favoriteDownloads=value))};changed()}
        PreferenceChoice("Keep most-played tracks offline",options.downloads.mostPlayedCount.toString(),listOf(0,25,50,100,200,500).map{it.toString() to if(it==0)"Off" else "$it tracks"}){value->FeatureSettings.update{it.copy(downloads=it.downloads.copy(mostPlayedCount=value.toInt()))};changed()}
        PreferenceTime("Daily refresh",options.downloads.smartHour*60){minute->FeatureSettings.update{it.copy(downloads=it.downloads.copy(smartHour=minute/60))};changed()}
        Text("${options.downloads.smartPlaylists.size} playlists kept up to date. Choose playlists using their download controls.",style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={changed();message="Refresh requested. Your download conditions still apply."}){Text("Refresh smart downloads")}
        OutlinedButton(onClick={name="Travel pack";pack=true}){Text("Create a travel pack")}
    }
    StudioCard(Modifier.fillMaxWidth()){
        Text("Offline storage",style=MaterialTheme.typography.headlineSmall)
        storageInfo?.let{(downloadBytes,cacheBytes,freeBytes)->
            Text("${size(downloadBytes)} saved offline · ${size(cacheBytes)} streaming cache · ${size(freeBytes)} free for Spock",style=MaterialTheme.typography.bodySmall)
            Text("${getDownloadedEntries(context).distinctBy{it.second}.size} downloaded tracks",style=MaterialTheme.typography.bodySmall)
        }
        playlistUsage.take(8).forEach{(name,bytes)->Text("$name · ${size(bytes)}",style=MaterialTheme.typography.bodySmall)}
        PreferenceChoice("Streaming cache limit",options.storage.cacheMiB.toString(),listOf(128,256,512,1024,2048,4096,8192).map{it.toString() to if(it<1024)"$it MB" else "${it/1024} GB"}){value->FeatureSettings.update{it.copy(storage=it.storage.copy(cacheMiB=value.toInt()))}}
        Text("Applies when Spock next opens the streaming cache. Offline downloads are stored separately.",style=MaterialTheme.typography.bodySmall)
        PreferenceChoice("Download limit",options.storage.maxDownloadsMiB.toString(),listOf(0,512,1024,2048,5120,10240,20480,51200).map{it.toString() to if(it==0)"No app limit" else if(it<1024)"$it MB" else "${it/1024} GB"}){value->FeatureSettings.update{it.copy(storage=it.storage.copy(maxDownloadsMiB=value.toInt()))}}
        PreferenceSwitch("Make room with rarely played tracks",options.storage.removeRare,"Only old downloads outside your favorites and smart playlists. The current track is protected."){value->FeatureSettings.update{it.copy(storage=it.storage.copy(removeRare=value))}}
        if(options.storage.removeRare)PreferenceChoice("Unplayed for",options.storage.rareAfterDays.toString(),listOf(30,60,90,180,365).map{it.toString() to "$it days"}){value->FeatureSettings.update{it.copy(storage=it.storage.copy(rareAfterDays=value.toInt()))}}
        OutlinedButton(enabled=!busy,onClick={busy=true;message="";scope.launch{
            try{audit=DownloadStorage.audit(context);message="Checked ${audit!!.checked} files · ${audit!!.damaged.size} need repair"}
            catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){message="Couldn't check every file. Try again."}finally{busy=false}
        }}){Text(if(busy)"Checking…" else "Check saved files")}
        audit?.takeIf{it.damaged.isNotEmpty()}?.let{result->Button(enabled=!busy,onClick={busy=true;scope.launch{try{val count=DownloadStorage.repair(context,result.damaged);message="$count repairs added to Downloads";audit=null}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){message="Couldn't queue repairs. Try again."}finally{busy=false}}}){Text("Download damaged files again")}}
        if(message.isNotBlank())Text(message,style=MaterialTheme.typography.bodySmall)
    }
    if(pack)AlertDialog(onDismissRequest={pack=false},title={Text("Travel pack")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        OutlinedTextField(name,{name=it.take(100)},label={Text("Name")},singleLine=true)
        PreferenceChoice("Include",source,listOf("queue" to "Current queue","favorites" to "Favorites","played" to "Most played","saved" to "All downloaded tracks")){source=it}
    }},confirmButton={TextButton(enabled=name.isNotBlank()&&!busy,onClick={busy=true;scope.launch{
        try{
            val queue=ListeningState.current?.queue?.value.orEmpty()
            val songs=withContext(Dispatchers.IO){
                val catalog=(queue+TrackCatalog.read(context)+getOfflinePlaylists(context).flatMap{it.songs}+getLocalSongs(context)).distinctBy{it.id}
                when(source){"favorites"->catalog.filter{it.id in getLikedSongIds(context)};"played"->SmartOffline.choose(catalog,emptySet(),ListeningMeter.samples(context).groupBy{it.songId}.mapValues{(_,samples)->samples.sumOf{it.ms}},false,100);"saved"->getDownloadedSongs(context);else->queue}
            }
            if(songs.isEmpty())message="No tracks available for this pack yet. Play or save some music first."
            else {
                withContext(Dispatchers.IO){saveOfflinePlaylist(context,OfflinePlaylist("pack-"+java.util.UUID.randomUUID(),name.trim(),songs.first().coverUri,songs))}
                Downloads.enqueueAll(songs,context);message="Travel pack added to Downloads";pack=false
            }
        }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){message="Couldn't save the travel pack. Try again."}finally{busy=false}
    }}){Text("Save pack")}},dismissButton={TextButton(onClick={pack=false}){Text("Cancel")}})
}
