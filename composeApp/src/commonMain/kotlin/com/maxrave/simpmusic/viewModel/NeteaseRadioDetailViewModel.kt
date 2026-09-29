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
import com.maxrave.simpmusic.extension.neteaseWriteErrorString
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.login_netease_first
import simpmusic.composeapp.generated.resources.netease_action_failed
import simpmusic.composeapp.generated.resources.podcast_subscribe_toast
import simpmusic.composeapp.generated.resources.podcast_unsubscribe_toast

/**
 * 网易云播客电台详情页(entry-scope,SimilarSongsViewModel 同款):
 * 电台信息(eapi /djradio/get,含已订阅态)+ 节目列表(30/批 offset 分页,默认新→旧)。
 * **播放队列用的是 mainSong.id**(节目自身 id 取流无效);playlistId 刻意用
 * NETEASE_PODCAST_RADIO_ 前缀——不碰 NETEASE_RADIO_ 无尽电台哨兵(那套会拿 simiSong 续播,
 * 与电台"播完即止"语义不符)。
 */
class NeteaseRadioDetailViewModel(
    private val neteaseRepository: NeteaseRepositoryImpl,
    private val sharedViewModel: SharedViewModel,
) : BaseViewModel() {
    data class UiState(
        val radio: NeteaseDjRadio? = null,
        val programs: List<NeteaseDjProgram> = emptyList(),
        val loading: Boolean = true,
        val loadingMore: Boolean = false,
        val hasMore: Boolean = false,
        val subInFlight: Boolean = false,
        /** 非 null=节目列表拉取失败——byradio 连续调用会被网易限流(HTTP 200+空数据或
         *  code=405"操作频繁",同一电台先空后有即此),网络抖动同走这里;UI 给重试不冒充空态 */
        val programsUnavailable: Boolean = false,
        /** 节目排序:false=最新在前(默认,byradio asc=false)/true=最早在前(asc=true) */
        val ascending: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> get() = _uiState.asStateFlow()

    private var radioId = 0L

    fun load(id: Long) {
        if (radioId == id && !_uiState.value.loading) return
        radioId = id
        _uiState.update { it.copy(loading = true, programsUnavailable = false) }
        viewModelScope.launch {
            // 串行:节目"空且电台声明有节目=不可用"的判定依赖详情的 programCount,
            // 并行会有详情未回(declared=0)的误判窗口
            neteaseRepository.getDjRadioDetail(id).fold(
                onSuccess = { radio -> _uiState.update { it.copy(radio = radio) } },
                onFailure = { log("radio detail failed: $it") },
            )
            loadPrograms()
        }
    }

    /** 重试(不可用态/失败的入口);成功但空且电台声明有节目=限流的静默空形态,也归不可用 */
    fun retry() {
        _uiState.update { it.copy(loading = true, programsUnavailable = false) }
        viewModelScope.launch { loadPrograms() }
    }

    /** 切换 最新在前/最早在前(byradio asc 参数),清空重拉第一页 */
    fun setAscending(ascending: Boolean) {
        if (_uiState.value.ascending == ascending) return
        _uiState.update { it.copy(ascending = ascending, programs = emptyList(), hasMore = false, loading = true, programsUnavailable = false) }
        viewModelScope.launch { loadPrograms() }
    }

    private suspend fun loadPrograms() {
        neteaseRepository.getDjRadioProgramsPage(radioId, offset = 0, asc = _uiState.value.ascending).fold(
            onSuccess = { (programs, more) ->
                // 不可播节目(mainSong 缺失)不进列表,显示列表==可播列表,下标对齐
                val playable = programs.filter { it.mainSongId != null }
                val declared = _uiState.value.radio?.programCount ?: 0
                val unavailable = playable.isEmpty() && declared > 0
                _uiState.update {
                    it.copy(
                        programs = playable,
                        hasMore = more,
                        loading = false,
                        programsUnavailable = unavailable,
                    )
                }
            },
            onFailure = {
                log("radio programs failed: $it")
                _uiState.update { it.copy(loading = false, programsUnavailable = true) }
            },
        )
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.hasMore || state.loadingMore || state.loading) return
        _uiState.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            neteaseRepository
                .getDjRadioProgramsPage(radioId, offset = state.programs.size, asc = _uiState.value.ascending)
                .fold(
                    onSuccess = { (programs, more) ->
                        val playable = programs.filter { it.mainSongId != null }
                        _uiState.update { current ->
                            // 整页撞重=服务端重复发批,收尾防 offset 死循环(SimilarSongs 同款)
                            val fresh = playable.filterNot { p -> current.programs.any { it.id == p.id } }
                            current.copy(
                                programs = current.programs + fresh,
                                hasMore = more && fresh.isNotEmpty(),
                                loadingMore = false,
                            )
                        }
                    },
                    onFailure = {
                        _uiState.update { it.copy(loadingMore = false) }
                    },
                )
        }
    }

    /** 点节目=从点击处整队起播(播完即止) */
    fun playFrom(index: Int) {
        val tracks =
            _uiState.value.programs
                .mapNotNull { it.toResultSong() }
                .map { it.toTrack() }
        val first = tracks.getOrNull(index) ?: return
        setQueueData(
            QueueData.Data(
                listTracks = ArrayList(tracks),
                firstPlayedTrack = first,
                playlistId = "NETEASE_PODCAST_RADIO_$radioId",
                playlistName = _uiState.value.radio?.name ?: "",
                playlistType = PlaylistType.PLAYLIST,
                continuation = null,
            ),
        )
        sharedViewModel.loadMediaItemFromTrack(first, Config.PLAYLIST_CLICK, index)
    }

    /** 订阅/退订(登录态 repo 层先验;按钮态在_flight 中防连点) */
    fun toggleSubscribe() {
        val state = _uiState.value
        val radio = state.radio ?: return
        if (state.subInFlight) return
        val target = radio.subed != true
        _uiState.update { it.copy(subInFlight = true) }
        viewModelScope.launch {
            neteaseRepository.setDjRadioSubscribed(radioId, target).fold(
                onSuccess = {
                    makeToast(getString(if (target) Res.string.podcast_subscribe_toast else Res.string.podcast_unsubscribe_toast))
                    _uiState.update {
                        it.copy(
                            subInFlight = false,
                            radio = it.radio?.copy(subed = target, subCount = (it.radio?.subCount ?: 0L) + if (target) 1 else -1),
                        )
                    }
                },
                onFailure = { e ->
                    // 失败≠没生效:sub/unsub 请求可能已被服务端处理、只是响应被频控/网络层
                    // 拦掉(实测"订阅成功却报错"即此)——先拉订阅列表对账,状态已到位按成功处理
                    val actualSubed = neteaseRepository.getMyDjRadios().getOrNull()?.any { it.id == radioId }
                    if (actualSubed == target) {
                        makeToast(getString(if (target) Res.string.podcast_subscribe_toast else Res.string.podcast_unsubscribe_toast))
                        _uiState.update {
                            it.copy(
                                subInFlight = false,
                                radio = it.radio?.copy(subed = target),
                            )
                        }
                    } else {
                        // 文案分流:未登录→登录提示;405 频控→操作频繁;其余→操作失败。
                        // 一律报"请登录"是错误引导(2026-09-29 用户实测踩中)
                        val msgRes =
                            when {
                                e.message?.contains("未登录") == true -> Res.string.login_netease_first
                                else -> neteaseWriteErrorString(e, Res.string.netease_action_failed)
                            }
                        makeToast(getString(msgRes))
                        _uiState.update { it.copy(subInFlight = false) }
                    }
                },
            )
        }
    }
}
