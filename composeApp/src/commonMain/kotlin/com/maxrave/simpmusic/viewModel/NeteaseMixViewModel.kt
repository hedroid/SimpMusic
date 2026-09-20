package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.viewModelScope
import com.maxrave.common.Config
import com.maxrave.common.NETEASE_FM_PLAYLIST_ID
import com.maxrave.data.repository.NeteaseRepositoryImpl
import com.maxrave.domain.data.model.home.Content
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.utils.toTrack
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.personal_fm

/** 下拉指示器时长上限,与 [NeteaseHomeViewModel] 同款:确认信号而非进度条 */
private const val REFRESH_INDICATOR_MS = 600L

/**
 * 网易"混合"tab(私人FM)独立 ViewModel:与 [NeteaseHomeViewModel] 同款模式——数据全部来自
 * [NeteaseRepositoryImpl],不实例化上游 ViewModel。页面分区:FM(hero+单行两列增量卡,
 * 队列挂 [NETEASE_FM_PLAYLIST_ID] 哨兵耗尽续批)、红心电台(红心随机 30 首起播,同挂哨兵)、
 * 每日推荐(日缓存)、新歌速递(地区 chips 切换)、最近在听(听歌周榜)。各分区并行拉取、
 * 单区失败只隐藏该区不拖垮整页。
 */
