package io.github.unidentified24.spock.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.unidentified24.spock.data.api.*
import io.github.unidentified24.spock.data.entity.SongsModel
import io.github.unidentified24.spock.data.preferences.*
import io.github.unidentified24.spock.features.FeatureSettings
import io.github.unidentified24.spock.features.downloads.TrackCatalog
import io.github.unidentified24.spock.ui.theme.*
import kotlinx.coroutines.*

@Composable fun TrackPickerDialog(dismiss:()->Unit,save:(List<SongsModel>)->Unit){
    val context=LocalContext.current.applicationContext
    var query by remember{mutableStateOf("")};var local by remember{mutableStateOf<List<SongsModel>>(emptyList())}
    var online by remember{mutableStateOf<List<SongsModel>>(emptyList())};var selected by remember{mutableStateOf<Map<Int,SongsModel>>(emptyMap())}
    var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")}
    LaunchedEffect(Unit){local=withContext(Dispatchers.IO){(getLocalSongs(context)+getDownloadedSongs(context)+TrackCatalog.read(context)).distinctBy{it.id}}}
    LaunchedEffect(query){
        online=emptyList();error="";busy=false
        if(query.trim().length>=2&&!FeatureSettings.value.downloads.offlineOnly){
            delay(300);busy=true
            try{withContext(Dispatchers.IO){Api(context).searchTracks(query.trim()).collect{when(it){is Response.Success->online=it.data;is Response.Error->error="Online search couldn't connect. Your saved music is still shown.";else->Unit}}}}
            catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){error="Online search couldn't connect."}finally{busy=false}
        }
    }
    val display=(local.filter{query.isBlank()||it.title.contains(query,true)||it.singer.contains(query,true)}+online).distinctBy{it.id}
    AlertDialog(onDismissRequest=dismiss,title={Text("Add tracks")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedTextField(query,{query=it.take(200)},label={Text("Search your music and Spotify")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Text("${selected.size} selected",color=AppPalette)
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error.isNotBlank())Text(error,style=MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.heightIn(max=360.dp)){
            items(display,key={it.id}){song->Row(Modifier.fillMaxWidth().clickable{selected=if(song.id in selected)selected-song.id else selected+(song.id to song)}.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
                Checkbox(song.id in selected,{checked->selected=if(checked)selected+(song.id to song)else selected-song.id})
                Column(Modifier.weight(1f)){Text(song.title,maxLines=2,style=MaterialTheme.typography.titleSmall);Text(song.singer,maxLines=1,color=TextMuted,style=MaterialTheme.typography.bodySmall)}
            }}
            if(display.isEmpty()&&!busy)item{Text("Search for songs or import local files from Collection.")}
        }
    }},confirmButton={TextButton(enabled=selected.isNotEmpty(),onClick={save(selected.values.toList());dismiss()}){Text("Add ${selected.size}")}},dismissButton={TextButton(onClick=dismiss){Text("Cancel")}})
}
