package io.github.unidentified24.spock.ui.viewmodel

import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.unidentified24.spock.data.api.Response
import io.github.unidentified24.spock.data.entity.AlbumsModel
import io.github.unidentified24.spock.data.entity.SongsModel
import io.github.unidentified24.spock.di.CurrentSongState
import io.github.unidentified24.spock.ui.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val repository: AppRepository,
    private val currentSongState: CurrentSongState,
) : ViewModel() {

    val currentSongPlayingState: State<Boolean> get() = currentSongState.playingState
    val currentSongId: State<Int> get() = currentSongState.songId

    private val _songs: MutableStateFlow<Response<List<SongsModel>>> = MutableStateFlow(Response.Loading())
    val songs: StateFlow<Response<List<SongsModel>>> = _songs

    private val _playlist: MutableStateFlow<Response<AlbumsModel>> = MutableStateFlow(Response.Loading())
    val playlist: StateFlow<Response<AlbumsModel>> = _playlist

    val queue: State<List<SongsModel>> get() = currentSongState.queue

    fun updateQueue(songs: List<SongsModel>) = currentSongState.updateQueue(songs)

    fun startShuffled(songs: List<SongsModel>) = currentSongState.startShuffled(songs)

    /** Pause/resume global playback (the header play button stays visible while playing). */
    fun setPlaying(playing: Boolean) {
        if (playing) io.github.unidentified24.spock.di.SongPlayer.play() else io.github.unidentified24.spock.di.SongPlayer.pause()
        currentSongState.updatePlayingState(playing)
    }

    fun updateSongState(coverUri: String, title: String, singer: String, playingState: Boolean, songId: Int, songIndex: Int = 0, album: String = "") {
        currentSongState.updateSongState(coverUri, title, singer, playingState, songId, songIndex, album)
    }

    val likeState = currentSongState.likeState

    fun updateLikeState(likeState: Boolean) {
        currentSongState.updateLikeState(likeState)
    }

    private var playlistKey: String? = null
    @Volatile private var loadGeneration=0
    private val _allTracksLoaded=MutableStateFlow(false)
    val allTracksLoaded:StateFlow<Boolean> = _allTracksLoaded

    fun loadPlaylist(playlistId: String, force:Boolean=false) {
        if (!force && playlistKey == playlistId) return
        playlistKey = playlistId
        val generation=++loadGeneration
        _allTracksLoaded.value=false
        _songs.value=Response.Loading()
        _playlist.value=Response.Loading()
        viewModelScope.launch(Dispatchers.IO) {
            try {repository.providePlaylist(playlistId).collect { if(loadGeneration==generation)_playlist.value = it }}
            catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){if(loadGeneration==generation)_playlist.value=Response.Error("Couldn't load playlist details")}
        }
        viewModelScope.launch(Dispatchers.IO) {
            try { repository.providePlaylistSongs(playlistId).collect { if(loadGeneration==generation)_songs.value = it } }
            catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){if(loadGeneration==generation)_songs.value=Response.Error("Couldn't load the complete playlist")}
            finally {if(loadGeneration==generation)_allTracksLoaded.value=true}
        }
    }
}
