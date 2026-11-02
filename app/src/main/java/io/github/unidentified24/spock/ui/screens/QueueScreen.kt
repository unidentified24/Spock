package io.github.unidentified24.spock.ui.screens
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
import kotlinx.coroutines.delay


@OptIn(ExperimentalLayoutApi::class)
@Composable fun QueueScreen(navController:NavController) {
    val vm:PlayerViewModel=hiltViewModel();val queue=vm.queue.value;val context=LocalContext.current
    val shelf by io.github.unidentified24.spock.features.library.QueueArchive.state.collectAsState()
    val saveError by io.github.unidentified24.spock.features.library.QueueArchive.saveError.collectAsState()
    val live by rememberUpdatedState(queue)
    var query by remember{mutableStateOf("")};var selecting by remember{mutableStateOf(false)}
    var selected by remember{mutableStateOf<Set<Int>>(emptySet())};var undo by remember{mutableStateOf<List<SongsModel>?>(null)}
    var saving by remember{mutableStateOf<List<SongsModel>?>(null)};var restore by remember{mutableStateOf(false)}
    var dragging by remember{mutableStateOf<Int?>(null)}
    val current=vm.currentSongId.value;val index=queue.indexOfFirst{it.id==current}
    val visible=queue.filter{query.isBlank()||it.title.contains(query,true)||it.singer.contains(query,true)}
    fun change(next:List<SongsModel>){undo=queue;vm.updateQueue(next)}
    fun remove(ids:Set<Int>){change(io.github.unidentified24.spock.features.library.QueueTools.retainCurrent(queue,ids,current));selected=emptySet()}
    fun lane(song:SongsModel)=when{song.id==current->"Now playing";index>=0&&queue.indexOfFirst{it.id==song.id}<index->"Earlier";song.id in shelf.nextIds->"Play next";else->"Play later"}
    saving?.let{AddTracksDialog(it){saving=null}}
    LazyColumn(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding(),contentPadding=PaddingValues(bottom=40.dp)) {
        item{StudioHeader(navController,"Your next chapter.",back=true)}
        if(saveError.isNotBlank())item{Text(saveError,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(20.dp))}
        item{Column(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            OutlinedTextField(query,{query=it},singleLine=true,label={Text("Search queue")},leadingIcon={Icon(Icons.Default.Search,null)},modifier=Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                StudioChip(if(selecting)"Done" else "Select",selecting){selecting=!selecting;selected=emptySet()}
                StudioChip("Save playlist"){saving=queue}
                Box{StudioChip("Previous queues"){restore=true};DropdownMenu(restore,{restore=false}){
                    if(shelf.current.isNotEmpty()&&queue.isEmpty())DropdownMenuItem(text={Text("Last queue · ${shelf.current.size} tracks")},onClick={change(shelf.current);restore=false})
                    shelf.recent.forEach{snapshot->DropdownMenuItem(text={Text("${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(snapshot.time))} · ${snapshot.songs.size} tracks")},onClick={change(snapshot.songs);restore=false})}
                    if(shelf.recent.isEmpty()&&shelf.current.isEmpty())DropdownMenuItem(text={Text("No saved queues yet")},enabled=false,onClick={})
                }}
                if(undo!=null)StudioChip("Undo"){val previous=undo!!;undo=null;vm.updateQueue(previous)}
                StudioChip("Clear after current"){change(io.github.unidentified24.spock.features.library.QueueTools.clearAfter(queue,current))}
            }
            if(selecting){
                Text("${selected.size} selected",color=AppPalette)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    TextButton(onClick={selected=visible.filter{it.id!=current}.map{it.id}.toSet()}){Text("Select all shown")}
                    TextButton(enabled=selected.isNotEmpty(),onClick={saving=queue.filter{it.id in selected}}){Text("Save selected")}
                    TextButton(enabled=selected.isNotEmpty(),onClick={remove(selected)}){Text("Remove selected")}
                    TextButton(enabled=selected.isNotEmpty(),onClick={
                        val picked=queue.filter{it.id in selected};val prefix=queue.take((index+1).coerceAtLeast(0)).filter{it.id !in selected}
                        change(prefix+picked+queue.drop((index+1).coerceAtLeast(0)).filter{it.id !in selected})
                        picked.forEach{io.github.unidentified24.spock.features.library.QueueArchive.next(it.id,true)};selected=emptySet()
                    }){Text("Play selected next")}
                }
            }
        }}
        itemsIndexed(visible,key={_,song->song.id}){position,song->
            if(position==0||lane(visible[position-1])!=lane(song))StudioSection(lane(song))
            var rowHeight by remember(song.id){mutableFloatStateOf(140f)}
            Column(Modifier.onSizeChanged{rowHeight=it.height.toFloat()}.background(if(dragging==song.id)AppPalette.copy(alpha=.1f)else AppBackground)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    if(selecting)Checkbox(song.id in selected,{checked->selected=if(checked)selected+song.id else selected-song.id},enabled=song.id!=current)
                    Box(Modifier.weight(1f)){StudioTrack(song,queue.indexOfFirst{it.id==song.id},song.id==current,{
                        if(selecting&&song.id!=current)selected=if(song.id in selected)selected-song.id else selected+song.id
                        else if(!selecting)vm.playSongAt(queue,queue.indexOfFirst{it.id==song.id},context)
                    },{selecting=true;if(song.id!=current)selected=selected+song.id})}
                }
                Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically){
                    if(query.isBlank())Box(Modifier.size(48.dp).pointerInput(song.id){var distance=0f
                        detectDragGesturesAfterLongPress(onDragStart={dragging=song.id;distance=0f},onDragEnd={dragging=null},onDragCancel={dragging=null}){change,delta->
                            change.consume();distance+=delta.y
                            if(kotlin.math.abs(distance)>rowHeight*.65f){val from=live.indexOfFirst{it.id==song.id};val to=from+if(distance>0)1 else -1
                                if(from>=0&&to in live.indices)vm.moveQueueItem(from,to);distance=0f}
                        }
                    },contentAlignment=Alignment.Center){Icon(Icons.Default.DragHandle,"Drag ${song.title}",tint=TextMuted)}
                    IconButton(enabled=queue.indexOfFirst{it.id==song.id}>0,onClick={val i=queue.indexOfFirst{it.id==song.id};vm.moveQueueItem(i,i-1)}){Icon(Icons.Default.KeyboardArrowUp,"Move earlier",tint=TextMuted)}
                    IconButton(enabled=queue.indexOfFirst{it.id==song.id}<queue.lastIndex,onClick={val i=queue.indexOfFirst{it.id==song.id};vm.moveQueueItem(i,i+1)}){Icon(Icons.Default.KeyboardArrowDown,"Move later",tint=TextMuted)}
                    IconButton(enabled=song.id!=current,onClick={remove(setOf(song.id))}){Icon(Icons.Default.Close,"Remove from queue",tint=TextMuted)}
                }
            }
        }
        if(visible.isEmpty())item{StudioEmpty(if(queue.isEmpty())"An open queue" else "No matching tracks",if(queue.isEmpty())"Choose music or restore a previous queue." else "Try a different title or artist.")}
    }
}