class NeteaseMixViewModel(
    private val neteaseRepository: NeteaseRepositoryImpl,
) : BaseViewModel() {
    /** FM 页内横滑增量加载的硬上限,防数据膨胀;达到后提示一次 */
    private companion object {
        const val FM_MAX_SONGS = 90

        /** 新歌速递默认地区:华语(0 全部 7 华语 96 欧美 8 日语 16 韩语) */
        const val EXPRESS_DEFAULT_AREA = 7
    }

    sealed interface State {
        data object Loading : State

        data class Ready(
            val fmContents: List<Content>,
            val dailyContents: List<Content>,
            val expressContents: List<Content>,
            val expressArea: Int,
            val recentContents: List<Content>,
        ) : State

        data class Error(val message: String? = null) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    /** 下拉指示器:手势已受理的短确认(≤600ms/首批落地先到先收),不等数据工作全程 */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** 红心电台入口卡加载态(拉红心 id + 批量详情期间转圈) */
    private val _heartLoading = MutableStateFlow(false)
    val heartLoading: StateFlow<Boolean> = _heartLoading.asStateFlow()

    /** FM 行尾增量加载中(行尾转圈 item) */
    private val _fmLoadingMore = MutableStateFlow(false)
    val fmLoadingMore: StateFlow<Boolean> = _fmLoadingMore.asStateFlow()

    /** 新歌速递地区切换中(chips 区小转圈) */
    private val _expressLoading = MutableStateFlow(false)
    val expressLoading: StateFlow<Boolean> = _expressLoading.asStateFlow()

    /** 上限提示只弹一次(VM 生命周期内;列表只增不减,重进页面/换进程自然重置) */
    private var fmCapNotified = false

    init {
        refresh()
    }

    /** 下拉刷新:已有内容(Ready)时**向后追加一批**,不整页重刷(列表只增不减,90 封顶);
     *  无内容(Loading/Error)时全量拉取兜底(错误页重试同路)。
     *  指示器统一走 [refreshing](600ms 上限/首批落地先收):Ready 追加路曾经完全不转(无反馈),
     *  整页重载路曾经陪跑全部 4 个并行请求——转好几秒就读成"转了好多圈"。 */
    fun refresh(force: Boolean = false) {
        if (!force && _state.value is State.Ready) return
        if (!_refreshing.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            launch {
                delay(REFRESH_INDICATOR_MS)
                _refreshing.value = false
            }
            if (force && _state.value is State.Ready) {
                loadMoreFm()
            } else {
                _state.value = State.Loading
                val (fm, daily, express, recent) =
                    coroutineScope {
                        val fmDeferred = async { neteaseRepository.getPersonalFmContents().getOrDefault(emptyList()) }
                        // 每日推荐恒走日缓存,不随下拉刷新重拉:端点带 afresh=true,服务端每次
                        // 重掷一版(探针实测),force 重拉等于换掉当天整份列表。跨日由 epochDay 失效。
                        val dailyDeferred = async { neteaseRepository.getDailyRecommendContents().getOrDefault(emptyList()) }
                        val expressDeferred =
                            async { neteaseRepository.getNewSongExpress(EXPRESS_DEFAULT_AREA).getOrDefault(emptyList()) }
                        val recentDeferred = async { neteaseRepository.getRecentPlayedContents().getOrDefault(emptyList()) }
                        listOf(fmDeferred.await(), dailyDeferred.await(), expressDeferred.await(), recentDeferred.await())
                    }
                val ready =
                    State.Ready(
                        fmContents = fm,
                        dailyContents = daily,
                        expressContents = express,
                        expressArea = EXPRESS_DEFAULT_AREA,
                        recentContents = recent,
                    )
                _state.value =
                    if (fm.isEmpty() && daily.isEmpty() && express.isEmpty() && recent.isEmpty()) {
                        State.Error()
                    } else {
                        ready
                    }
            }
            _refreshing.value = false
        }
    }

    /** 新歌速递切地区:chips 点击,命中会话缓存即时换;网络区切换期间小转圈。 */
    fun loadExpress(area: Int) {
        val ready = _state.value as? State.Ready ?: return
        if (area == ready.expressArea || _expressLoading.value) return
        _expressLoading.value = true
        viewModelScope.launch {
            neteaseRepository.getNewSongExpress(area).fold(
                onSuccess = { contents ->
                    (_state.value as? State.Ready)?.let { current ->
                        _state.value = current.copy(expressContents = contents, expressArea = area)
                    }
                },
                onFailure = {
                    // 失败保持原地区内容,chips 回弹
                },
            )
            _expressLoading.value = false
        }
    }

    /** FM 行滑到尾/下拉刷新:拉下一批去重追加,累计 [FM_MAX_SONGS] 首封顶并提示一次。
     *  列表只增不减(垃圾桶可逐首减),封顶后追加静默停止。 */
    fun loadMoreFm() {
        val ready = _state.value as? State.Ready ?: return
        if (_fmLoadingMore.value) return
        if (ready.fmContents.size >= FM_MAX_SONGS) {
            notifyFmCapOnce()
            return
        }
        viewModelScope.launch {
            _fmLoadingMore.value = true
            val seen = ready.fmContents.mapNotNull { it.videoId }.toSet()
            val more = neteaseRepository.fetchMoreFmContents(seen)
            (_state.value as? State.Ready)?.let { current ->
                val existing = current.fmContents.mapNotNull { it.videoId }.toSet()
                val merged = (current.fmContents + more.filter { it.videoId !in existing }).take(FM_MAX_SONGS)
                _state.value = current.copy(fmContents = merged)
                if (merged.size >= FM_MAX_SONGS) notifyFmCapOnce()
            }
            _fmLoadingMore.value = false
        }
    }

    private fun notifyFmCapOnce() {
        if (fmCapNotified) return
        fmCapNotified = true
        makeToast("私人FM 已加载 90 首")
    }

    /** FM 起播:整批入队(RADIO),点哪首哪首 firstPlayed;队列耗尽由 loadMore 续批。 */
    fun playFrom(contents: List<Content>, index: Int) {
        playQueue(contents, index, playlistId = NETEASE_FM_PLAYLIST_ID, nameRes = Res.string.personal_fm)
    }

    /** 红心电台:红心随机 30 首起播,同挂 FM 哨兵——播完接私人FM 续批。 */
    fun playHeartRadio() {
        if (_heartLoading.value) return
        viewModelScope.launch {
            _heartLoading.value = true
            neteaseRepository.getHeartRadioContents().fold(
                onSuccess = { contents ->
                    if (contents.isEmpty()) {
                        makeToast("红心歌单为空")
                    } else {
                        playQueue(contents, 0, playlistId = NETEASE_FM_PLAYLIST_ID, name = "红心电台")
                    }
                },
                onFailure = { makeToast(it.message ?: "红心电台启动失败") },
            )
            _heartLoading.value = false
        }
    }

    /** 每日推荐/新歌速递/最近在听分区起播:整队 RADIO、点哪首哪首起播;不挂哨兵播完即止。 */
    fun playSectionFrom(
        contents: List<Content>,
        index: Int,
        name: String,
    ) {
        playQueue(contents, index, playlistId = null, name = name)
    }

    private fun playQueue(
        contents: List<Content>,
        index: Int,
        playlistId: String?,
        name: String? = null,
        nameRes: org.jetbrains.compose.resources.StringResource? = null,
    ) {
        val tracks = contents.map { it.toTrack() }
        val first = tracks.getOrNull(index) ?: return
        viewModelScope.launch {
            val queueName = nameRes?.let { getString(it) } ?: name
            setQueueData(
                QueueData.Data(
                    listTracks = tracks,
                    firstPlayedTrack = first,
                    playlistId = playlistId,
                    playlistName = queueName,
                    playlistType = PlaylistType.RADIO,
                    continuation = null,
                ),
            )
            loadMediaItem(first, Config.SONG_CLICK)
        }
    }

    /** FM 垃圾桶:乐观移除卡片 → 服务端标记不感兴趣 → 响应补位歌或补拉一批插回原位。
     *  补位拿不到就只缩短列表(下拉刷新会拉全新批)。 */
    fun trashFm(content: Content) {
        val videoId = content.videoId ?: return
        viewModelScope.launch {
            val ready = _state.value as? State.Ready ?: return@launch
            val idx = ready.fmContents.indexOfFirst { it.videoId == videoId }
            if (idx == -1) return@launch
            val shrunk = ready.fmContents.toMutableList().apply { removeAt(idx) }
            _state.value = ready.copy(fmContents = shrunk)

            val seen = shrunk.mapNotNull { it.videoId }.toSet()
            val replacement =
                neteaseRepository.trashFmSong(videoId).getOrNull()
                    ?: neteaseRepository.fetchMoreFmContents(seen).firstOrNull()
            if (replacement != null) {
                (_state.value as? State.Ready)?.let { current ->
                    val stillAbsent = replacement.videoId !in current.fmContents.mapNotNull { it.videoId }.toSet()
                    if (stillAbsent) {
                        val inserted = current.fmContents.toMutableList().apply { add(idx.coerceAtMost(size), replacement) }
                        _state.value = current.copy(fmContents = inserted)
                    }
                }
            }
        }
    }
}
