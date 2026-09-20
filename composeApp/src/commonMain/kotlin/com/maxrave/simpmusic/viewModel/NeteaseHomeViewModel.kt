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
import com.maxrave.domain.data.model.searchResult.albums.AlbumsResult
import com.maxrave.domain.data.model.searchResult.artists.ArtistsResult
import com.maxrave.domain.utils.toTrack
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 网易主页独立 ViewModel(行级懒加载版):页面立即渲染,每行独立状态
 * (Loading 占位 → 滚到可见才拉取 → Ready/Failed),不再整页等全量。
 * 数据全部来自 [NeteaseRepositoryImpl],各行走自己的 10min 会话缓存
 * (命中即返回、miss 才网络);下拉刷新把全部行重置 Loading 并 force 绕过缓存。
 */
class NeteaseHomeViewModel(
    private val neteaseRepository: NeteaseRepositoryImpl,
) : BaseViewModel() {
    /** 主页行(渲染顺序即枚举顺序) */
    enum class Row(
        val title: String,
    ) {
        DAILY("每日推荐歌单"),
        RADAR_SONGS("私人雷达"),
        RADAR_PLAYLISTS("雷达歌单"),
        HQ("精品歌单"),
        NEW_SONGS("推荐新歌"),
        CHART("排行榜"),
        NEW_ALBUMS("新碟上架"),
        SECTIONS("分类"),
        // 对齐 YTM:热门歌手(艺人榜)是主页最后一行
        TOP_ARTISTS("热门歌手"),
    }

    /** 行内容(Feed=歌单/歌曲行复用 HomeItem 形状) */
    sealed interface RowContent {
        data class Feed(val item: HomeItem) : RowContent

        data class ChartRow(val chart: Chart) : RowContent

        data class ArtistsRow(val list: List<ArtistsResult>) : RowContent

        data class AlbumsRow(val list: List<AlbumsResult>) : RowContent

        data class SectionsRow(val mood: Mood) : RowContent
    }

    sealed interface RowUi {
        data object Loading : RowUi

        data class Ready(val content: RowContent) : RowUi

        data object Failed : RowUi
    }

    data class ReadyState(
        val rows: Map<Row, RowUi>,
        /** 新碟上架当前地区(ALL/ZH/EA/KR/JP,chips 选中态) */
        val newAlbumsArea: String = "ALL",
    )

    private val _state = MutableStateFlow<ReadyState>(
        ReadyState(rows = Row.entries.associateWith { RowUi.Loading }),
    )
    val state: StateFlow<ReadyState> = _state.asStateFlow()

    /** 账户摘要(欢迎区),未登录 null */
    private val _accountInfo = MutableStateFlow<Pair<String?, String?>?>(null)
    val accountInfo: StateFlow<Pair<String?, String?>?> = _accountInfo.asStateFlow()

    /** 顶栏 chips:固定 8 个高频分类快捷 */
    val chips: List<String> = neteaseRepository.curatedHomeTags

    /** 下拉刷新进行中(顶部指示器):已就绪行的后台重拉尚未全部落地 */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** 进行中的行,防占位重组重复发请求 */
    private val inFlightRows = mutableSetOf<Row>()

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
    }

    /** 下拉刷新(静默):已就绪的行**保持内容不回占位**,后台 force 重拉(绕过 10min 行缓存)、
     *  落地即原位替换;失败行回 Loading 走重试;从未加载的行维持原状,滚到才拉。
     *  指示器只陪跑到**第一批**重拉落地——各行并行重拉,最慢的行(分类目录/排行榜这类
     *  多接口聚合)能到 10s+,指示器全程陪跑就是"顶端转圈转好多圈"的观感;收起后其余行
     *  继续静默原位替换。曾经的实现是"全部行重置 Loading、各行转圈",已废弃。 */
    fun refresh() {
        if (!_refreshing.compareAndSet(expect = false, update = true)) return
        val snapshot = _state.value.rows
        viewModelScope.launch {
            var firstLanded = false
            snapshot.filterValues { it is RowUi.Failed }.keys.forEach { row ->
                _state.update { it.copy(rows = it.rows + (row to RowUi.Loading)) }
            }
            coroutineScope {
                snapshot.forEach { (row, ui) ->
                    when (ui) {
                        is RowUi.Ready ->
                            launch {
                                val fresh = loadRow(row, force = true)
                                _state.update { it.copy(rows = it.rows + (row to fresh)) }
                                if (!firstLanded) {
                                    firstLanded = true
                                    _refreshing.value = false
                                }
                            }
                        is RowUi.Failed -> launch { ensureRowLoaded(row) }
                        RowUi.Loading -> {}
                    }
                }
            }
            _refreshing.value = false
        }
    }

    /** 行进入可视区(Loading 占位被组合)时调用:幂等,进行中/已就绪不重发;行走行缓存 */
    fun ensureRowLoaded(row: Row) {
        val current = (_state.value.rows[row]) ?: return
        if (current !is RowUi.Loading) return
        synchronized(inFlightRows) {
            if (!inFlightRows.add(row)) return
        }
        viewModelScope.launch {
            // update{} 是原子读改写,并行完成的行各自结果都保留。这里曾是
            // "_state.value = ReadyState(_state.value.rows + …)" 的先读后写:并行行完成撞车,
            // 后写者把先写者刚落地的 Ready 覆盖回 Loading,该行占位重组后再次拉取再次转圈。
            val ui = loadRow(row, force = false)
            _state.update { it.copy(rows = it.rows + (row to ui)) }
            synchronized(inFlightRows) { inFlightRows.remove(row) }
        }
    }

    /** 新碟上架切地区:行回占位即止——Loading 槽位重组会让 ensureRowLoaded 接管加载
     *  (loadRow 的 NEW_ALBUMS 分支读的是 state.newAlbumsArea,已切到新地区)。
     *  别在这里再手动 launch 拉一次,否则和槽位触发并发成双请求(回归时实测过)。 */
    fun loadNewAlbumsArea(area: String) {
        if (area == _state.value.newAlbumsArea) return
        _state.update {
            it.copy(
                rows = it.rows + (Row.NEW_ALBUMS to RowUi.Loading),
                newAlbumsArea = area,
            )
        }
    }

    /** 失败行点重试:回 Loading 再走正常加载 */
    fun retryRow(row: Row) {
        _state.update { it.copy(rows = it.rows + (row to RowUi.Loading)) }
        ensureRowLoaded(row)
    }

    private suspend fun loadRow(
        row: Row,
        force: Boolean,
    ): RowUi =
        when (row) {
            Row.DAILY -> neteaseRepository.getDailyPlaylistsRow(force)?.let { RowUi.Ready(RowContent.Feed(it)) } ?: RowUi.Failed
            Row.RADAR_SONGS -> neteaseRepository.getRadarSongsRow(force)?.let { RowUi.Ready(RowContent.Feed(it)) } ?: RowUi.Failed
            Row.RADAR_PLAYLISTS -> neteaseRepository.getRadarPlaylistsRow(force)?.let { RowUi.Ready(RowContent.Feed(it)) } ?: RowUi.Failed
            Row.HQ -> neteaseRepository.getHqPlaylistsRow(force)?.let { RowUi.Ready(RowContent.Feed(it)) } ?: RowUi.Failed
            Row.NEW_SONGS -> neteaseRepository.getNewSongsRow(force)?.let { RowUi.Ready(RowContent.Feed(it)) } ?: RowUi.Failed
            Row.CHART ->
                neteaseRepository.getHomeChart().getOrNull()
                    ?.let { RowUi.Ready(RowContent.ChartRow(it)) } ?: RowUi.Failed
            Row.TOP_ARTISTS ->
                neteaseRepository.getTopArtists(force = force).getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { RowUi.Ready(RowContent.ArtistsRow(it)) } ?: RowUi.Failed
            Row.NEW_ALBUMS ->
                neteaseRepository.getNewAlbums(area = _state.value.newAlbumsArea, force = force).getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { RowUi.Ready(RowContent.AlbumsRow(it)) } ?: RowUi.Failed
            Row.SECTIONS ->
                neteaseRepository.getMoodSections().getOrNull()
                    ?.let { RowUi.Ready(RowContent.SectionsRow(it)) } ?: RowUi.Failed
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
