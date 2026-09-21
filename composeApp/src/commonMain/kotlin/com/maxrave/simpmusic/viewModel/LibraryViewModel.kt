package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.viewModelScope
import com.maxrave.common.Config
import com.maxrave.common.LibraryChipType
import com.maxrave.data.repository.NeteaseRepositoryImpl
import com.maxrave.domain.data.entities.AlbumEntity
import com.maxrave.domain.data.entities.DownloadState.STATE_NOT_DOWNLOADED
import com.maxrave.domain.data.entities.LocalPlaylistEntity
import com.maxrave.domain.data.entities.PlaylistEntity
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.data.model.searchResult.albums.AlbumsResult
import com.maxrave.domain.data.model.searchResult.artists.ArtistsResult
import com.maxrave.domain.data.model.searchResult.playlists.PlaylistsResult
import com.maxrave.domain.data.model.searchResult.songs.Thumbnail
import com.maxrave.domain.data.type.ChartItem
import com.maxrave.domain.data.type.MonthlyRecapItem
import com.maxrave.domain.data.type.PlaylistType
import com.maxrave.domain.data.type.RecentlyType
import com.maxrave.domain.extension.now
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.handler.DownloadHandler
import com.maxrave.domain.repository.AlbumRepository
import com.maxrave.domain.repository.AccountRepository
import com.maxrave.domain.repository.AnalyticsRepository
import com.maxrave.domain.repository.ArtistRepository
import com.maxrave.domain.repository.CommonRepository
import com.maxrave.domain.repository.LocalPlaylistRepository
import com.maxrave.domain.repository.PlaylistRepository
import com.maxrave.domain.repository.PodcastRepository
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.utils.LocalResource
import com.maxrave.domain.utils.Resource
import com.maxrave.domain.utils.isRadioPlaylistId
import com.maxrave.simpmusic.ui.screen.home.analytics.monthFullNameResource
import com.maxrave.simpmusic.ui.screen.library.LibraryDynamicPlaylistType
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import com.maxrave.simpmusic.viewModel.base.removeExclusiveTrackDownloads
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import org.koin.core.component.inject
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.added_local_playlist
import simpmusic.composeapp.generated.resources.could_not_create_playlist
import simpmusic.composeapp.generated.resources.created_playlist
import simpmusic.composeapp.generated.resources.netease_action_failed
import simpmusic.composeapp.generated.resources.unsubscribed_netease_album
import simpmusic.composeapp.generated.resources.unsubscribed_netease_playlist
import simpmusic.composeapp.generated.resources.unsubscribed_youtube_playlist
import simpmusic.composeapp.generated.resources.deleted_playlist
import simpmusic.composeapp.generated.resources.removed_download
import simpmusic.composeapp.generated.resources.wrapped_recap_month
import simpmusic.composeapp.generated.resources.wrapped_recap_month_year
import simpmusic.composeapp.generated.resources.youtube_liked_music

