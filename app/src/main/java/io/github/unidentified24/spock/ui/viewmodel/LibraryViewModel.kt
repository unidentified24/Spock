package io.github.unidentified24.spock.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.unidentified24.spock.data.api.Response
import io.github.unidentified24.spock.data.entity.AccountModel
import io.github.unidentified24.spock.data.entity.LibraryEntry
import io.github.unidentified24.spock.data.preferences.cacheFollowedArtists
import io.github.unidentified24.spock.data.preferences.cacheLibraryEntries
import io.github.unidentified24.spock.data.preferences.getCachedFollowedArtists
import io.github.unidentified24.spock.data.preferences.getCachedLibraryEntries
import io.github.unidentified24.spock.ui.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: AppRepository,
    @ApplicationContext private val context: android.content.Context,
) : ViewModel() {

    private val _entries: MutableStateFlow<Response<List<LibraryEntry>>> = MutableStateFlow(Response.Loading())
    val entries: StateFlow<Response<List<LibraryEntry>>> = _entries

    private val _account: MutableStateFlow<Response<AccountModel>> = MutableStateFlow(Response.Loading())
    val account: StateFlow<Response<AccountModel>> = _account

    private val _followedArtists: MutableStateFlow<List<io.github.unidentified24.spock.data.entity.ArtistsModel>> =
        MutableStateFlow(emptyList())
    val followedArtists: StateFlow<List<io.github.unidentified24.spock.data.entity.ArtistsModel>> = _followedArtists

    init {
        // Seed UI with the last-seen library immediately so it's visible offline.
        val cached = getCachedLibraryEntries(context)
        if (cached.isNotEmpty()) _entries.value = Response.Success(cached)
        val cachedArtists = getCachedFollowedArtists(context)
        if (cachedArtists.isNotEmpty()) _followedArtists.value = cachedArtists

        load()
        loadAccount()
        loadFollowedArtists()
    }

    fun load() = viewModelScope.launch(Dispatchers.IO) {
        repository.provideLibrary().collect { response ->
            // Offline: keep the saved library instead of replacing it with a
            // spinner/error, so downloads stay reachable after a restart (#51).
            if (response !is Response.Success && _entries.value is Response.Success) return@collect
            // Nothing saved yet and no network: offer just the downloads (not cached).
            if (response is Response.Error) {
                _entries.value = Response.Success(listOf(io.github.unidentified24.spock.data.api.Api.DOWNLOADED_ENTRY))
                return@collect
            }
            _entries.value = response
            if (response is Response.Success) cacheLibraryEntries(context, response.data)
        }
    }

    private fun loadFollowedArtists() = viewModelScope.launch(Dispatchers.IO) {
        val artists = repository.provideFollowedArtists()
        _followedArtists.value = artists
        if (artists.isNotEmpty()) cacheFollowedArtists(context, artists)
    }

    private fun loadAccount() = viewModelScope.launch(Dispatchers.IO) {
        repository.provideAccount().collect { _account.value = it }
    }
}
