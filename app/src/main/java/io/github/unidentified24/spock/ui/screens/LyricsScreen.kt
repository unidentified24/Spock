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
import kotlinx.coroutines.delay

@Composable fun LyricsScreen(title:String,artist:String,album:String,accentColor:Color,onClose:()->Unit) {
    val vm:LyricsViewModel=hiltViewModel();val state by vm.state.collectAsState();val context=LocalContext.current
    var position by remember(title,artist){mutableStateOf(0L)}
    val language=getLyricTranslateLang(context).ifBlank{java.util.Locale.getDefault().language}
    LaunchedEffect(title,artist){vm.load(title,artist,album,(SongPlayer.getDuration()/1000).toInt())}
    LaunchedEffect(title,artist){while(true){position=SongPlayer.getCurrentPosition();delay(350)}}
    val listState=rememberLazyListState()
    val loaded=state as? LyricsViewModel.State.Loaded
    val active=loaded?.lyrics?.takeIf{it.synced}?.lines?.indexOfLast{it.timeMs<=position+200} ?: -1
    LaunchedEffect(active){if(active>=0)listState.animateScrollToItem(active,scrollOffset=-100)}
    Column(Modifier.fillMaxSize().background(AppBackground).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            StudioIcon(Icons.Default.KeyboardArrowDown,"Close lyrics",onClick=onClose)
            Column(Modifier.weight(1f)) {Text("THE WORDS",color=AppPalette,style=MaterialTheme.typography.labelSmall);Text(title,color=Color.White,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)}
            if(loaded!=null)TextButton(onClick={vm.toggleTranslation(language)}){Text(if(loaded.translating)"Translating…" else if(loaded.showTranslation)"Original" else "Translate")}
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
                            Text(line.text.ifBlank{"♪"},color=if(i==active||!data.lyrics.synced)Color.White else TextMuted.copy(alpha=.65f),
                                style=MaterialTheme.typography.headlineSmall,fontWeight=if(i==active)FontWeight.Bold else FontWeight.Medium)
                            if(data.showTranslation)data.translations?.getOrNull(i)?.takeIf{it.isNotBlank()}?.let{Text(it,color=AppPalette,modifier=Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodyMedium)}
                        }
                    }
                }
            }
        }
    }
}
