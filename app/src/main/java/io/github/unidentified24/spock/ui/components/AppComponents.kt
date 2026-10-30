package io.github.unidentified24.spock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.*
import io.github.unidentified24.spock.ui.theme.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import io.github.unidentified24.spock.R
import io.github.unidentified24.spock.data.preferences.addLikedSongId
import io.github.unidentified24.spock.data.preferences.isSongLiked
import io.github.unidentified24.spock.data.preferences.removeLikedSongId
import io.github.unidentified24.spock.di.SongPlayer
import io.github.unidentified24.spock.ui.navigation.Routes
import io.github.unidentified24.spock.ui.theme.AppBackground
import io.github.unidentified24.spock.ui.theme.GridBackground
import io.github.unidentified24.spock.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay

@Composable
fun Loader() {
    Column(Modifier
        .background(Color(AppBackground.toArgb())),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(45.dp),
            color = io.github.unidentified24.spock.ui.theme.AppPalette
        )
    }

}

@Composable
fun MiniPlayer(navController:NavHostController) {
    val vm:PlayerViewModel=hiltViewModel();val playing=vm.currentSongPlayingState.value
    val context=LocalContext.current
    Column(Modifier.padding(horizontal=20.dp,vertical=6.dp).fillMaxWidth().clip(StudioShape).background(GridBackground)) {
        Row(Modifier.fillMaxWidth().clickable{navController.navigate(Routes.Player.route)}.padding(10.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            StudioCover(vm.currentSongCoverUri.value,Modifier.size(44.dp))
            Column(Modifier.weight(1f)) {
                Text(vm.currentSongTitle.value,color=Color.White,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(vm.currentSongSinger.value,color=TextMuted,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            IconButton(onClick={
                if(playing)SongPlayer.pause() else SongPlayer.play()
                vm.updateSongState(vm.currentSongCoverUri.value,vm.currentSongTitle.value,vm.currentSongSinger.value,!playing,vm.currentSongId.value,vm.currentSongIndex.value,vm.currentSongAlbum.value)
            }) {Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,if(playing)"Pause" else "Play",tint=AppPalette)}
            IconButton(onClick={vm.playNextSongs(vm.queue.value,context)}){Icon(Icons.Default.SkipNext,"Next",tint=Color.White)}
        }
        MiniProgress(vm.currentSongId.value,playing)
    }
}
@Composable private fun MiniProgress(id:Int,playing:Boolean) {
    var progress by remember(id){mutableFloatStateOf(0f)}
    LaunchedEffect(id,playing){while(true){val duration=SongPlayer.getDuration();progress=if(duration>0)(SongPlayer.getCurrentPosition().toFloat()/duration).coerceIn(0f,1f)else 0f;delay(if(playing)1000 else 2000)}}
    LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth().height(2.dp),color=AppPalette,trackColor=SurfaceVariant)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSlider(
    modifier: Modifier = Modifier,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    colors: SliderColors = SliderDefaults.colors(),
) {
    Box(
        //contentAlignment = Alignment.BottomEnd,
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)

    ) {

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = colors,
            thumb = {
                SliderDefaults.Thumb( //androidx.compose.material3.SliderDefaults
                    interactionSource = remember { MutableInteractionSource() },
                    modifier = Modifier.align(Alignment.Center),
                    colors = colors,
                    thumbSize = DpSize(4.dp, 4.dp)
                )
            }
        )
    }
}

@Composable
fun Snackbar(showMessage : String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center
    ){
        Text(
            fontWeight = FontWeight.W500,
            fontSize = 14.sp,
            color = Color.Black,
            text = showMessage
        )
    }
}

/**
 * Asks before downloading a whole list: the download-all icon is small and shows
 * up only once the list has loaded, so a stray tap used to start dozens of downloads.
 */
@Composable
fun ConfirmDownloadAllDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("Download $count tracks?") },
        text = { androidx.compose.material3.Text("They are saved for offline playback. You can cancel from Downloads.") },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onDismiss(); onConfirm() }) {
                androidx.compose.material3.Text("Download")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text("Cancel") }
        },
    )
}
