package io.github.unidentified24.spock.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.unidentified24.spock.data.backup.SpockDataBackup
import io.github.unidentified24.spock.ui.components.*
import io.github.unidentified24.spock.ui.theme.*
import kotlinx.coroutines.launch

@Composable fun BackupSettings(){
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var busy by remember{mutableStateOf(false)};var message by remember{mutableStateOf("")}
    var pending by remember{mutableStateOf<SpockDataBackup.Prepared?>(null)}
    var selectedSections by remember{mutableStateOf<Set<String>>(emptySet())};var merge by remember{mutableStateOf(false)}
    val backupOptions=io.github.unidentified24.spock.features.FeatureSettings.state.collectAsState().value.backups
    val chooseFolder=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->if(uri!=null){runCatching{context.contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)};io.github.unidentified24.spock.features.FeatureSettings.update{it.copy(backups=it.backups.copy(folder=uri.toString()))};io.github.unidentified24.spock.data.backup.AutomaticBackup.schedule(context)}}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->if(uri!=null){busy=true;scope.launch{
        try{SpockDataBackup.export(context,uri);message="Backup exported"}catch(_:Exception){message="Couldn't export backup. Please try another location."}finally{busy=false}
    }}}
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){busy=true;scope.launch{
        try{val prepared=SpockDataBackup.prepare(context,uri);pending=prepared;selectedSections=prepared.values.keys;merge=false}catch(_:Exception){message="Couldn't read this backup. Choose a valid .spockdata file."}finally{busy=false}
    }}}
    StudioCard(Modifier.fillMaxWidth()){
        Text("Your Spock data",style=MaterialTheme.typography.headlineSmall)
        Text("Favorites, library, listening history and preferences. Music files and account logins are not included.",color=TextMuted)
        Button(onClick={export.launch("Spock-${java.time.LocalDate.now()}.spockdata")},enabled=!busy){Icon(Icons.Default.FileUpload,null);Text(" Export .spockdata")}
        OutlinedButton(onClick={import.launch(arrayOf("application/octet-stream","application/json","*/*"))},enabled=!busy){Icon(Icons.Default.FileDownload,null);Text(" Import .spockdata")}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(message.isNotBlank())Text(message,color=TextMuted)
    }
    StudioCard(Modifier.fillMaxWidth()){
        Text("Automatic backups",style=MaterialTheme.typography.titleLarge,color=Color.White)
        Text(if(backupOptions.folder.isBlank())"Choose a folder to enable scheduled .spockdata backups." else "Saved folder: ${android.net.Uri.parse(backupOptions.folder).lastPathSegment.orEmpty()}",color=TextMuted)
        OutlinedButton(onClick={chooseFolder.launch(null)}){Text(if(backupOptions.folder.isBlank())"Choose backup folder" else "Change folder")}
        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Switch(backupOptions.automatic,{value->io.github.unidentified24.spock.features.FeatureSettings.update{it.copy(backups=it.backups.copy(automatic=value))};io.github.unidentified24.spock.data.backup.AutomaticBackup.schedule(context)});Text("Create backups automatically",color=Color.White)}
        Text("Every ${backupOptions.intervalDays} day(s) · keep ${backupOptions.historyCount} backups",color=TextMuted)
        Slider(backupOptions.intervalDays.toFloat(),{value->io.github.unidentified24.spock.features.FeatureSettings.update{it.copy(backups=it.backups.copy(intervalDays=value.toInt()))};io.github.unidentified24.spock.data.backup.AutomaticBackup.schedule(context)},valueRange=1f..30f,steps=28)
        val status=context.getSharedPreferences("auto_backup_status",0)
        Text(status.getString("error","").takeIf{!it.isNullOrBlank()} ?: status.getLong("last",0L).takeIf{it>0}?.let{"Last backup: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it))}"} ?: "No automatic backup yet",color=TextMuted,style=MaterialTheme.typography.bodySmall)
    }
    pending?.let{backup->AlertDialog(onDismissRequest={pending=null},title={Text("Review backup")},text={Column(Modifier.heightIn(max=520.dp)){
        Text("${backup.items} saved items across ${backup.values.size} sections. Select what to restore.",color=TextMuted)
        backup.values.toSortedMap().forEach{(section,values)->Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Checkbox(section in selectedSections,{checked->selectedSections=if(checked)selectedSections+section else selectedSections-section});Text("${section.replace('_',' ')} · ${values.size}",color=Color.White)}}
        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Switch(merge,{merge=it});Text("Merge into this device",color=Color.White)}
        Text(if(merge)"Existing keys stay; backup values update matching keys. Listening totals are combined." else "Selected sections replace the matching data on this device.",color=TextMuted,style=MaterialTheme.typography.bodySmall)
        Text("Music files and account logins are never imported.",color=TextMuted,style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(enabled=selectedSections.isNotEmpty(),onClick={pending=null;busy=true;scope.launch{
        try{
            SpockDataBackup.restore(context,backup,selectedSections,merge)
            val prefs=context.getSharedPreferences("appearance",0)
            currentLook=SpockLook.entries.firstOrNull{it.name==prefs.getString("theme",null)} ?: SpockLook.EMBER
            currentLayout=SpockLayout.entries.firstOrNull{it.name==prefs.getString("layout",null)} ?: SpockLayout.STUDIO
            message="Backup restored"
        }catch(_:Exception){message="Couldn't restore the backup. Your previous data was kept."}finally{busy=false}
    }}){Text(if(merge)"Merge selected" else "Restore selected")}},dismissButton={TextButton(onClick={pending=null}){Text("Cancel")}})}
}
