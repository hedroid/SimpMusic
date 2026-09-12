package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.viewModelScope
import com.maxrave.common.Config
import com.maxrave.data.repository.NeteaseRepositoryImpl
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.data.model.home.Content
import com.maxrave.domain.data.model.home.HomeItem
import com.maxrave.domain.data.model.home.chart.Chart
import com.maxrave.domain.data.model.mood.Mood
import com.maxrave.domain.utils.toTrack
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 网易主页独立 ViewModel:网易专属页面,数据全部来自 [NeteaseRepositoryImpl],
 * 与上游 HomeViewModel 无任何共享状态(避免上次独立页误实例化上游 VM 的坑)。
 * 播放走 [BaseViewModel] 的 setQueueData/loadMediaItem(与上游同一条播放链路)。
 */
class NeteaseHomeViewModel(
    private val neteaseRepository: NeteaseRepositoryImpl,
) : BaseViewModel() {
    data class ReadyState(
        val rows: List<HomeItem>,
        val sections: Mood?,
        val chart: Chart?,
    )

    sealed interface State {
        data object Loading : State

        data class Ready(val data: ReadyState) : State

        data class Error(val message: String? = null) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    /** 账户摘要(欢迎区),未登录 null */
    private val _accountInfo = MutableStateFlow<Pair<String?, String?>?>(null)
    val accountInfo: StateFlow<Pair<String?, String?>?> = _accountInfo.asStateFlow()

    /** 顶栏 chips:固定 8 个高频分类快捷 */
    val chips: List<String> = neteaseRepository.curatedHomeTags

    init {
        viewModelScope.launch {
            neteaseRepository.isLoggedIn.collect { logged ->
                _accountInfo.value =
                    if (logged) {
                        Pair(
                            neteaseRepository.accountName.first(),
                            neteaseRepository.accountThumbUrl.first(),
                        )
                    } else {
                        null
                    }
            }
        }
        refresh()
    }

    fun refresh() {
        _state.value = State.Loading
        viewModelScope.launch {
            val rows = neteaseRepository.getHome().getOrNull() ?: emptyList()
            val chart = neteaseRepository.getHomeChart().getOrNull()
            val sections = neteaseRepository.getMoodSections().getOrNull()
            if (rows.isEmpty() && chart == null && sections == null) {
                _state.value = State.Error()
            } else {
                _state.value = State.Ready(ReadyState(rows = rows, sections = sections, chart = chart))
            }
        }
    }

    /** 点歌播放:单曲队列 + 电台续播语义,与上游 HomeItem 同款 */
    fun playSong(content: Content) {
        val track = content.toTrack()
        setQueueData(
            QueueData.Data(
                listTracks = arrayListOf(track),
                firstPlayedTrack = track,
                playlistId = "RDAMVM${content.videoId}",
                playlistName = content.title,
                playlistType = PlaylistType.RADIO,
                continuation = null,
            ),
        )
        loadMediaItem(track, Config.SONG_CLICK)
    }
}
