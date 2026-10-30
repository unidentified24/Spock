package io.github.unidentified24.spock.ui.screens
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.vectorResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.github.unidentified24.spock.R
import io.github.unidentified24.spock.data.api.Response
import io.github.unidentified24.spock.data.entity.SongsModel
import io.github.unidentified24.spock.data.preferences.addLikedSongId
import io.github.unidentified24.spock.data.preferences.alternativeStreamKey
import io.github.unidentified24.spock.data.preferences.clearAlternativeStream
import io.github.unidentified24.spock.data.preferences.getAlternativeStream
import io.github.unidentified24.spock.data.preferences.getLikedSongIds
import io.github.unidentified24.spock.data.preferences.getSongsByIds
import io.github.unidentified24.spock.data.preferences.isSongLiked
import io.github.unidentified24.spock.data.preferences.removeLikedSongId
import io.github.unidentified24.spock.data.preferences.setLocalAlternativeStream
import io.github.unidentified24.spock.data.preferences.setYouTubeAlternativeStream
import io.github.unidentified24.spock.di.SongPlayer
import io.github.unidentified24.spock.ui.components.Snackbar
import io.github.unidentified24.spock.ui.navigation.Routes
import io.github.unidentified24.spock.ui.navigation.albumRoute
import io.github.unidentified24.spock.ui.navigation.artistRoute
import io.github.unidentified24.spock.ui.theme.AppBackground
import io.github.unidentified24.spock.ui.theme.AppPalette
import io.github.unidentified24.spock.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.*
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.*
import io.github.unidentified24.spock.ui.components.*
import io.github.unidentified24.spock.ui.theme.*
@Composable
fun PlayerScreen(navController:NavController) {
    val vm:PlayerViewModel=hiltViewModel();val context=LocalContext.current
    val title=vm.currentSongTitle.value;val artist=vm.currentSongSinger.value;val id=vm.currentSongId.value
    val playing=vm.currentSongPlayingState.value;val songs=vm.queue.value
    var menu by remember {mutableStateOf(false)};var lyrics by remember {mutableStateOf(false)}
    val liked=remember(id) {mutableStateOf(isSongLiked(context,id.toString()))}
    if(menu) PlayerOptionsSheet(navController,vm,context,liked,onDismiss={menu=false})
    BackHandler(lyrics){lyrics=false}
    if(lyrics) {
        LyricsScreen(title,artist,vm.currentSongAlbum.value,AppPalette,onClose={lyrics=false})
        return
    }
    val current=songs.firstOrNull {it.id==id}
    Column(Modifier.fillMaxSize().background(AppBackground).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            StudioIcon(Icons.Default.KeyboardArrowDown,"Close player"){navController.navigateUp()}
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                SpockMark(Modifier.size(26.dp));Text("THE LISTENING ROOM",color=AppPalette,style=MaterialTheme.typography.labelSmall)
            }
            StudioIcon(Icons.Default.MoreHoriz,"Track options"){menu=true}
        }
        PlayerPresentation(title,vm.currentSongCoverUri.value,playing)
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(vm.currentSongAlbum.value.ifBlank{"NOW PLAYING"}.uppercase(),color=AppPalette,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(title.ifBlank{"Nothing playing yet"},color=Color.White,style=MaterialTheme.typography.headlineLarge,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(artist,color=TextMuted,style=MaterialTheme.typography.titleMedium,modifier=Modifier.clickable {
                vm.goToArtist(current?.spotifyTrackId.orEmpty(),artist){navController.navigate(it)}
            })
        }
        StudioPlayerTimeline(id,playing)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly) {
            StudioIcon(Icons.Default.Shuffle,"Shuffle",selected=vm.shuffleState.value){vm.updateShuffleState(!vm.shuffleState.value)}
            StudioIcon(Icons.Default.SkipPrevious,"Previous"){vm.playPreviousSong(songs,context)}
            FilledIconButton(onClick={
                if(playing) SongPlayer.pause() else SongPlayer.play()
                vm.updateSongState(vm.currentSongCoverUri.value,title,artist,!playing,id,vm.currentSongIndex.value,vm.currentSongAlbum.value)
            },modifier=Modifier.size(76.dp),shape=RoundedCornerShape(24.dp)) {
                Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,if(playing)"Pause" else "Play",modifier=Modifier.size(38.dp))
            }
            StudioIcon(Icons.Default.SkipNext,"Next"){vm.playNextSongs(songs,context)}
            StudioIcon(Icons.Default.Repeat,"Repeat",selected=vm.repeatState.value){vm.updateRepeatState(!vm.repeatState.value)}
        }
        StudioSourceStatus(id,navController,current?.url.orEmpty())
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick={lyrics=true},shape=RoundedCornerShape(14.dp),modifier=Modifier.weight(1f)){Icon(Icons.Default.Notes,null);Text(" Lyrics")}
            OutlinedButton(onClick={navController.navigate(Routes.Queue.route)},shape=RoundedCornerShape(14.dp),modifier=Modifier.weight(1f)){Icon(Icons.Default.QueueMusic,null);Text(" Up next")}
        }
        Text("Track options include save, download, sleep timer and alternative audio.",color=TextMuted,style=MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun StudioSourceStatus(id:Int,navController:NavController,query:String) {
    val context=LocalContext.current
    var failed by remember(id){mutableStateOf(false)}
    var label by remember(id){mutableStateOf("")}
    LaunchedEffect(id){while(true){failed=SongPlayer.playbackFailed;label=SongPlayer.playbackMessage.ifBlank {SongPlayer.currentSource+" · "+SongPlayer.currentQuality};delay(600)}}
    Row(Modifier.fillMaxWidth().clip(StudioShape).background(GridBackground).padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Default.GraphicEq,null,tint=AppPalette)
        Column(Modifier.weight(1f)) {
            Text(label,color=TextMuted,style=MaterialTheme.typography.bodySmall)
            if(failed) Row {
                TextButton(onClick={SongPlayer.invalidateResolvedStream(query);SongPlayer.playSong(query,context)}) { Text("Retry") }
                TextButton(onClick={navController.navigate(Routes.Connections.route)}) { Text("Sources") }
            }
        }
    }
}
@Composable private fun StudioPlayerTimeline(id:Int,playing:Boolean) {
    var position by remember(id){mutableStateOf(0L)};var duration by remember(id){mutableStateOf(0L)}
    var dragging by remember(id){mutableStateOf(false)};var value by remember(id){mutableStateOf(0f)}
    LaunchedEffect(id,playing){while(true){position=SongPlayer.getCurrentPosition().coerceAtLeast(0);duration=SongPlayer.getDuration().coerceAtLeast(0);delay(if(playing)500 else 1500)}}
    Column {
        Slider(value=if(dragging)value else if(duration>0)(position.toFloat()/duration).coerceIn(0f,1f) else 0f,
            onValueChange={dragging=true;value=it},onValueChangeFinished={SongPlayer.seekTo((value*duration).toLong());dragging=false},
            colors=SliderDefaults.colors(thumbColor=AppPalette,activeTrackColor=AppPalette,inactiveTrackColor=SurfaceVariant),modifier=Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("SEEK",color=TextMuted,style=MaterialTheme.typography.labelSmall)
            Text(if(duration>0) "${((if(dragging)value else position.toFloat()/duration)*100).toInt().coerceIn(0,100)}%" else "—",color=TextMuted,style=MaterialTheme.typography.labelSmall)
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun PlayerOptionsSheet(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    context: Context,
    isLiked: MutableState<Boolean>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSleep by remember { mutableStateOf(false) }
    var showSavedIn by remember { mutableStateOf(false) }
    var showAlternativeStream by remember { mutableStateOf(false) }
    val title = playerViewModel.currentSongTitle.value
    val singer = playerViewModel.currentSongSinger.value
    val cover = playerViewModel.currentSongCoverUri.value
    val album = playerViewModel.currentSongAlbum.value
    val songId = playerViewModel.currentSongId.value
    // The full track model (spotify id, real album, stream url) — the state above
    // only carries display strings, and `album` is the *context* name (playlist…).
    val currentSong = playerViewModel.queue.value.firstOrNull { it.id == songId }
    var downloaded by remember(songId) { mutableStateOf(io.github.unidentified24.spock.data.preferences.isDownloaded(context, songId.toString())) }
    var downloadingNow by remember(songId) { mutableStateOf(currentSong != null && io.github.unidentified24.spock.di.Downloads.isDownloading(currentSong.url)) }
    val alternativeKey = currentSong?.let { alternativeStreamKey(it) }.orEmpty()
    var currentAlternative by remember(songId, alternativeKey) {
        mutableStateOf(alternativeKey.takeIf { it.isNotBlank() }?.let { getAlternativeStream(context, it) })
    }
    val localFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val song = currentSong ?: return@rememberLauncherForActivityResult
        val picked = uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                picked,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        setLocalAlternativeStream(context, alternativeKey, picked, picked.lastPathSegment.orEmpty())
        SongPlayer.invalidateResolvedStream(song.url)
        currentAlternative = getAlternativeStream(context, alternativeKey)
        Toast.makeText(context, "Alternative stream set to local file", Toast.LENGTH_SHORT).show()
    }
    if (showSavedIn && currentSong != null) {
        io.github.unidentified24.spock.ui.components.SavedInSheet(
            song = currentSong,
            context = context,
            onDismiss = { showSavedIn = false; onDismiss() },
            onLikedChanged = { isLiked.value = it },
        )
        return
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = io.github.unidentified24.spock.ui.theme.GridBackground,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 12.dp)
        ) {
            if (showAlternativeStream) {
                AlternativeStreamEditor(
                    currentAlternative = currentAlternative,
                    enabled = currentSong != null,
                    onBack = { showAlternativeStream = false },
                    onUseYouTube = { text ->
                        val song = currentSong ?: return@AlternativeStreamEditor
                        val videoId = SongPlayer.videoIdFromYouTubeLink(text)
                        if (videoId == null) {
                            Toast.makeText(context, "Paste a YouTube video link or video ID", Toast.LENGTH_SHORT).show()
                        } else {
                            setYouTubeAlternativeStream(context, alternativeKey, videoId)
                            SongPlayer.invalidateResolvedStream(song.url)
                            currentAlternative = getAlternativeStream(context, alternativeKey)
                            Toast.makeText(context, "Alternative stream set to YouTube", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onPickLocal = {
                        localFileLauncher.launch(arrayOf("audio/*"))
                    },
                    onClear = {
                        val song = currentSong ?: return@AlternativeStreamEditor
                        clearAlternativeStream(context, alternativeKey)
                        SongPlayer.invalidateResolvedStream(song.url)
                        currentAlternative = null
                        Toast.makeText(context, "Alternative stream cleared", Toast.LENGTH_SHORT).show()
                    },
                )
            } else if (!showSleep) {
                // ── Now-playing header ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    GlideImage(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        model = cover,
                        contentScale = ContentScale.Crop,
                        contentDescription = ""
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(singer, color = Color.Gray, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
                androidx.compose.material3.HorizontalDivider(color = io.github.unidentified24.spock.ui.theme.SurfaceVariant)
                PlayerMenuRow(
                    icon = Icons.Default.Share,
                    label = "Share"
                ) {
                    // Share the real Spotify track link when we know the id.
                    val shareText = currentSong?.spotifyTrackId?.takeIf { it.isNotBlank() }
                        ?.let { "https://open.spotify.com/track/$it" }
                        ?: "Listening to $title by $singer"
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(send, "Share"))
                    onDismiss()
                }
                PlayerMenuRow(
                    icon = if (downloaded) Icons.Default.CheckCircle else ImageVector.vectorResource(R.drawable.ic_download),
                    iconTint = if (downloaded) Color(AppPalette.toArgb()) else Color.White,
                    label = when {
                        downloaded -> "Downloaded — remove"
                        downloadingNow -> "Downloading…"
                        else -> "Download"
                    },
                    enabled = currentSong != null && !downloadingNow,
                ) {
                    val song = currentSong ?: return@PlayerMenuRow
                    if (downloaded) {
                        io.github.unidentified24.spock.data.preferences.removeDownload(context, song.id.toString())
                        downloaded = false
                    } else {
                        downloadingNow = true
                        io.github.unidentified24.spock.di.Downloads.downloadSong(song, context) { ok ->
                            downloadingNow = false
                            downloaded = ok
                        }
                    }
                }
                PlayerMenuRow(
                    icon = Icons.Default.PlayArrow,
                    iconTint = if (currentAlternative != null) Color(AppPalette.toArgb()) else Color.White,
                    label = if (currentAlternative == null) "Alternative stream" else "Alternative stream set",
                    enabled = currentSong != null,
                    trailingArrow = true,
                ) {
                    showAlternativeStream = true
                }
                PlayerMenuRow(
                    icon = if (isLiked.value) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    iconTint = if (isLiked.value) Color(AppPalette.toArgb()) else Color.White,
                    label = if (isLiked.value) "Remove from Liked Songs" else "Add to Liked Songs"
                ) {
                    if (isLiked.value) {
                        removeLikedSongId(context, songId.toString())
                    } else {
                        addLikedSongId(context, songId.toString())
                    }
                    isLiked.value = isSongLiked(context, songId.toString())
                    // Mirror the like to the real Spotify account.
                    io.github.unidentified24.spock.data.api.SpotifySync.setTrackSaved(
                        context, currentSong?.spotifyTrackId.orEmpty(), isLiked.value)
                    onDismiss()
                }
                PlayerMenuRow(
                    icon = Icons.Default.Add,
                    label = "Add to playlist",
                    enabled = currentSong != null,
                    trailingArrow = true,
                ) {
                    showSavedIn = true
                }
                PlayerMenuRow(
                    icon = Icons.AutoMirrored.Filled.List,
                    label = "View queue"
                ) {
                    onDismiss()
                    navController.navigate(Routes.Queue.route)
                }
                // Use the track's REAL album (currentSongAlbum is the playing
                // context — a playlist name would resolve to garbage).
                val realAlbum = currentSong?.album?.ifBlank { null } ?: album
                PlayerMenuRow(
                    icon = Icons.Default.PlayArrow,
                    label = "Go to album",
                    enabled = realAlbum.isNotBlank()
                ) {
                    onDismiss()
                    navController.navigate(albumRoute(realAlbum, singer))
                }
                PlayerMenuRow(
                    icon = Icons.Default.Person,
                    label = "Go to artist",
                    enabled = singer.isNotBlank()
                ) {
                    onDismiss()
                    playerViewModel.goToArtist(currentSong?.spotifyTrackId.orEmpty(), singer) { route ->
                        navController.navigate(route)
                    }
                }
                PlayerMenuRow(
                    icon = Icons.Default.Notifications,
                    label = "Sleep timer",
                    trailingArrow = true
                ) { showSleep = true }
            } else {
                Text(
                    "Sleep timer",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
                androidx.compose.material3.HorizontalDivider(color = io.github.unidentified24.spock.ui.theme.SurfaceVariant)
                val options = listOf(
                    "Off" to 0L,
                    "5 minutes" to 5L,
                    "15 minutes" to 15L,
                    "30 minutes" to 30L,
                    "45 minutes" to 45L,
                    "1 hour" to 60L
                )
                options.forEach { (label, minutes) ->
                    PlayerMenuRow(icon = Icons.Default.Notifications, label = label) {
                        SongPlayer.setSleepTimer(minutes * 60_000L)
                        onDismiss()
                    }
                }
            }
        }
    }
}
@Composable
fun AlternativeStreamEditor(
    currentAlternative: io.github.unidentified24.spock.data.preferences.AlternativeStream?,
    enabled: Boolean,
    onBack: () -> Unit,
    onUseYouTube: (String) -> Unit,
    onPickLocal: () -> Unit,
    onClear: () -> Unit,
) {
    var youtubeText by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onBack() },
                contentDescription = null,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Alternative stream",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = when {
                currentAlternative == null -> "No alternative stream set"
                currentAlternative.isYouTube -> "Current: YouTube video ${currentAlternative.value}"
                currentAlternative.isLocal -> "Current: local file ${currentAlternative.label.ifBlank { currentAlternative.value }}"
                else -> "Current alternative stream"
            },
            color = if (currentAlternative == null) Color(0xFFB9B4C3) else Color(AppPalette.toArgb()),
            fontSize = 13.sp,
            maxLines = 2,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = youtubeText,
            onValueChange = { youtubeText = it },
            enabled = enabled,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
            label = { Text("YouTube link or video ID", color = Color(0xFFB9B4C3)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(enabled = enabled && youtubeText.isNotBlank(), onClick = { onUseYouTube(youtubeText) }) {
                Text("Use YouTube", color = if (enabled && youtubeText.isNotBlank()) AppPalette else Color.Gray)
            }
        }
        PlayerMenuRow(
            icon = Icons.Default.Add,
            label = "Use local audio file",
            enabled = enabled,
        ) {
            onPickLocal()
        }
        PlayerMenuRow(
            icon = Icons.Default.CheckCircle,
            label = "Clear alternative stream",
            enabled = enabled && currentAlternative != null,
            iconTint = Color(0xFFE57373),
        ) {
            onClear()
        }
    }
}
@Composable
fun PlayerMenuRow(
    icon: ImageVector,
    label: String,
    iconTint: Color = Color.White,
    enabled: Boolean = true,
    trailingArrow: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal=14.dp,vertical=4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(io.github.unidentified24.spock.ui.theme.SurfaceVariant)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Icon(
            imageVector = icon,
            tint = if (enabled) iconTint else Color.Gray,
            modifier = Modifier.size(22.dp),
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = label,
            color = if (enabled) Color.White else Color.Gray,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        if (trailingArrow) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                tint = Color.Gray,
                modifier = Modifier.size(20.dp),
                contentDescription = null
            )
        }
    }
}
