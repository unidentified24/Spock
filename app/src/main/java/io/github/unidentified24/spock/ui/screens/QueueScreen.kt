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

@Composable fun QueueScreen(navController:NavController) {
    val vm:PlayerViewModel=hiltViewModel();val queue=vm.queue.value;val context=LocalContext.current
    LazyColumn(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding(),contentPadding=PaddingValues(bottom=40.dp)) {
        item{StudioHeader(navController,"In the pipeline.",back=true)}
        item{StudioSection("Your queue","Move tracks with the arrows, or remove them with ×.")}
        itemsIndexed(queue,key={i,s->"${s.id}:$i"}) {i,s->
            StudioTrack(s,i,s.id==vm.currentSongId.value,{vm.playSongAt(queue,i,context)},{})
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),horizontalArrangement=Arrangement.End) {
                IconButton(enabled=i>0,onClick={vm.moveQueueItem(i,i-1)}){Icon(Icons.Default.KeyboardArrowUp,"Move earlier",tint=TextMuted)}
                IconButton(enabled=i<queue.lastIndex,onClick={vm.moveQueueItem(i,i+1)}){Icon(Icons.Default.KeyboardArrowDown,"Move later",tint=TextMuted)}
                IconButton(onClick={vm.removeFromQueue(s)}){Icon(Icons.Default.Close,"Remove from queue",tint=AppPalette)}
            }
        }
        if(queue.isEmpty())item{StudioEmpty("An open queue","Choose a track from Discover or Collection to start listening.")}
    }
}
