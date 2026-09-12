package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maxrave.data.repository.NeteaseRepositoryImpl
import com.maxrave.domain.data.model.home.HomeItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * 网易云主页(M4):独立页,数据全部来自 [NeteaseRepositoryImpl.getHome]
 * (每日推荐歌单/雷达组/排行榜/推荐新歌/精品歌单,YTM HomeItem 形状)。
 * 行渲染复用上游 AdapterItems.HomeItem,点歌/点歌单的导航与播放走同一条链路。
 */
class NeteaseHomeViewModel(
    private val neteaseRepository: NeteaseRepositoryImpl,
) : ViewModel() {
    sealed interface State {
        data object Loading : State

        data class Ready(val rows: List<HomeItem>) : State

        data class Error(val message: String? = null) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    /** 未登录:主页可看公共 feed,但播放/红心都不可用,给出登录引导 */
    private val _loggedIn = MutableStateFlow(true)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    init {
        neteaseRepository.isLoggedIn
            .onEach { _loggedIn.value = it }
            .launchIn(viewModelScope)
        refresh()
    }

    fun refresh() {
        _state.value = State.Loading
        viewModelScope.launch {
            neteaseRepository
                .getHome()
                .fold(
                    onSuccess = { rows ->
                        _state.value =
                            if (rows.isEmpty()) {
                                State.Error()
                            } else {
                                State.Ready(rows)
                            }
                    },
                    onFailure = { _state.value = State.Error(it.message) },
                )
        }
    }
}
