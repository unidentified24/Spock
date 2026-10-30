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

@Composable fun HistoryScreen(navController:NavController) {
    val player:PlayerViewModel=hiltViewModel()
    val context=LocalContext.current;var history by remember {mutableStateOf(getListeningHistory(context))}
    LazyColumn(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding(),contentPadding=PaddingValues(bottom=32.dp)) {
        item{StudioHeader(navController,"The recent rotation.",back=true)}
        item{Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            Text("${history.size} recent plays",color=TextMuted)
            TextButton(onClick={clearListeningHistory(context);history=emptyList()}){Text("Clear history")}
        }}
        items(history) {entry->
            StudioMediaRow(entry.title,entry.singer,entry.image){
                val song=SongsModel(entry.songId,entry.title,entry.album,entry.singer,entry.image,
                    SongPlayer.buildSpotifyPlayQuery("",entry.title,entry.singer),durationMs=entry.durationMs)
                playStudioTrack(player,listOf(song),0,context)
            }
        }
        if(history.isEmpty())item{StudioEmpty("A fresh start","Your listening history will appear as you play music.")}
    }
}