class LibraryViewModel(
    private val dataStoreManager: DataStoreManager,
    private val analyticsRepository: AnalyticsRepository,
    private val songRepository: SongRepository,
    private val commonRepository: CommonRepository,
    private val playlistRepository: PlaylistRepository,
    private val localPlaylistRepository: LocalPlaylistRepository,
    private val albumRepository: AlbumRepository,
    private val podcastRepository: PodcastRepository,
    private val neteaseRepository: NeteaseRepositoryImpl,
    private val artistRepository: ArtistRepository,
    private val accountRepository: AccountRepository,
) : BaseViewModel() {
    private val downloadUtils: DownloadHandler by inject<DownloadHandler>()

    private val _currentScreen: MutableStateFlow<LibraryChipType> = MutableStateFlow(LibraryChipType.YOUR_LIBRARY)
    val currentScreen: StateFlow<LibraryChipType> get() = _currentScreen.asStateFlow()
    private val _recentlyAdded: MutableStateFlow<LocalResource<List<RecentlyType>>> =
        MutableStateFlow(LocalResource.Loading())
    val recentlyAdded: StateFlow<LocalResource<List<RecentlyType>>> get() = _recentlyAdded.asStateFlow()

    private val _yourLocalPlaylist: MutableStateFlow<LocalResource<List<LocalPlaylistEntity>>> =
        MutableStateFlow(LocalResource.Loading())
    val yourLocalPlaylist: StateFlow<LocalResource<List<LocalPlaylistEntity>>> get() = _yourLocalPlaylist.asStateFlow()

    private val _youTubePlaylist: MutableStateFlow<LocalResource<List<PlaylistsResult>>> =
        MutableStateFlow(LocalResource.Loading())
    val youTubePlaylist: StateFlow<LocalResource<List<PlaylistsResult>>> get() = _youTubePlaylist.asStateFlow()

    // ------------------------------------------------ "您的 YouTube Music"tab(三分区,并行独立降级,结构镜像"您的网易云")

    /** 收藏的专辑(YT 云端 FEmusic_liked_albums) */
    private val _youTubeAlbums: MutableStateFlow<LocalResource<List<AlbumsResult>>> =
        MutableStateFlow(LocalResource.Loading())
    val youTubeAlbums: StateFlow<LocalResource<List<AlbumsResult>>> get() = _youTubeAlbums.asStateFlow()

    /** 关注的歌手(本地关注表镜像,YT 关注按钮本地+云端双写;只取 YT 艺人,数字 id=网易) */
    private val _followedYTArtists: MutableStateFlow<LocalResource<List<ArtistsResult>>> =
        MutableStateFlow(LocalResource.Loading())
    val followedYTArtists: StateFlow<LocalResource<List<ArtistsResult>>> get() = _followedYTArtists.asStateFlow()

    /** 收藏的他人歌单(YT 库歌单分区,tab2) */
    private val _youTubeLikedPlaylists: MutableStateFlow<LocalResource<List<PlaylistsResult>>> =
        MutableStateFlow(LocalResource.Loading())
    val youTubeLikedPlaylists: StateFlow<LocalResource<List<PlaylistsResult>>> get() = _youTubeLikedPlaylists.asStateFlow()

    /** 系统歌单(红心歌单 Liked Music 等),置顶满行展示 */
    private val _youTubeAutoPlaylists: MutableStateFlow<LocalResource<List<PlaylistsResult>>> =
        MutableStateFlow(LocalResource.Loading())
    val youTubeAutoPlaylists: StateFlow<LocalResource<List<PlaylistsResult>>> get() = _youTubeAutoPlaylists.asStateFlow()

    /** YT tab 静默刷新指示(已有数据时的下拉/返回刷新,镜像 neteaseRefreshing 语义) */
    private val _youTubeRefreshing = MutableStateFlow(false)
    val youTubeRefreshing: StateFlow<Boolean> get() = _youTubeRefreshing.asStateFlow()

    private val _youTubeMixForYou: MutableStateFlow<LocalResource<List<PlaylistsResult>>> =
        MutableStateFlow(LocalResource.Loading())
    val youTubeMixForYou: StateFlow<LocalResource<List<PlaylistsResult>>> get() = _youTubeMixForYou.asStateFlow()

    private val _favoritePlaylist: MutableStateFlow<LocalResource<List<PlaylistType>>> =
        MutableStateFlow(LocalResource.Loading())
    val favoritePlaylist: StateFlow<LocalResource<List<PlaylistType>>> get() = _favoritePlaylist.asStateFlow()

    private val _favoritePodcasts: MutableStateFlow<LocalResource<List<PlaylistType>>> =
        MutableStateFlow(LocalResource.Loading())
    val favoritePodcasts: StateFlow<LocalResource<List<PlaylistType>>> get() = _favoritePodcasts.asStateFlow()

    private val _downloadedPlaylist: MutableStateFlow<LocalResource<List<PlaylistType>>> =
        MutableStateFlow(LocalResource.Loading())
    val downloadedPlaylist: StateFlow<LocalResource<List<PlaylistType>>> get() = _downloadedPlaylist.asStateFlow()

    private val _chartPlaylists: MutableStateFlow<LocalResource<List<ChartItem>>> =
        MutableStateFlow(LocalResource.Loading())
    val chartPlaylists: StateFlow<LocalResource<List<ChartItem>>> get() = _chartPlaylists.asStateFlow()

    private val _listCanvasSong: MutableStateFlow<LocalResource<List<SongEntity>>> =
        MutableStateFlow(LocalResource.Loading())
    val listCanvasSong: StateFlow<LocalResource<List<SongEntity>>> get() = _listCanvasSong.asStateFlow()

    /**
     * The months the Wrapped tab offers a recap for, newest first.
     *
     * A [MonthlyRecapItem] rather than the destination's own
     * [LibraryDynamicPlaylistType.MonthlyRecap]: the tab draws these through the shared
     * `GridLibraryPlaylist`, which renders only [PlaylistType]s, and a tile needs a title and a
     * cover on top of the year and month the destination carries. The destination is rebuilt from
     * the year and month when a tile is tapped.
     */
    private val _monthlyRecaps: MutableStateFlow<LocalResource<List<MonthlyRecapItem>>> =
        MutableStateFlow(LocalResource.Loading())
    val monthlyRecaps: StateFlow<LocalResource<List<MonthlyRecapItem>>> get() = _monthlyRecaps.asStateFlow()

    private val _accountThumbnail: MutableStateFlow<String?> = MutableStateFlow(null)
    val accountThumbnail: StateFlow<String?> get() = _accountThumbnail.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val youtubeLoggedIn = dataStoreManager.loggedIn.mapLatest { it == DataStoreManager.TRUE }

    /** 网易云登录态(MUSIC_U cookie 存在与否),门控"您的网易云"chip 与登出回落 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val neteaseLoggedIn = dataStoreManager.neteaseCookie.mapLatest { it.isNotEmpty() }

    // ------------------------------------------------ "您的网易云"tab(三分区,并行独立降级)

    /** 歌单(红心歌单固定首位,repo 已按 specialType 稳定排序) */
    private val _neteasePlaylist: MutableStateFlow<LocalResource<List<PlaylistEntity>>> =
        MutableStateFlow(LocalResource.Loading())
    val neteasePlaylist: StateFlow<LocalResource<List<PlaylistEntity>>> get() = _neteasePlaylist.asStateFlow()

    /** 关注的歌手(/artist/sublist,repo 自带 10min 行缓存) */
    private val _subscribedArtists: MutableStateFlow<LocalResource<List<ArtistsResult>>> =
        MutableStateFlow(LocalResource.Loading())
    val subscribedArtists: StateFlow<LocalResource<List<ArtistsResult>>> get() = _subscribedArtists.asStateFlow()

    /** 收藏的专辑(/mine/rn/resource/list,repo 自带 10min 行缓存) */
    private val _starredAlbums: MutableStateFlow<LocalResource<List<AlbumsResult>>> =
        MutableStateFlow(LocalResource.Loading())
    val starredAlbums: StateFlow<LocalResource<List<AlbumsResult>>> get() = _starredAlbums.asStateFlow()

    /**
     * 刷新指示器独立于分区状态:下拉刷新不清空已显示的分区(原地替换),
     * 只有首拉(无数据)才让分区进 Loading 占整页 spinner。
     */
    private val _neteaseRefreshing = MutableStateFlow(false)
    val neteaseRefreshing: StateFlow<Boolean> get() = _neteaseRefreshing.asStateFlow()

    /** 自建网易歌单 ID 集(creatorId==账号 uid);长按"取消收藏"只对收藏歌单露出,自建的不能误删 */
    private val _ownNeteasePlaylistIds = MutableStateFlow<Set<String>>(emptySet())
    val ownNeteasePlaylistIds: StateFlow<Set<String>> get() = _ownNeteasePlaylistIds.asStateFlow()

    /** 红心歌单 id("我喜欢的音乐",不可删除);长按入口排除 */
    private val _neteaseLikedPlaylistId = MutableStateFlow<String?>(null)
    val neteaseLikedPlaylistId: StateFlow<String?> get() = _neteaseLikedPlaylistId.asStateFlow()

    /**
     * Whether the Wrapped chip has anything behind it.
     *
     * The same setting the Analytics tab follows, read the same way — Wrapped and the recaps are
     * built entirely from `playback_event`, which local tracking is what fills.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val localTrackingEnabled = dataStoreManager.localTrackingEnabled.mapLatest { it == DataStoreManager.TRUE }

    init {
        viewModelScope.launch {
            val currentScreenJob =
                launch {
                    // 进库默认选第一个可见 chip(2026-09-20 定序:网易云 → YT → 排行榜)。
                    // 不再恢复持久化选中——持久化值没有其它读者,选中记忆与"点库回到
                    // 第一个分区"的需求冲突;运行中登出网易的回落由下面的 collect 兜底。
                    setCurrentScreen(defaultLibraryChip())
                }
            val accountThumbJob =
                launch {
                    // Live-observe the key instead of a one-shot read on cookie change, so
                    // repairs to the stored value (settings account sync, re-login) reach
                    // the Library avatar immediately without an app restart.
                    dataStoreManager
                        .getString("AccountThumbUrl")
                        .distinctUntilChanged()
                        .collect { thumb -> _accountThumbnail.value = thumb?.takeIf { it.isNotEmpty() } }
                }
            val accountHealJob =
                launch {
                    // AccountThumbUrl/AccountName were once overwritten by the YT home scrape
                    // with a mood shelf's header (title fragment + artwork), and a Netease-first
                    // user never re-fetches the YT home to repair them. The Room row was written
                    // at login from the account endpoint, so when the stored NAME disagrees with
                    // it the stored pair is polluted — re-assert the row. Name-only check keeps a
                    // legitimate scrape refresh (same name, upscaled thumb URL) from ping-ponging.
                    accountRepository.getUsedGoogleAccount().firstOrNull()?.let { used ->
                        if (used.thumbnailUrl.isNotEmpty() && dataStoreManager.getString("AccountName").first() != used.name) {
                            dataStoreManager.putString("AccountName", used.name)
                            dataStoreManager.putString("AccountThumbUrl", used.thumbnailUrl)
                        }
                    }
                }
            val neteaseLogoutJob =
                launch {
                    // 运行中登出网易:停在"您的网易云"tab 时弹回,并清数据防下次登入闪旧内容
                    dataStoreManager.neteaseCookie.distinctUntilChanged().collect { cookie ->
                        if (cookie.isEmpty()) {
                            if (_currentScreen.value == LibraryChipType.NETEASE_PLAYLIST) {
                                setCurrentScreen(defaultLibraryChip())
                            }
                            _neteasePlaylist.value = LocalResource.Loading()
                            _subscribedArtists.value = LocalResource.Loading()
                            _starredAlbums.value = LocalResource.Loading()
                        }
                    }
                }
            currentScreenJob.join()
            accountThumbJob.join()
            accountHealJob.join()
            neteaseLogoutJob.join()
        }
    }

    /**
     * 库页默认落点:网易登录 → "您的网易云";否则 YT 登录 → YT 歌单;再不然 → 排行榜。
     * ("您的库"chip 页已下线,旧持久化值/登出回落都改道到这里)
     */
    private suspend fun defaultLibraryChip(): LibraryChipType {
        // DataStore 首读偶发拿空(启动竞态):500ms 超时兜底,并把实际读值打进日志,下次
        // 再落到排行榜就知道是哪个分支判的
        val netease = kotlinx.coroutines.withTimeoutOrNull(500) { dataStoreManager.neteaseCookie.first() } ?: ""
        val yt = kotlinx.coroutines.withTimeoutOrNull(500) { dataStoreManager.cookie.first() } ?: ""
        com.maxrave.logger.Logger.w(
            "LibraryVM",
            "defaultLibraryChip: netease=${netease.length} yt=${yt.length} -> " +
                when {
                    netease.isNotEmpty() -> "NETEASE"
                    yt.isNotEmpty() -> "YOUTUBE"
                    else -> "CHART"
                },
        )
        return when {
            netease.isNotEmpty() -> LibraryChipType.NETEASE_PLAYLIST
            yt.isNotEmpty() -> LibraryChipType.YOUTUBE_MUSIC_PLAYLIST
            else -> LibraryChipType.CHART
        }
    }

    /** 不再以 chip 形式出现的页(持久化值命中即回落):"您的库"及其四个子页入口。 */
    private fun invisibleLibraryChips() =
        setOf(
            LibraryChipType.YOUR_LIBRARY,
            LibraryChipType.LOCAL_PLAYLIST,
            LibraryChipType.FAVORITE_PLAYLIST,
            LibraryChipType.FAVORITE_PODCAST,
        )

    fun setCurrentScreen(chipType: LibraryChipType) {
        _currentScreen.value = chipType
        viewModelScope.launch {
            dataStoreManager.putString("library_current_screen", chipType.toStringValue())
        }
    }

    fun getRecentlyAdded() {
        viewModelScope.launch {
            commonRepository.getAllRecentData().collectLatest { data ->
                val temp: MutableList<RecentlyType> = mutableListOf()
                temp.addAll(data)
                temp
                    .find {
                        it is PlaylistEntity && it.id.isRadioPlaylistId()
                    }.let {
                        temp.remove(it)
                    }
                temp.removeIf { it is SongEntity && it.inLibrary == Config.REMOVED_SONG_DATE_TIME }
                if (dataStoreManager.loggedIn.first() == DataStoreManager.TRUE) {
                    temp.removeIf { it is PlaylistEntity && it.id == "LM" }
                    temp.add(
                        PlaylistEntity(
                            title = getString(Res.string.youtube_liked_music),
                            author = "YouTube Music",
                            id = "LM",
                            description = "PIN",
                            thumbnails = "https://www.gstatic.com/youtube/media/ytm/images/pbg/liked-songs-delhi-1200.png",
                        ),
                    )
                }
                temp.reverse()
                _recentlyAdded.value = LocalResource.Success(temp.toImmutableList())
            }
        }
    }

    /**
     * "您的 YouTube Music"tab 三分区并行拉取:YouTube 歌单(云端)/收藏的专辑(云端)/
     * 关注的歌手(YT 订阅列表真源,adopt-on 回填本地关注位)。分区独立降级,一个失败只隐藏
     * 该分区;结构与"您的网易云"tab 对称。force=绕过艺人列表的 10min 缓存。
     *
     * 首次(五路全空)整页 Loading;已有数据时**静默刷新**——原地替换不清已显示分区,
     * 仅 [youTubeRefreshing] 驱动下拉指示器(语义与"您的网易云"一致)。从子页(歌单/歌手/
     * 播放页)返回本 tab 时静默 force 重拉,移除操作/红心曲目数无需手动下拉即可回写。
     */
    fun getYouTubeLibrary(force: Boolean = false) {
        val firstLoad =
            _youTubePlaylist.value.data == null &&
                _youTubeLikedPlaylists.value.data == null &&
                _youTubeAutoPlaylists.value.data == null &&
                _youTubeAlbums.value.data == null &&
                _followedYTArtists.value.data == null
        if (firstLoad) {
            _youTubePlaylist.value = LocalResource.Loading()
            _youTubeLikedPlaylists.value = LocalResource.Loading()
            _youTubeAutoPlaylists.value = LocalResource.Loading()
            _youTubeAlbums.value = LocalResource.Loading()
            _followedYTArtists.value = LocalResource.Loading()
        } else {
            _youTubeRefreshing.value = true
        }
        viewModelScope.launch {
            coroutineScope {
                launch {
                    playlistRepository.getLibraryPlaylistSplit().collect { split ->
                        // null=拉取失败/空库:静默刷新模式下保留已显示内容,失败清空会让
                        // 歌单分区闪没(重进本 tab 必拉,失败概率被放大)
                        if (split != null) {
                            // created 进主网格(加歌单弹窗也吃它:自建才能加);LM 等系统歌单置顶;他人歌单纯展示
                            _youTubePlaylist.value = LocalResource.Success(split.created)
                            _youTubeAutoPlaylists.value = LocalResource.Success(split.auto)
                            _youTubeLikedPlaylists.value = LocalResource.Success(split.liked)
                        }
                    }
                }
                launch {
                    playlistRepository.getLibraryAlbum().collect { data ->
                        if (data != null) {
                            _youTubeAlbums.value = LocalResource.Success(data)
                        }
                    }
                }
                launch {
                    artistRepository.getYouTubeLibraryArtists(force).collect { artists ->
                        if (artists != null) {
                            _followedYTArtists.value =
                                LocalResource.Success(
                                    artists
                                        .filter { it.channelId.toLongOrNull() == null }
                                        .map { entity ->
                                            ArtistsResult(
                                                artist = entity.name,
                                                browseId = entity.channelId,
                                                category = "",
                                                radioId = "",
                                                resultType = "artist",
                                                shuffleId = "",
                                                thumbnails =
                                                    listOfNotNull(
                                                        entity.thumbnails?.let {
                                                            Thumbnail(width = 560, height = 560, url = it)
                                                        },
                                                    ),
                                            )
                                        },
                                )
                        }
                    }
                }
            }
            _youTubeRefreshing.value = false
        }
    }

    /**
     * "您的网易云"tab 三分区并行拉取:歌单(红心置顶)/关注的歌手/收藏的专辑。
     * 分区独立降级——一个失败只隐藏该分区,不拖垮整页;[force] 透传给带行缓存的
     * 艺人/专辑两路(下拉刷新绕过缓存),歌单无缓存每次都拉。
     */
    fun getNeteaseLibrary(force: Boolean = false) {
        val firstLoad =
            _neteasePlaylist.value.data == null &&
                _subscribedArtists.value.data == null &&
                _starredAlbums.value.data == null
        if (firstLoad) {
            _neteasePlaylist.value = LocalResource.Loading()
            _subscribedArtists.value = LocalResource.Loading()
            _starredAlbums.value = LocalResource.Loading()
        }
        _neteaseRefreshing.value = true
        viewModelScope.launch {
            coroutineScope {
                launch {
                    neteaseRepository.getLibraryPlaylists().fold(
                        onSuccess = {
                            _neteasePlaylist.value = LocalResource.Success(it)
                            _ownNeteasePlaylistIds.value = neteaseRepository.getOwnNeteasePlaylistIds()
                            _neteaseLikedPlaylistId.value = neteaseRepository.getNeteaseLikedPlaylistIdCached()
                        },
                        onFailure = { _neteasePlaylist.value = LocalResource.Error(it.message ?: "netease playlists failed") },
                    )
                }
                launch {
                    neteaseRepository.getSubscribedArtists(force).fold(
                        onSuccess = { _subscribedArtists.value = LocalResource.Success(it.toList()) },
                        onFailure = { _subscribedArtists.value = LocalResource.Error(it.message ?: "subscribed artists failed") },
                    )
                }
                launch {
                    neteaseRepository.getStarredAlbums(force).fold(
                        onSuccess = { _starredAlbums.value = LocalResource.Success(it.toList()) },
                        onFailure = { _starredAlbums.value = LocalResource.Error(it.message ?: "starred albums failed") },
                    )
                }
            }
            _neteaseRefreshing.value = false
        }
    }

    /** 取消收藏网易歌单(/playlist/subscribe t=0),成功后 force 刷新"您的网易云"三分区 */
    fun unsubscribeNeteasePlaylist(playlistId: String) {
        viewModelScope.launch {
            neteaseRepository
                .subscribeNeteasePlaylist(playlistId, subscribe = false)
                .fold(
                    onSuccess = {
                        if (it) {
                            makeToast(getString(Res.string.unsubscribed_netease_playlist))
                            getNeteaseLibrary(force = true)
                        } else {
                            makeToast(getString(Res.string.netease_action_failed))
                        }
                    },
                    onFailure = { makeToast(getString(Res.string.netease_action_failed)) },
                )
        }
    }

    /** 删除自己的网易歌单(/playlist/delete,不可逆;UI 层已强确认),成功后 force 刷新三分区 */
    fun deleteNeteasePlaylist(playlistId: String) {
        viewModelScope.launch {
            neteaseRepository
                .deleteNeteasePlaylist(playlistId)
                .fold(
                    onSuccess = { ok ->
                        if (ok) {
                            makeToast(getString(Res.string.deleted_playlist))
                            getNeteaseLibrary(force = true)
                        } else {
                            makeToast(getString(Res.string.netease_action_failed))
                        }
                    },
                    onFailure = { makeToast(getString(Res.string.netease_action_failed)) },
                )
        }
    }

    /** 取消收藏网易专辑(/album/sub t=0),成功后 force 刷新"您的网易云"三分区 */
    fun unsubscribeNeteaseAlbum(albumId: String) {
        viewModelScope.launch {
            neteaseRepository
                .subscribeNeteaseAlbum(albumId, subscribe = false)
                .fold(
                    onSuccess = {
                        if (it) {
                            makeToast(getString(Res.string.unsubscribed_netease_album))
                            getNeteaseLibrary(force = true)
                        } else {
                            makeToast(getString(Res.string.netease_action_failed))
                        }
                    },
                    onFailure = { makeToast(getString(Res.string.netease_action_failed)) },
                )
        }
    }

    /** 库页"创建的歌单"分区新建入口(网易):建隐私歌单,成功 toast+静默刷新三分区 */
    fun createNeteasePlaylistInLibrary(name: String) {
        viewModelScope.launch {
            neteaseRepository
                .createNeteasePlaylist(name)
                .fold(
                    onSuccess = {
                        makeToast(getString(Res.string.created_playlist))
                        getNeteaseLibrary(force = true)
                    },
                    onFailure = { makeToast(getString(Res.string.could_not_create_playlist)) },
                )
        }
    }

    /** 库页"创建的歌单"分区新建入口(YT):建空歌单,成功 toast+静默刷新 */
    fun createYouTubePlaylistInLibrary(name: String) {
        viewModelScope.launch {
            val id = playlistRepository.createYouTubePlaylistWithTracks(name, emptyList())
            if (id != null) {
                makeToast(getString(Res.string.created_playlist))
                getYouTubeLibrary(force = true)
            } else {
                makeToast(getString(Res.string.could_not_create_playlist))
            }
        }
    }

    /** 取消收藏他人 YT 歌单(playlist/delete,服务端移出资料库),成功 toast+静默刷新 */
    fun unsubscribeYouTubePlaylist(playlistId: String) {
        viewModelScope.launch {
            if (playlistRepository.deleteYouTubePlaylist(playlistId)) {
                makeToast(getString(Res.string.unsubscribed_youtube_playlist))
                getYouTubeLibrary(force = true)
            } else {
                makeToast(getString(Res.string.netease_action_failed))
            }
        }
    }

    /** 删除自建 YT 歌单(playlist/delete,不可逆;UI 层已强确认),成功 toast+静默刷新 */
    fun deleteYouTubePlaylist(playlistId: String) {
        viewModelScope.launch {
            if (playlistRepository.deleteYouTubePlaylist(playlistId)) {
                makeToast(getString(Res.string.deleted_playlist))
                getYouTubeLibrary(force = true)
            } else {
                makeToast(getString(Res.string.netease_action_failed))
            }
        }
    }

    fun getYouTubeMixedForYou() {
        _youTubeMixForYou.value = LocalResource.Loading()
        viewModelScope.launch {
            playlistRepository.getMixedForYou().collect { data ->
                _youTubeMixForYou.value = LocalResource.Success(data ?: emptyList())
            }
        }
    }

    fun getYouTubeLoggedIn(): Boolean = runBlocking { dataStoreManager.loggedIn.first() } == DataStoreManager.TRUE

    fun getPlaylistFavorite() {
        viewModelScope.launch {
            albumRepository.getLikedAlbums().collect { album ->
                val temp: MutableList<PlaylistType> = mutableListOf()
                temp.addAll(album)
                playlistRepository.getLikedPlaylists().collect { playlist ->
                    temp.addAll(playlist)
                    val sortedList =
                        temp.sortedWith<PlaylistType>(
                            Comparator { p0, p1 ->
                                val timeP0: LocalDateTime? =
                                    when (p0) {
                                        is AlbumEntity -> p0.favoriteAt ?: p0.inLibrary
                                        is PlaylistEntity -> p0.favoriteAt ?: p0.inLibrary
                                        else -> null
                                    }
                                val timeP1: LocalDateTime? =
                                    when (p1) {
                                        is AlbumEntity -> p1.favoriteAt ?: p1.inLibrary
                                        is PlaylistEntity -> p1.favoriteAt ?: p1.inLibrary
                                        else -> null
                                    }
                                if (timeP0 == null || timeP1 == null) {
                                    return@Comparator if (timeP0 == null && timeP1 == null) {
                                        0
                                    } else if (timeP0 == null) {
                                        -1
                                    } else {
                                        1
                                    }
                                }
                                timeP0.compareTo(timeP1) // Sort in descending order by inLibrary time
                            },
                        )
                    _favoritePlaylist.value = LocalResource.Success(sortedList)
                }
            }
        }
    }

    fun getFavoritePodcasts() {
        viewModelScope.launch {
            podcastRepository.getFavoritePodcasts().collectLatest { podcasts ->
                val sortedList = podcasts.sortedByDescending { it.favoriteTime }
                _favoritePodcasts.value = LocalResource.Success(sortedList)
            }
        }
    }

    fun getCanvasSong() {
        _listCanvasSong.value = LocalResource.Loading()
        viewModelScope.launch {
            songRepository.getCanvasSong(max = 5).collect { data ->
                _listCanvasSong.value = LocalResource.Success(data)
            }
        }
    }

    fun getLocalPlaylist() {
        _yourLocalPlaylist.value = LocalResource.Loading()
        viewModelScope.launch {
            localPlaylistRepository.getAllLocalPlaylists().collect { values ->
//                    _listLocalPlaylist.postValue(values)
                _yourLocalPlaylist.value = LocalResource.Success(values.reversed())
            }
        }
    }

    fun getDownloadedPlaylist() {
        viewModelScope.launch {
            playlistRepository.getAllDownloadedPlaylist().combine(
                localPlaylistRepository.getDownloadedLocalPlaylists(),
            ) { remote, local ->
                (remote + local).sortedByDescending {
                    when (it) {
                        is AlbumEntity -> it.downloadedAt
                        is PlaylistEntity -> it.downloadedAt
                        is LocalPlaylistEntity -> it.downloadedAt
                        else -> null
                    }
                }
            }.collect { values ->
                _downloadedPlaylist.value = LocalResource.Success(values)
            }
        }
    }

    /**
     * Remove one playlist's/album's download from the Library grid (long-press). Same shape as the
     * detail screens' removal, minus their watchers: reset the container first, then remove the
     * downloads of the songs it owns exclusively — tracks another downloaded container references
     * keep their files, so deleting A never breaks B's offline copy.
     */
    fun removeDownloadedPlaylist(item: PlaylistType) {
        viewModelScope.launch {
            val tracks =
                when (item) {
                    is PlaylistEntity -> {
                        playlistRepository.updatePlaylistDownloadState(item.id, STATE_NOT_DOWNLOADED)
                        item.tracks
                    }

                    is AlbumEntity -> {
                        albumRepository.updateAlbumDownloadState(item.browseId, STATE_NOT_DOWNLOADED)
                        item.tracks
                    }

                    is LocalPlaylistEntity -> {
                        localPlaylistRepository.updateLocalPlaylistDownloadState(STATE_NOT_DOWNLOADED, item.id)
                        item.tracks
                    }

                    else -> return@launch
                } ?: return@launch
            removeExclusiveTrackDownloads(
                tracks = tracks,
                songRepository = songRepository,
                downloadUtils = downloadUtils,
                playlistRepository = playlistRepository,
                albumRepository = albumRepository,
                localPlaylistRepository = localPlaylistRepository,
            )
            makeToast(getString(Res.string.removed_download))
            getDownloadedPlaylist()
        }
    }

    /**
     * Which of the last twelve months the user actually listened in, and what each tile shows.
     *
     * A month with no plays is left out rather than shown empty: a "Recap March" that opens onto
     * nothing is worse than no row at all. Twelve is a cap, not a quota — a new install shows one
     * row, or none.
     *
     * The count comes first and gates everything after it: twelve `COUNT`s over an indexed
     * timestamp range are cheap, so the months with nothing in them are dropped before anything
     * asks them for a ranking. Only the survivors pay for a cover.
     *
     * Title and cover are resolved here rather than in the tile, which cannot suspend: the title
     * needs a month name out of a string resource with a format argument, and the cover needs a
     * ranking query followed by a song lookup.
     */
    fun getMonthlyRecaps() {

        // TODO(NETEASE_NEXT): 听歌分析基于本地 Room 统计,网易云歌曲落库(source 列)后自动
        // 被覆盖;可选增强:交叉 /user/record 账号级听歌排行做校准。无需结构性改动。
        _monthlyRecaps.value = LocalResource.Loading()
        viewModelScope.launch {
            val today = now().date
            val thisMonth = LocalDate(today.year, today.month, 1)
            val months =
                (0 until MONTHS_OF_RECAP)
                    .map { thisMonth.minus(it, DateTimeUnit.MONTH) }
                    .mapNotNull { firstDay ->
                        val lastDay = firstDay.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
                        val start = firstDay.atTime(0, 0)
                        val end = lastDay.atTime(23, 59, 59)
                        val plays =
                            analyticsRepository
                                .getPlaybackEventCountInRange(
                                    startTimestamp = start,
                                    endTimestamp = end,
                                ).firstOrNull() ?: 0L
                        if (plays <= 0L) return@mapNotNull null
                        MonthlyRecapItem(
                            year = firstDay.year,
                            month = firstDay.month.number,
                            title = recapTitle(firstDay.year, firstDay.month, today.year),
                        )
                    }
            _monthlyRecaps.value = LocalResource.Success(months)
        }
    }

    /**
     * "Recap January", or "Recap January 2025" once the year stops being obvious.
     *
     * The same rule and the same two format strings as the header the tile opens — see
     * [LibraryDynamicPlaylistType.title]. Fully qualified because [BaseViewModel] has a `getString`
     * of its own that takes no format argument and wraps `runBlocking`, which has no business
     * running inside a coroutine that is already suspended here.
     */
    private suspend fun recapTitle(
        year: Int,
        month: Month,
        currentYear: Int,
    ): String {
        val monthName =
            org.jetbrains.compose.resources
                .getString(monthFullNameResource(month))
        return if (year == currentYear) {
            org.jetbrains.compose.resources
                .getString(Res.string.wrapped_recap_month, monthName)
        } else {
            org.jetbrains.compose.resources
                .getString(Res.string.wrapped_recap_month_year, monthName, year.toString())
        }
    }

    fun getChartPlaylists() {
        _chartPlaylists.value = LocalResource.Loading()
        viewModelScope.launch {
            playlistRepository.getChartPlaylist().collectLatest {
                when (it) {
                    is Resource.Success -> _chartPlaylists.value = LocalResource.Success(it.data ?: emptyList())
                    is Resource.Error -> _chartPlaylists.value = LocalResource.Error(it.message ?: "Unknown error")
                }
            }
        }
    }

    fun createPlaylist(title: String) {
        viewModelScope.launch {
            val localPlaylistEntity = LocalPlaylistEntity(title = title)
            localPlaylistRepository
                .insertLocalPlaylist(
                    localPlaylistEntity,
                    getString(Res.string.added_local_playlist),
                ).lastOrNull()
                ?.let {
                    log("Created playlist with id: $it")
                }
            getLocalPlaylist()
        }
    }

    fun deleteSong(videoId: String) {
        _recentlyAdded.value = LocalResource.Loading()
        viewModelScope.launch {
            songRepository.setInLibrary(videoId, Config.REMOVED_SONG_DATE_TIME)
            songRepository.resetTotalPlayTime(videoId)
            delay(500) // Wait for the database to update
            getRecentlyAdded()
        }
    }

    companion object {
        /** How far back the Wrapped tab offers recaps, counting the current month as the first. */
        private const val MONTHS_OF_RECAP = 12
    }
}