package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maxrave.data.repository.NeteaseRepositoryImpl
import com.maxrave.domain.data.model.mood.moodmoments.Content
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 网易标签分类页(网格)VM:tag → 分类歌单列表(网页版 discover/playlist 同源) */
class NeteaseTagViewModel(
    private val neteaseRepository: NeteaseRepositoryImpl,
) : ViewModel() {
    sealed interface State {
        data object Loading : State

        data class Ready(val contents: List<Content>) : State

        data class Error(val message: String? = null) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private var loadedTag: String? = null

    fun load(tag: String) {
        if (tag == loadedTag && _state.value is State.Ready) return
        _state.value = State.Loading
        viewModelScope.launch {
            neteaseRepository.getMoodContent(tag).fold(
                onSuccess = { data ->
                    loadedTag = tag
                    _state.value =
                        data?.items?.firstOrNull()?.contents?.filterNotNull()?.let {
                            State.Ready(it)
                        } ?: State.Error()
                },
                onFailure = { _state.value = State.Error(it.message) },
            )
        }
    }
}
