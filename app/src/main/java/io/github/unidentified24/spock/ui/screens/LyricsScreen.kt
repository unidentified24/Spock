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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import io.github.unidentified24.spock.data.api.Response
import io.github.unidentified24.spock.data.entity.*
import io.github.unidentified24.spock.data.preferences.*
import io.github.unidentified24.spock.di.SongPlayer
import io.github.unidentified24.spock.features.ShareFiles
import io.github.unidentified24.spock.ui.components.*
import io.github.unidentified24.spock.ui.navigation.*
import io.github.unidentified24.spock.ui.theme.*
import io.github.unidentified24.spock.ui.viewmodel.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

@Composable fun LyricsScreen(title:String,artist:String,album:String,accentColor:Color,onClose:()->Unit) {
    val vm:LyricsViewModel=hiltViewModel();val state by vm.state.collectAsState();val context=LocalContext.current
    var position by remember(title,artist){mutableStateOf(0L)}
    var savedSearch by remember{mutableStateOf(false)};var savedQuery by remember{mutableStateOf("")}
    var savedResults by remember{mutableStateOf(emptyList<Pair<String,Lyrics>>())}
    val shareScope=rememberCoroutineScope()
    val lyricOptions=io.github.unidentified24.spock.features.FeatureSettings.state.collectAsState().value.lyrics
    val language=getLyricTranslateLang(context).ifBlank{java.util.Locale.getDefault().language}
    LaunchedEffect(title,artist){vm.load(title,artist,album,(SongPlayer.getDuration()/1000).toInt())}
    LaunchedEffect(title,artist){while(true){position=SongPlayer.getCurrentPosition();delay(350)}}
    LaunchedEffect(savedSearch,savedQuery){if(savedSearch)savedResults=withContext(Dispatchers.IO){io.github.unidentified24.spock.data.api.LyricsApi.searchSaved(savedQuery)}}
    val listState=rememberLazyListState()
    val loaded=state as? LyricsViewModel.State.Loaded
    val lyricPosition=position-lyricOptions.offsetMs
    val active=loaded?.lyrics?.takeIf{it.synced}?.lines?.indexOfLast{it.timeMs<=lyricPosition+200} ?: -1
    LaunchedEffect(active){if(active>=0)listState.animateScrollToItem(active,scrollOffset=-100)}
    Column(Modifier.fillMaxSize().background(AppBackground).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            StudioIcon(Icons.Default.KeyboardArrowDown,"Close lyrics",onClick=onClose)
            Column(Modifier.weight(1f)) {Text("THE WORDS",color=AppPalette,style=MaterialTheme.typography.labelSmall);Text(title,color=Color.White,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)}
            StudioIcon(Icons.Default.Search,"Search saved lyrics",onClick={savedSearch=true})
            StudioIcon(Icons.Default.Remove,"Lyrics earlier",onClick={io.github.unidentified24.spock.features.FeatureSettings.update{it.copy(lyrics=it.lyrics.copy(offsetMs=it.lyrics.offsetMs-250))}})
            StudioIcon(Icons.Default.Add,"Lyrics later",onClick={io.github.unidentified24.spock.features.FeatureSettings.update{it.copy(lyrics=it.lyrics.copy(offsetMs=it.lyrics.offsetMs+250))}})
            if(loaded!=null)TextButton(onClick={vm.toggleTranslation(language)}){Text(if(loaded.translating)"Translating…" else if(loaded.showTranslation)"Original" else "Translate")}
            if(loaded!=null)StudioIcon(Icons.Default.Share,"Share lyric card",onClick={shareScope.launch{runCatching{
                val lines=loaded.lyrics.lines.map{it.text}.filter{it.isNotBlank()}.take(5)
                val file=withContext(Dispatchers.IO){ShareFiles.card(context,"Lyrics",title,lines,AppPalette.toArgb(),AppBackground.toArgb())};ShareFiles.share(context,file,"image/png")
            }.onFailure{android.widget.Toast.makeText(context,"Couldn't create a lyrics card",android.widget.Toast.LENGTH_SHORT).show()}}})
        }
        when(val data=state) {
            is LyricsViewModel.State.Loading -> Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center){CircularProgressIndicator()}
            is LyricsViewModel.State.NotFound -> StudioEmpty("No words just yet","Lyrics aren't available for this recording.")
            is LyricsViewModel.State.Loaded -> {
                Text(if(data.lyrics.synced)"Tap a line to jump there" else "Lyrics for this recording",color=TextMuted,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(horizontal=24.dp,vertical=8.dp))
                LazyColumn(Modifier.fillMaxWidth().weight(1f),state=listState,contentPadding=PaddingValues(start=20.dp,end=20.dp,top=20.dp,bottom=160.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(data.lyrics.lines) {i,line->
                        Column(Modifier.fillMaxWidth().clip(StudioShape).background(if(i==active)GridBackground else Color.Transparent)
                            .clickable(enabled=data.lyrics.synced){SongPlayer.seekTo(line.timeMs);SongPlayer.play()}.padding(18.dp)) {
                            val lyricStyle=if(lyricOptions.layout=="COMPACT")MaterialTheme.typography.bodyLarge else MaterialTheme.typography.headlineSmall
                            Text(line.text.ifBlank{"♪"},color=if(i==active||!data.lyrics.synced)Color.White else TextMuted.copy(alpha=.65f),
                                style=lyricStyle.copy(fontSize=lyricStyle.fontSize*lyricOptions.textScale),textAlign=if(lyricOptions.layout=="LEFT")TextAlign.Start else TextAlign.Center,fontWeight=if(i==active)FontWeight.Bold else FontWeight.Medium)
                            if(data.showTranslation)data.translations?.getOrNull(i)?.takeIf{it.isNotBlank()}?.let{translated->
                                if(lyricOptions.bilingual)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                                    Text(line.text,Modifier.weight(1f),color=Color.White,style=MaterialTheme.typography.bodyMedium)
                                    Text(translated,Modifier.weight(1f),color=AppPalette,style=MaterialTheme.typography.bodyMedium)
                                }else Text(translated,color=AppPalette,modifier=Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
    if(savedSearch){
        AlertDialog(onDismissRequest={savedSearch=false},title={Text("Saved lyrics")},
            text={Column(Modifier.fillMaxWidth().heightIn(max=440.dp)){
                OutlinedTextField(savedQuery,{savedQuery=it},singleLine=true,label={Text("Search words, song or artist")},modifier=Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f,false)){
                    items(savedResults.size){index->val (key,lyrics)=savedResults[index];val parts=key.split('|',limit=2)
                        TextButton(onClick={vm.loadSaved(key,lyrics);savedSearch=false}){Column(Modifier.fillMaxWidth()){
                            Text(parts.getOrElse(0){key},color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text(parts.getOrElse(1){""},color=TextMuted,style=MaterialTheme.typography.bodySmall)
                        }}
                    }
                }
                if(savedResults.isEmpty())Text("No saved lyrics match that search.",color=TextMuted,modifier=Modifier.padding(12.dp))
            }},confirmButton={TextButton(onClick={savedSearch=false}){Text("Done")}})
    }
}
