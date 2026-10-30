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

@Composable
fun DownloadsScreen(navController:NavController) {
    val context=LocalContext.current;var songs by remember {mutableStateOf<List<SongsModel>>(emptyList())}
    var pending by remember {mutableIntStateOf(0)}
    LaunchedEffect(Unit) {while(true){withContext(Dispatchers.IO){getDownloadedSongs(context) to io.github.unidentified24.spock.di.Downloads.downloadingSnapshot().size}.let {songs=it.first;pending=it.second};delay(1500)}}
    StudioCollection(navController,"Always with you.",if(pending>0) "$pending downloads in progress" else "Downloaded for offline listening",songs.firstOrNull()?.coverUri.orEmpty(),songs)
}
