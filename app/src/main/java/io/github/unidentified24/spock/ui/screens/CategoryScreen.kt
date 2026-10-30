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
fun CategoryScreen(navController:NavController,genre:String,title:String) {
    val vm:CategoryViewModel=hiltViewModel();val state by vm.playlists.collectAsState()
    LaunchedEffect(genre){vm.load(genre)}
    LazyColumn(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding(),contentPadding=PaddingValues(bottom=32.dp)) {
        item{StudioHeader(navController,title,back=true)}
        if(state is Response.Loading) item{Box(Modifier.fillMaxWidth().padding(30.dp),contentAlignment=Alignment.Center){CircularProgressIndicator()}}
        items((state as? Response.Success)?.data.orEmpty()) {e->StudioMediaRow(e.name,e.subtitle,e.coverUri){navController.navigate(playlistRoute(e.spotifyId,e.name))}}
        if(state is Response.Error) item{StudioEmpty("This shelf couldn't load","Please try again when your connection is available.")}
    }
}
