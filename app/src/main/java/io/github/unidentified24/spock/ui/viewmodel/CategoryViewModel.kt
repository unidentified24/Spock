package io.github.unidentified24.spock.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.unidentified24.spock.data.api.Response
import io.github.unidentified24.spock.data.entity.LibraryEntry
import io.github.unidentified24.spock.ui.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoryViewModel @Inject constructor(private val repository: AppRepository) : ViewModel() {

    private val _playlists: MutableStateFlow<Response<List<LibraryEntry>>> = MutableStateFlow(Response.Loading())
    val playlists: StateFlow<Response<List<LibraryEntry>>> = _playlists

    private var genreKey: String? = null

    fun load(genre: String) {
        if (genreKey == genre) return
        genreKey = genre
        viewModelScope.launch(Dispatchers.IO) {
            repository.provideCategoryPlaylists(genre).collect { _playlists.value = it }
        }
    }
}
