package io.github.unidentified24.spock.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import io.github.unidentified24.spock.features.library.*
import kotlinx.coroutines.*

@Composable fun CollectionAppearance(playlist:ManagedPlaylist){
    val context=LocalContext.current.applicationContext;val scope=rememberCoroutineScope();val shelf by LibraryStore.state.collectAsState()
    var creatingFolder by remember{mutableStateOf(false)};var folder by remember{mutableStateOf("")}
    var message by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)}
    val image=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch{
        busy=true
        try{
            val result=withContext(Dispatchers.IO){
                val bytes=context.contentResolver.openInputStream(uri)?.use{io.github.unidentified24.spock.features.BoundedInput.read(it,12*1024*1024)} ?: error("No image")
                require(bytes.size<=12*1024*1024)
                val bounds=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true};android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                require(bounds.outWidth>0&&bounds.outHeight>0&&bounds.outWidth.toLong()*bounds.outHeight<=100_000_000)
                val options=android.graphics.BitmapFactory.Options().apply{inSampleSize=(maxOf(bounds.outWidth,bounds.outHeight)/1024).coerceAtLeast(1)}
                val bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,options) ?: error("Invalid image")
                val target=java.io.File(context.filesDir,"playlist_covers").apply{mkdirs()}.resolve(java.util.UUID.randomUUID().toString()+".png")
                try{target.outputStream().use{check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}}finally{bitmap.recycle()}
                android.net.Uri.fromFile(target).toString()
            }
            LibraryStore.update(playlist.id){it.copy(cover=result)};message="Cover updated"
        }catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){message="Choose an image smaller than 12 MB."}finally{busy=false}
    }}
    PreferenceSwitch("Pin playlist",playlist.pinned){value->LibraryStore.update(playlist.id){it.copy(pinned=value)}}
    PreferenceChoice("Folder",playlist.folder,listOf("" to "No folder")+shelf.folders.map{it to it}+listOf("__new__" to "New folder")){value->
        if(value=="__new__"){folder="";creatingFolder=true}else LibraryStore.update(playlist.id){it.copy(folder=value)}
    }
    TextButton(enabled=!busy,onClick={image.launch(arrayOf("image/*"))}){Text(if(busy)"Saving cover…" else "Choose a cover")}
    if(message.isNotBlank())Text(message,style=MaterialTheme.typography.bodySmall)
    if(creatingFolder)AlertDialog(onDismissRequest={creatingFolder=false},title={Text("New playlist folder")},text={OutlinedTextField(folder,{folder=it.take(100)},singleLine=true,label={Text("Folder name")})},confirmButton={TextButton(enabled=folder.isNotBlank()&&folder.trim()!="__new__",onClick={val value=folder.trim();LibraryStore.folder(value);LibraryStore.update(playlist.id){it.copy(folder=value)};creatingFolder=false}){Text("Create")}},dismissButton={TextButton(onClick={creatingFolder=false}){Text("Cancel")}})
}
