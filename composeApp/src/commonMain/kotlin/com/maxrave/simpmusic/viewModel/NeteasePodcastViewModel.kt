package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.viewModelScope
import com.maxrave.common.Config
import com.maxrave.data.repository.NeteaseRepositoryImpl
import com.maxrave.data.repository.toResultSong
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.utils.toTrack
import com.maxrave.netease.model.NeteaseDjProgram
import com.maxrave.netease.model.NeteaseDjRadio
import com.maxrave.netease.model.NeteasePodcastCategory
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.login_netease_first
import simpmusic.composeapp.generated.resources.podcast_latest_programs
import simpmusic.composeapp.generated.resources.podcast_program_toplist
import simpmusic.composeapp.generated.resources.podcast_subscribe_toast
import simpmusic.composeapp.generated.resources.podcast_unsubscribe_toast

/**
 * 网易云播客 chip 页(Koin single:tab 往返零重拉,NeteaseHomeViewModel 同款——entry-scope 会
 * "切 tab 回来满屏转圈")。端点形状对齐 Melodia 真机抓包(eapi),区块独立降级:
 * 猜你喜欢/我的订阅未登录或失败即隐藏,不阻塞其它区块。
 * **节目可播的是 mainSong.id**(节目自身 id 取流无效),装载时即过滤不可播节目,
 * 保证播放列表下标与显示列表一致。
 */
class NeteasePodcastViewModel(
    private val neteaseRepository: NeteaseRepositoryImpl,
    private val sharedViewModel: SharedViewModel,
) : BaseViewModel() {
    data class UiState(
        val categories: List<NeteasePodcastCategory> = emptyList(),
        val hotRadios: List<NeteaseDjRadio> = emptyList(),
        val newRadios: List<NeteaseDjRadio> = emptyList(),
        val programToplist: List<NeteaseDjProgram> = emptyList(),
        val programs: List<NeteaseDjProgram> = emptyList(),
        val programsLoading: Boolean = true,
        val personalizedRadios: List<NeteaseDjRadio> = emptyList(),
        val recommendRadios: List<NeteaseDjRadio> = emptyList(),
        val myRadios: List<NeteaseDjRadio> = emptyList(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> get() = _uiState.asStateFlow()

    /** 首拉过一次即不再整页 Loading(静默刷新口径) */
    private var loadedOnce = false

    /** chip 页被选中时调用:首拉整页,之后只静默刷新订阅区(详情页订阅/退订要反映回来) */
    fun onPageSelected() {
        if (!loadedOnce) {
            loadedOnce = true
            refresh()
        } else {
            refreshMyRadios()
        }
    }

    /** 静默刷新:不清已显示分区(下拉刷新口径),各区块独立回写。
     *  并行发起(每个请求各自 launch,网易主页三分区同口径)——串行 7 个请求要
     *  1.5~2.5s 才全部落地,并行后总耗时=最慢单请求;回写都是 update{},并发安全。 */
    fun refresh() {
        viewModelScope.launch {
            neteaseRepository.getPodcastCategories().onSuccess { categories ->
                _uiState.update { it.copy(categories = categories) }
            }
        }
        viewModelScope.launch {
            neteaseRepository.getRecommendDjRadios().onSuccess { radios ->
                _uiState.update { it.copy(recommendRadios = radios) }
            }
        }
        viewModelScope.launch {
            neteaseRepository.getDjRadioToplist(type = 1).onSuccess { radios ->
                _uiState.update { it.copy(hotRadios = radios) }
            }
        }
        viewModelScope.launch {
            neteaseRepository.getDjRadioToplist(type = 0).onSuccess { radios ->
                _uiState.update { it.copy(newRadios = radios) }
            }
        }
        viewModelScope.launch {
            // 热门节目榜:元素是节目形状,过滤不可播(同最新节目口径)
            neteaseRepository.getDjProgramToplist().onSuccess { programs ->
                _uiState.update { it.copy(programToplist = programs.filter { p -> p.mainSongId != null }) }
            }
        }
        viewModelScope.launch {
            // 猜你喜欢:未登录时服务端返回空列表(成功),区块随之隐藏
            neteaseRepository.getPersonalizedDjRadios().onSuccess { radios ->
                _uiState.update { it.copy(personalizedRadios = radios) }
            }
        }
        refreshMyRadios()
        viewModelScope.launch { reloadPrograms() }
    }


    private fun refreshMyRadios() {
        viewModelScope.launch {
            neteaseRepository.getMyDjRadios().onSuccess { radios ->
                _uiState.update { it.copy(myRadios = radios) }
            }
        }
    }

    private suspend fun reloadPrograms() {
        // cateId 服务端忽略(见 Screen 文档注释),恒传 null
        neteaseRepository.getRecommendPodcastProgramsPage(null, offset = 0).fold(
            onSuccess = { (programs, more) ->
                // 不可播节目(mainSong 缺失)不进列表,显示列表==可播列表,下标对齐
                val playable = programs.filter { it.mainSongId != null }
                _uiState.update {
                    it.copy(programs = playable, programsLoading = false)
                }
            },
            onFailure = {
                _uiState.update { it.copy(programsLoading = false) }
            },
        )
    }

    /** 点节目=从点击处整队起播(最新节目列表,播完即止) */
    fun playProgram(index: Int) {
        val tracks =
            _uiState.value.programs
                .mapNotNull { it.toResultSong() }
                .map { it.toTrack() }
        val first = tracks.getOrNull(index) ?: return
        setQueueData(
            QueueData.Data(
                listTracks = ArrayList(tracks),
                firstPlayedTrack = first,
                playlistId = "NETEASE_PODCAST_LATEST",
                playlistName = getString(Res.string.podcast_latest_programs),
                playlistType = PlaylistType.PLAYLIST,
                continuation = null,
            ),
        )
        sharedViewModel.loadMediaItemFromTrack(first, Config.PLAYLIST_CLICK, index)
    }

    /** 点热门节目榜=从点击处整队起播(榜单列表,播完即止) */
    fun playProgramToplist(index: Int) {
        val tracks =
            _uiState.value.programToplist
                .mapNotNull { it.toResultSong() }
                .map { it.toTrack() }
        val first = tracks.getOrNull(index) ?: return
        setQueueData(
            QueueData.Data(
                listTracks = ArrayList(tracks),
                firstPlayedTrack = first,
                playlistId = "NETEASE_PODCAST_TOPLIST",
                playlistName = getString(Res.string.podcast_program_toplist),
                playlistType = PlaylistType.PLAYLIST,
                continuation = null,
            ),
        )
        sharedViewModel.loadMediaItemFromTrack(first, Config.PLAYLIST_CLICK, index)
    }

    /** 订阅/退订(登录态 repo 层先验,失败 toast 不动列表) */
    fun setSubscribed(
        radioId: Long,
        subscribe: Boolean,
    ) {
        viewModelScope.launch {
            neteaseRepository.setDjRadioSubscribed(radioId, subscribe).fold(
                onSuccess = {
                    makeToast(getString(if (subscribe) Res.string.podcast_subscribe_toast else Res.string.podcast_unsubscribe_toast))
                    refreshMyRadios()
                },
                onFailure = {
                    makeToast(getString(Res.string.login_netease_first))
                },
            )
        }
    }
}
