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

@Composable fun MusicSourceScreen(navController:NavController) {
    val context=LocalContext.current
    fun home(){setPrimaryMusicSource(context,MusicSource.YOUTUBE_MUSIC);setYouTubeSourceEnabled(context,true);navController.navigate(Routes.Home.route){popUpTo(Routes.MusicSource.route){inclusive=true}}}
    Column(Modifier.fillMaxSize().background(AppBackground).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
        SpockMark(Modifier.size(76.dp))
        Text("More ways\nto press play.",color=Color.White,style=MaterialTheme.typography.displayMedium)
        Text("Spotify brings your collection. Connect the audio services you use; Spock plays the first one that's ready.",color=TextMuted,style=MaterialTheme.typography.bodyLarge)
        StudioAction("YouTube Music",if(isYoutubeLoggedIn(context))"Connected" else "Connect your account for more recordings",Icons.Default.PlayCircle){navController.navigate("${Routes.YouTubeLogin.route}?next=home")}
        StudioAction("Deezer","Connect your own account",Icons.Default.GraphicEq){navController.navigate("${Routes.DeezerLogin.route}?next=home")}
        StudioAction("FLAC","Optional experimental setup",Icons.Default.Album){navController.navigate("${Routes.SpotiflacVerify.route}?next=home")}
        Button(onClick={home()},modifier=Modifier.fillMaxWidth().height(56.dp),shape=RoundedCornerShape(16.dp)){Text("Start listening")}
        Text("You can manage every source later in Account → Settings. Some recordings need a connected account.",color=TextMuted,style=MaterialTheme.typography.bodySmall)
    }
}
