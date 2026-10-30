package io.github.unidentified24.spock

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import io.github.unidentified24.spock.data.preferences.toSong
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import io.github.unidentified24.spock.di.SongPlayer
import io.github.unidentified24.spock.ui.notification.PlaybackService
import io.github.unidentified24.spock.ui.theme.SpockTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @javax.inject.Inject lateinit var songState:io.github.unidentified24.spock.di.CurrentSongState
    private fun openAudio(incoming:android.content.Intent?) {
        if(incoming?.action!=android.content.Intent.ACTION_VIEW)return
        val uri=incoming.data?.takeIf{it.scheme=="content"} ?: return
        lifecycleScope.launch {
            val tracks=try {withContext(Dispatchers.IO){io.github.unidentified24.spock.data.local.LocalImport.importFiles(this@MainActivity,listOf(uri))}}
                catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
                catch(error:Exception){android.widget.Toast.makeText(this@MainActivity,"Couldn't open this audio file. Choose it again from Local music.",android.widget.Toast.LENGTH_LONG).show();return@launch}
            val track=tracks.firstOrNull() ?: return@launch
            io.github.unidentified24.spock.data.preferences.addLocalTracks(this@MainActivity,tracks)
            val song=track.toSong()
            songState.updateQueue(listOf(song));songState.updateSongState(song.coverUri,song.title,song.singer,true,song.id,0,song.album)
            SongPlayer.playSong(song.url,this@MainActivity)
        }
    }
    override fun onNewIntent(incoming:android.content.Intent){super.onNewIntent(incoming);setIntent(android.content.Intent());openAudio(incoming)}
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val browserHost:()->Unit={if(!isDestroyed)io.github.unidentified24.spock.di.SpotifyWebPlayer.attach(this)}

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    @OptIn(UnstableApi::class)
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?){

        super.onCreate(savedInstanceState)
        SongPlayer.onBrowserNeeded=browserHost
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

        // Ask for notification permission (Android 13+) so the media notification shows.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !getSharedPreferences("onboarding",0).getBoolean("notificationsAsked",false) &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            getSharedPreferences("onboarding",0).edit().putBoolean("notificationsAsked",true).apply()
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // Connect a controller to bootstrap the MediaSessionService: this brings up
        // the system media notification and keeps playback alive in the background.
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()


        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {

            SpockTheme {
                // A surface container using the 'background' color from the theme
                    App()

                // New-release check (GitHub): prompts Upgrade / Dismiss / Don't show again.
                io.github.unidentified24.spock.ui.components.UpdatePrompt()
            }
        }

        // Experimental Spotify web-player engine: attach its hidden WebView AFTER
        // setContent so the Compose content view doesn't replace/orphan it (an
        // orphaned WebView gets a 0×0 viewport and Spotify won't render/navigate).
        if (SongPlayer.webPlayerEnabled && io.github.unidentified24.spock.data.preferences.isWebPlaybackEnabled(this) &&
            io.github.unidentified24.spock.data.api.SpotifySession.spDc(this).isNotBlank()) {
            io.github.unidentified24.spock.di.SpotifyWebPlayer.attach(this)
        }
    }

    override fun onStart(){super.onStart();if(intent?.action==android.content.Intent.ACTION_VIEW){val pending=intent;setIntent(android.content.Intent());openAudio(pending)}}
    override fun onDestroy() {
        super.onDestroy()
        if(SongPlayer.onBrowserNeeded===browserHost)SongPlayer.onBrowserNeeded=null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        // The foreground playback service owns release, including background audio.
    }
}


