package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maxrave.data.repository.NeteaseRepositoryImpl
import com.maxrave.data.repository.NeteaseTagOrder
import com.maxrave.domain.data.model.mood.moodmoments.Content
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 网易标签分类页(网格)VM:tag + 排序 → 分类歌单列表(网页版 discover/playlist 同源)。
 *  VM 每次导航是新实例,跨页/跨次进入的缓存都在仓库层(getTagContent 会话缓存)。 */
class NeteaseTagViewModel(
    private val neteaseRepository: NeteaseRepositoryImpl,
) : ViewModel() {
    sealed interface State {
        data object Loading : State

        data class Ready(val contents: List<Content>) : State

        data class Error(val message: String? = null) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> get() = _state.asStateFlow()

    private var loadedTag: String? = null
    private var loadedOrder: NeteaseTagOrder? = null

    fun load(
        tag: String,
        order: NeteaseTagOrder = NeteaseTagOrder.HOT,
        force: Boolean = false,
    ) {
        if (!force && tag == loadedTag && order == loadedOrder && _state.value is State.Ready) return
        _state.value = State.Loading
        viewModelScope.launch {
            neteaseRepository.getTagContent(tag, order, force).fold(
                onSuccess = { data ->
                    loadedTag = tag
                    loadedOrder = order
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
