package io.github.unidentified24.spock.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.unidentified24.spock.data.backup.SpockDataBackup
import io.github.unidentified24.spock.ui.components.*
import io.github.unidentified24.spock.ui.theme.*
import kotlinx.coroutines.launch

@Composable fun BackupSettings(){
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var busy by remember{mutableStateOf(false)};var message by remember{mutableStateOf("")}
    var pending by remember{mutableStateOf<SpockDataBackup.Prepared?>(null)}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->if(uri!=null){busy=true;scope.launch{
        try{SpockDataBackup.export(context,uri);message="Backup exported"}catch(_:Exception){message="Couldn't export backup. Please try another location."}finally{busy=false}
    }}}
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){busy=true;scope.launch{
        try{pending=SpockDataBackup.prepare(context,uri)}catch(_:Exception){message="Couldn't read this backup. Choose a valid .spockdata file."}finally{busy=false}
    }}}
    StudioCard(Modifier.fillMaxWidth()){
        Text("Your Spock data",style=MaterialTheme.typography.headlineSmall)
        Text("Favorites, library, listening history and preferences. Music files and account logins are not included.",color=TextMuted)
        Button(onClick={export.launch("Spock-${java.time.LocalDate.now()}.spockdata")},enabled=!busy){Icon(Icons.Default.FileUpload,null);Text(" Export .spockdata")}
        OutlinedButton(onClick={import.launch(arrayOf("application/octet-stream","application/json","*/*"))},enabled=!busy){Icon(Icons.Default.FileDownload,null);Text(" Import .spockdata")}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(message.isNotBlank())Text(message,color=TextMuted)
    }
    pending?.let{backup->AlertDialog(onDismissRequest={pending=null},title={Text("Restore backup?")},text={Text("This replaces the saved preferences, favorites, library and listening history in this backup. Your music files and logins stay on this device.")},confirmButton={TextButton(onClick={pending=null;busy=true;scope.launch{
        try{
            SpockDataBackup.restore(context,backup)
            val prefs=context.getSharedPreferences("appearance",0)
            currentLook=SpockLook.entries.firstOrNull{it.name==prefs.getString("theme",null)} ?: SpockLook.EMBER
            currentLayout=SpockLayout.entries.firstOrNull{it.name==prefs.getString("layout",null)} ?: SpockLayout.STUDIO
            message="Backup restored"
        }catch(_:Exception){message="Couldn't restore the backup. Your previous data was kept."}finally{busy=false}
    }}){Text("Restore")}},dismissButton={TextButton(onClick={pending=null}){Text("Cancel")}})}
}
