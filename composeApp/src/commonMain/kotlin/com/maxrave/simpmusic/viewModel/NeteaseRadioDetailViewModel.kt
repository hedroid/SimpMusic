package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.viewModelScope
import com.maxrave.domain.repository.SongRepository
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
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
    private val dataStoreManager: com.maxrave.domain.manager.DataStoreManager,
    private val songRepository: SongRepository,
) : BaseViewModel() {
    data class UiState(
        val radio: NeteaseDjRadio? = null,
        val programs: List<NeteaseDjProgram> = emptyList(),
        /** 服务端游标:已消费的原始节目数(含不可播)。显示列表过滤后与 offset 必须解耦——
         *  用过滤后 programs.size 当 offset,前页存在不可播节目就会重取旧页,去重后 fresh
         *  恒空把 hasMore 错杀成 false,后续节目永久不可达(CR-25;core 续页同按原始页大小推进) */
        val rawProgramCount: Int = 0,
        val loading: Boolean = true,
        val loadingMore: Boolean = false,
        val hasMore: Boolean = false,
        val subInFlight: Boolean = false,
        /** 非 null=节目列表拉取失败——byradio 连续调用会被网易限流(HTTP 200+空数据或
         *  code=405"操作频繁",同一电台先空后有即此),网络抖动同走这里;UI 给重试不冒充空态 */
        val programsUnavailable: Boolean = false,
        /** 节目排序:false=最新在前(默认,byradio asc=false)/true=最早在前(asc=true) */
        val ascending: Boolean = false,
        /** 上次收听记忆(programId+已播毫秒,持久化;队列清空后回页仍可续播,官方回听同款) */
        val resumeProgramId: Long? = null,
        val resumePositionMs: Long = 0L,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> get() = _uiState.asStateFlow()

    /** 当前电台 id(StateFlow 化:radioPlayback 的 combine 源,避免可变 var 闭包取旧值) */
    private val radioIdFlow = MutableStateFlow(0L)
    private var radioId: Long
        get() = radioIdFlow.value
        set(value) {
            radioIdFlow.value = value
        }

    /** 本电台当前播放态(null=播放队列不是本电台):驱动 播放全部/继续播放/暂停 按钮三态 */
    data class RadioPlayback(val isPlaying: Boolean)

    val radioPlayback: kotlinx.coroutines.flow.StateFlow<RadioPlayback?> =
        kotlinx.coroutines.flow.combine(
            radioIdFlow,
            mediaPlayerHandler.queueData,
            mediaPlayerHandler.controlState,
        ) { id, q, c ->
            if (id != 0L && q?.data?.playlistId == "NETEASE_PODCAST_RADIO_$id") {
                RadioPlayback(c.isPlaying)
            } else {
                null
            }
        }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), null)

    init {
        // 本电台播放中周期落盘收听记忆(10s 粒度;页面关闭/进程死都最多丢 10s)。
        // 判定直接读 handler 三个 StateFlow.value,不走 radioPlayback(它是 WhileSubscribed,
        // 页面退到后台停收集变 null,记忆就断——实测踩过)
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10_000)
                val id = radioId
                if (id == 0L) continue
                val q = mediaPlayerHandler.queueData.value
                if (q?.data?.playlistId != "NETEASE_PODCAST_RADIO_$id") continue
                val c = mediaPlayerHandler.controlState.value
                if (!c.isPlaying) continue
                // track 在部分转场时机为 null(播放页同款回退 songEntity);Ready 只是装载完成
                // 一瞬,稳态是 Progress/Buffering——判"在播"族即可
                val nowTrack = mediaPlayerHandler.nowPlayingState.value
                val vid = nowTrack.track?.videoId ?: nowTrack.songEntity?.videoId
                // programId 优先从队列 Track O(1) 直读(播客构造 Track 时携带,core 续页
                // 追加的节目同样有)——详情 VM 的 programs 只到自己已载的页,播放器续到
                // 第 2 页以后旧反查永远 miss,记忆就断(CR-26);track 缺槽再回退列表反查
                val pid =
                    nowTrack.track?.neteaseProgramId
                        ?: _uiState.value.programs.firstOrNull { it.mainSongId?.toString() == vid }?.id
                val state = mediaPlayerHandler.simpleMediaState.value
                val active = state is com.maxrave.domain.mediaservice.handler.SimpleMediaState.Progress ||
                    state is com.maxrave.domain.mediaservice.handler.SimpleMediaState.Ready ||
                    state is com.maxrave.domain.mediaservice.handler.SimpleMediaState.Buffering
                if (pid != null && active) {
                    val pos = mediaPlayerHandler.player.currentPosition
                    dataStoreManager.putString("podcast_resume_$id", "$pid|$pos")
                }
            }
        }
    }

    /** 头部按钮:非本队=播放全部(当前列表顺序整队起播);本队=继续/暂停(PlayPause 翻转) */
    fun playAllOrResume() {
        val playback = radioPlayback.value
        when {
            playback == null -> playFrom(0)
            else -> viewModelScope.launch { mediaPlayerHandler.onPlayerEvent(com.maxrave.domain.mediaservice.handler.PlayerEvent.PlayPause) }
        }
    }

    /** 节目列表加载链(首页/续页共用单飞)+代数:换电台/切排序递增 generation,在途回包
     *  按代丢弃——切排序时旧升/降序的首页或续页晚到,会把另一序的节目追加进新列表
     *  (乱序+重复+rawProgramCount/hasMore 错位,五轮 CR) */
    private var programsJob: kotlinx.coroutines.Job? = null
    private var programsGeneration = 0L

    fun load(
        id: Long,
        navName: String = "",
    ) {
        if (radioId == id && !_uiState.value.loading) return
        programsGeneration++
        programsJob?.cancel()
        radioId = id
        _uiState.update { it.copy(loading = true, programsUnavailable = false) }
        // 读该电台的上次收听记忆("programId|positionMs"单键)
        viewModelScope.launch {
            val saved = dataStoreManager.getString("podcast_resume_$id").first()
            saved?.split("|")?.let { parts ->
                val pid = parts.getOrNull(0)?.toLongOrNull()
                val pos = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                if (pid != null) _uiState.update { it.copy(resumeProgramId = pid, resumePositionMs = pos) }
            }
        }
        viewModelScope.launch {
            // 串行:节目"空且电台声明有节目=不可用"的判定依赖详情的 programCount,
            // 并行会有详情未回(declared=0)的误判窗口
            neteaseRepository.getDjRadioDetail(id).fold(
                onSuccess = { radio -> _uiState.update { it.copy(radio = radio) } },
                onFailure = {
                    log("radio detail failed: $it")
                    // 下架/版权到期电台 /djradio/get 回 code=401 无 djRadio 字段(2026-10-02
                    // 探针实证,凯叔讲西游记第一部),radio=null 会让 header 整块消失。
                    // 订阅列表 /djradio/get/subed 对下架电台形状仍健康——按 id 兜底
                    // (能订阅到列表里的必然已订阅,subed 钉 true);未订阅命中不了时用
                    // 导航传入的电台名构造最小实体,保住 header 与播放按钮
                    val fallback =
                        neteaseRepository.getMyDjRadios().getOrNull()
                            ?.firstOrNull { it.id == id }
                            ?.copy(subed = true)
                            ?: NeteaseDjRadio(
                                id = id,
                                name = navName,
                                coverUrl = null,
                                programCount = 0,
                                djNickname = null,
                            )
                    _uiState.update { it.copy(radio = fallback) }
                },
            )
            loadPrograms()
        }
    }

    /** 重试(不可用态/失败的入口);成功空响应=真没内容(列表端点是权威),不走这里 */
    fun retry() {
        _uiState.update { it.copy(loading = true, programsUnavailable = false) }
        loadPrograms()
    }

    /** 切换 最新在前/最早在前(byradio asc 参数),清空重拉第一页 */
    fun setAscending(ascending: Boolean) {
        if (_uiState.value.ascending == ascending) return
        _uiState.update { it.copy(ascending = ascending, programs = emptyList(), rawProgramCount = 0, hasMore = false, loading = true, programsUnavailable = false) }
        loadPrograms()
    }

    private fun loadPrograms() {
        programsJob?.cancel()
        val gen = ++programsGeneration
        val radioIdAtStart = radioId
        val ascendingAtStart = _uiState.value.ascending
        programsJob =
            viewModelScope.launch {
                neteaseRepository.getDjRadioProgramsPage(radioIdAtStart, offset = 0, asc = ascendingAtStart).fold(
                    onSuccess = { (programs, more) ->
                        // 换电台/切排序后到达的旧回包整包丢弃
                        if (gen != programsGeneration || radioId != radioIdAtStart) return@fold
                        // 不可播节目(mainSong 缺失)不进列表,显示列表==可播列表,下标对齐;
                        // 原始页大小进 rawProgramCount 当服务端游标。
                        // 列表端点的成功空响应=内容权威,直接"暂无节目"(2026-10-07 探针:
                        // 单田芳评书详情 programCount=100 是下架删内容后的脏元数据,byradio
                        // 200+count=0 稳定空;旧"声明>0 且空=限流"判定把这种电台永久误显
                        // "拉取失败")。风控两种形态:405 已在端点层转 Result.failure 走
                        // onFailure;"200+空数据"形态由 repo 层 800ms 串行间隔防御
                        val playable = programs.filter { it.mainSongId != null }
                        _uiState.update {
                            it.copy(
                                programs = playable,
                                rawProgramCount = programs.size,
                                hasMore = more,
                                loading = false,
                                programsUnavailable = false,
                            )
                        }
                    },
                    onFailure = {
                        if (gen != programsGeneration || radioId != radioIdAtStart) return@fold
                        log("radio programs failed: $it")
                        _uiState.update { it.copy(loading = false, programsUnavailable = true) }
                    },
                )
            }
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.hasMore || state.loadingMore || state.loading) return
        _uiState.update { it.copy(loadingMore = true) }
        val gen = programsGeneration
        val radioIdAtStart = radioId
        programsJob?.cancel()
        programsJob =
            viewModelScope.launch {
                neteaseRepository
                    .getDjRadioProgramsPage(radioIdAtStart, offset = state.rawProgramCount, asc = state.ascending)
                    .fold(
                        onSuccess = { (programs, more) ->
                            if (gen != programsGeneration || radioId != radioIdAtStart) return@fold
                            val playable = programs.filter { it.mainSongId != null }
                            _uiState.update { current ->
                                // 整页撞重=服务端重复发批,收尾防 offset 死循环(SimilarSongs 同款)
                                val fresh = playable.filterNot { p -> current.programs.any { it.id == p.id } }
                                current.copy(
                                    programs = current.programs + fresh,
                                    // 游标按原始页大小推进,不可播节目也占服务端 offset
                                    rawProgramCount = current.rawProgramCount + programs.size,
                                    hasMore = more && fresh.isNotEmpty(),
                                    loadingMore = false,
                                )
                            }
                        },
                        onFailure = {
                            if (gen != programsGeneration || radioId != radioIdAtStart) return@fold
                            _uiState.update { it.copy(loadingMore = false) }
                        },
                    )
        }
    }

    /** 头部"继续播放":跳到记忆节目+seek 到记忆位置(队列不在本台时) */
    fun resumePlayback() {
        val pid = _uiState.value.resumeProgramId ?: return
        val idx = _uiState.value.programs.indexOfFirst { it.id == pid }
        if (idx >= 0) {
            playFrom(idx, _uiState.value.resumePositionMs)
        } else {
            playFrom(0)
        }
    }

    /** 点节目=从点击处整队起播;startMs 供续播跳到上次位置。
     *  队列只带组队时已载的节目(通常第一页 30 条),还有未载页时发续页令牌
     *  (POD{A|D}_<offset>,A=最早在前)——队列页近底/下拉由 core 续本台下一页,播完即止 */
    fun playFrom(
        index: Int,
        startMs: Long = 0L,
    ) {
        val state = _uiState.value
        val programs = state.programs.mapNotNull { it.toResultSong() }
        // 播客归类回填(2026-10-01):updateCatalog 落库是 INSERT IGNORE,旧行(v32 前建的
        // song 行没有 neteaseProgramId 列值)不会被覆盖——起播时显式 UPDATE,让下载页
        // 播客 tab 能识别历史行(每行一条轻量 SQL,只补 null)
        viewModelScope.launch {
            programs.forEach { rs ->
                if (rs.videoId.toLongOrNull() != null) {
                    songRepository.updateNeteaseProgramIdIfNull(rs.videoId, rs.neteaseProgramId)
                }
            }
        }
        val tracks = programs.map { it.toTrack() }
        val first = tracks.getOrNull(index) ?: return
        setQueueData(
            QueueData.Data(
                listTracks = ArrayList(tracks),
                firstPlayedTrack = first,
                playlistId = "NETEASE_PODCAST_RADIO_$radioId",
                playlistName = state.radio?.name ?: "",
                playlistType = PlaylistType.PLAYLIST,
                continuation =
                    if (state.hasMore) {
                        // 令牌 offset=服务端游标(原始节目数,含不可播),core 续页从该 offset 起拉
                        "POD${if (state.ascending) 'A' else 'D'}_${state.rawProgramCount}"
                    } else {
                        null
                    },
            ),
        )
        sharedViewModel.loadMediaItemFromTrack(first, Config.PLAYLIST_CLICK, index)
        if (startMs > 0) {
            // 等 READY 再 seek(装载中 seek 无效);首曲即目标节目(index 定位保证)
            viewModelScope.launch {
                kotlinx.coroutines.withTimeoutOrNull(8000) {
                    mediaPlayerHandler.simpleMediaState.first { it is com.maxrave.domain.mediaservice.handler.SimpleMediaState.Ready }
                }
                mediaPlayerHandler.player.seekTo(startMs)
            }
        }
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
