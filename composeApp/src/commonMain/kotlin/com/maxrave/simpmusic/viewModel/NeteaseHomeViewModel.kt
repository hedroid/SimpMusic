package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maxrave.data.repository.NeteaseRepositoryImpl
import com.maxrave.domain.data.model.home.HomeItem
import com.maxrave.netease.model.NeteaseHighQualityTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
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

    /** 高质量分类标签(主页 chips),与 YT 主页的 mood chips 同位置同交互 */
    private val _tags = MutableStateFlow<List<NeteaseHighQualityTag>>(emptyList())
    val tags: StateFlow<List<NeteaseHighQualityTag>> = _tags.asStateFlow()

    /** 当前选中的分类(null=全部),只影响"精品歌单"行 */
    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    private val hqRowTitle = "精品歌单"

    init {
        neteaseRepository.isLoggedIn
            .onEach { _loggedIn.value = it }
            .launchIn(viewModelScope)
        viewModelScope.launch {
            neteaseRepository.getHighQualityTags().getOrNull()?.let { _tags.value = it }
        }
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

    /**
     * chip 点击:局部替换"精品歌单"行,其余行不动(对比 YT chips 整页换 mood,
     * 这里只有精品歌单是分类敏感的,没必要让推荐/雷达闪一次 loading)。
     */
    fun selectTag(cat: String?) {
        if (_selectedTag.value == cat) return
        _selectedTag.value = cat
        viewModelScope.launch {
            neteaseRepository.getHqPlaylistsRow(cat).getOrNull()?.let { newRow ->
                _state.update { current ->
                    if (current is State.Ready) {
                        val rows = current.rows.toMutableList()
                        val index = rows.indexOfFirst { it.title == hqRowTitle }
                        if (index >= 0) rows[index] = newRow else rows.add(newRow)
                        State.Ready(rows)
                    } else {
                        current
                    }
                }
            }
        }
    }
}
