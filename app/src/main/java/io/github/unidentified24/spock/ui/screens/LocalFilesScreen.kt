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
    var note by remember{mutableStateOf("Imported music stays on this device")}
    fun accept(uris:List<android.net.Uri>) {
        if(uris.isEmpty())return
        importing=true
        scope.launch {
            runCatching {withContext(Dispatchers.IO){
                val tracks=io.github.unidentified24.spock.data.local.LocalImport.importFiles(context,uris)
                addLocalTracks(context,tracks);getLocalSongs(context)
            }}.onSuccess{songs=it;note="${songs.size} tracks in your local collection"}.onFailure{note="Couldn't import these files. Try another audio file."}
            importing=false
        }
    }
    val picker=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()){accept(it)}
    val folder=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()){uri->
        if(uri!=null){importing=true;scope.launch{
            runCatching{withContext(Dispatchers.IO){val tracks=io.github.unidentified24.spock.data.local.LocalImport.importFolder(context,uri);addLocalTracks(context,tracks);getLocalSongs(context)}}
                .onSuccess{songs=it;note="${songs.size} tracks in your local collection"}.onFailure{note="Couldn't read that folder. Check folder access."}
            importing=false
        }}
    }
    val permission=if(android.os.Build.VERSION.SDK_INT>=33) android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE
    fun scan() {
        importing=true
        scope.launch {
            runCatching {withContext(Dispatchers.IO){
                val found=io.github.unidentified24.spock.data.local.DeviceAudio.scan(context)
                addLocalTracks(context,found);getLocalSongs(context)
            }}.onSuccess{songs=it;note="${it.size} local tracks · files stay in their original folders"}
                .onFailure{note="Device access unavailable. You can still use Add files or Add folder."}
            importing=false
        }
    }
    val access=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()){granted->if(granted)scan()else note="Choose individual files or a folder, or grant audio access to scan your phone."}
    LaunchedEffect(Unit){if(androidx.core.content.ContextCompat.checkSelfPermission(context,permission)==android.content.pm.PackageManager.PERMISSION_GRANTED)scan()}
    StudioCollection(navController,"Made local.",note,songs.firstOrNull()?.coverUri.orEmpty(),songs,extra={
        Button(enabled=!importing,onClick={if(androidx.core.content.ContextCompat.checkSelfPermission(context,permission)==android.content.pm.PackageManager.PERMISSION_GRANTED)scan()else access.launch(permission)}) {Text("Find music on this phone")}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled=!importing,onClick={picker.launch(arrayOf("audio/*"))},shape=RoundedCornerShape(14.dp)){Text(if(importing)"Importing…" else "Add files")}
            TextButton(enabled=!importing,onClick={folder.launch(null)}){Text("Add folder")}
        }
    })
}
