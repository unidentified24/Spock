package io.github.unidentified24.spock.ui.screens
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import io.github.unidentified24.spock.data.api.Response
import io.github.unidentified24.spock.data.entity.*
import io.github.unidentified24.spock.data.preferences.*
import io.github.unidentified24.spock.di.SongPlayer
import io.github.unidentified24.spock.ui.components.*
import io.github.unidentified24.spock.ui.navigation.*
import io.github.unidentified24.spock.ui.theme.*
import io.github.unidentified24.spock.ui.viewmodel.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable fun LocalFilesScreen(navController:NavController) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var songs by remember {mutableStateOf(getLocalSongs(context))};var importing by remember{mutableStateOf(false)}
    var missing by remember{mutableStateOf(emptyList<io.github.unidentified24.spock.data.preferences.LocalTrack>())}
    var relinkTarget by remember{mutableStateOf<io.github.unidentified24.spock.data.preferences.LocalTrack?>(null)}
    var excludeText by remember{mutableStateOf(io.github.unidentified24.spock.features.FeatureSettings.value.local.excluded.joinToString(", "))}
    var note by remember{mutableStateOf("Play files where they are. Scan your phone, choose files, or open audio from your file manager.")}
    fun accept(uris:List<android.net.Uri>) {
        if(uris.isEmpty())return
        importing=true
        scope.launch {
            runCatching {withContext(Dispatchers.IO){
                val tracks=io.github.unidentified24.spock.data.local.LocalImport.importFiles(context,uris)
                addLocalTracks(context,tracks);getLocalSongs(context)
            }}.onSuccess{songs=it;missing=withContext(Dispatchers.IO){findMissingLocalTracks(context)};note="${songs.size} tracks in your local collection"}.onFailure{note="Couldn't import these files. Try another audio file."}
            importing=false
        }
    }
    val picker=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()){accept(it)}
    val folder=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()){uri->
        if(uri!=null){val key=uri.toString();io.github.unidentified24.spock.features.FeatureSettings.update{it.copy(local=it.local.copy(folders=it.local.folders+key))};importing=true;scope.launch{
            runCatching{withContext(Dispatchers.IO){val tracks=io.github.unidentified24.spock.data.local.LocalImport.importFolder(context,uri,io.github.unidentified24.spock.features.FeatureSettings.value.local.excluded);addLocalTracks(context,tracks);getLocalSongs(context)}}
                .onSuccess{songs=it;missing=withContext(Dispatchers.IO){findMissingLocalTracks(context)};note="${songs.size} tracks in your local collection"}.onFailure{note="Couldn't read that folder. Check folder access."}
            importing=false
        }}
    }
    val relink=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()){uri->
        val original=relinkTarget;relinkTarget=null
        if(uri!=null&&original!=null){importing=true;scope.launch{runCatching{withContext(Dispatchers.IO){
            val replacement=io.github.unidentified24.spock.data.local.LocalImport.relinkFile(context,original,uri)
                ?: error("Replacement audio could not be read")
            io.github.unidentified24.spock.data.preferences.updateLocalTrack(context,original.uri,replacement)
            getLocalSongs(context)
        }}.onSuccess{songs=it;missing=withContext(Dispatchers.IO){findMissingLocalTracks(context)};note="File relinked"}.onFailure{note="Couldn't relink this file. Choose a readable audio file."};importing=false}}
    }
    val permission=if(android.os.Build.VERSION.SDK_INT>=33) android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE
    fun scan() {
        importing=true
        scope.launch {
            runCatching {withContext(Dispatchers.IO){
                val found=io.github.unidentified24.spock.data.local.DeviceAudio.scan(context)
                addLocalTracks(context,found);getLocalSongs(context)
            }}.onSuccess{songs=it;missing=withContext(Dispatchers.IO){findMissingLocalTracks(context)};note="${it.size} local tracks · files stay in their original folders"}
                .onFailure{note="Device access unavailable. You can still use Add files or Add folder."}
            importing=false
        }
    }
    val access=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()){granted->if(granted)scan()else note="Choose individual files or a folder, or grant audio access to scan your phone."}
    LaunchedEffect(Unit){missing=withContext(Dispatchers.IO){findMissingLocalTracks(context)};if(androidx.core.content.ContextCompat.checkSelfPermission(context,permission)==android.content.pm.PackageManager.PERMISSION_GRANTED)scan()}
    StudioCollection(navController,"Made local.",note,songs.firstOrNull()?.coverUri.orEmpty(),songs,extra={
        if(missing.isNotEmpty()){
            StudioSection("Files to reconnect")
            missing.take(20).forEach{track->Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=4.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(track.title,color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis);Text(track.uri.substringAfterLast('/').take(54),color=TextMuted,style=MaterialTheme.typography.bodySmall,maxLines=1)};TextButton(enabled=!importing,onClick={relinkTarget=track;relink.launch(arrayOf("audio/*"))}){Text("Find file")}}}
        }
        Button(enabled=!importing,onClick={if(androidx.core.content.ContextCompat.checkSelfPermission(context,permission)==android.content.pm.PackageManager.PERMISSION_GRANTED)scan()else access.launch(permission)}) {Text("Find music on this phone")}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled=!importing,onClick={picker.launch(arrayOf("audio/*"))},shape=RoundedCornerShape(14.dp)){Text(if(importing)"Importing…" else "Add files")}
            TextButton(enabled=!importing,onClick={folder.launch(null)}){Text("Add folder")}
        }
        val localOptions=io.github.unidentified24.spock.features.FeatureSettings.state.collectAsState().value.local
        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Scan saved folders automatically",color=Color.White);Text("Check your chosen folders once a day",color=TextMuted,style=MaterialTheme.typography.bodySmall)};Switch(checked=localOptions.autoScan,onCheckedChange={enabled->io.github.unidentified24.spock.features.FeatureSettings.update{it.copy(local=it.local.copy(autoScan=enabled))}})}
        if(localOptions.folders.isNotEmpty()){
            localOptions.folders.forEach{saved->Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Text(android.net.Uri.parse(saved).lastPathSegment.orEmpty(),Modifier.weight(1f),color=TextMuted,maxLines=1);TextButton(onClick={io.github.unidentified24.spock.features.FeatureSettings.update{it.copy(local=it.local.copy(folders=it.local.folders-saved))};runCatching{context.contentResolver.releasePersistableUriPermission(android.net.Uri.parse(saved),android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)}}){Text("Remove")}}}
            OutlinedTextField(excludeText,{value->excludeText=value;val excluded=value.split(',').map{it.trim()}.filter{it.isNotBlank()}.toSet();io.github.unidentified24.spock.features.FeatureSettings.update{it.copy(local=it.local.copy(excluded=excluded))}},label={Text("Exclude folder names")},supportingText={Text("Separate names with commas, for example Podcasts, Ringtones")},modifier=Modifier.fillMaxWidth(),shape=StudioShape)
        }
    })
}

private fun findMissingLocalTracks(context:android.content.Context):List<io.github.unidentified24.spock.data.preferences.LocalTrack> =
    io.github.unidentified24.spock.data.preferences.getLocalTracks(context).filter{track->runCatching{
        context.contentResolver.openFileDescriptor(android.net.Uri.parse(track.uri),"r")?.use{true} ?: false
    }.getOrDefault(false).not()}
