package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.viewModelScope
import com.maxrave.common.Config
import com.maxrave.domain.data.entities.ArtistEntity
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.browse.artist.Albums
import com.maxrave.domain.data.model.browse.artist.ArtistBrowse
import com.maxrave.domain.data.model.browse.artist.ArtistLogo
import com.maxrave.domain.data.model.browse.artist.Related
import com.maxrave.domain.data.model.browse.artist.ResultPlaylist
import com.maxrave.domain.data.model.browse.artist.Singles
import com.maxrave.domain.data.model.searchResult.artists.ArtistsResult
import com.maxrave.domain.data.model.searchResult.songs.Thumbnail
import com.maxrave.domain.data.model.streams.YouTubeWatchEndpoint
import com.maxrave.domain.extension.now
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.repository.ArtistRepository
import com.maxrave.domain.repository.LyricsCanvasRepository
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.utils.Resource
import com.maxrave.simpmusic.extension.toArtistScreenData
import com.maxrave.simpmusic.viewModel.ArtistScreenState.Error
import com.maxrave.simpmusic.viewModel.ArtistScreenState.Loading
import com.maxrave.simpmusic.viewModel.ArtistScreenState.Success
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import kotlinx.coroutines.Job
import org.koin.core.component.inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.radio
import simpmusic.composeapp.generated.resources.shuffle
import simpmusic.composeapp.generated.resources.sync_follow_failed
import simpmusic.composeapp.generated.resources.subscribed_on_youtube
import simpmusic.composeapp.generated.resources.cloud_action_failed_netease
import simpmusic.composeapp.generated.resources.cloud_action_failed_youtube
import simpmusic.composeapp.generated.resources.followed_toast
import simpmusic.composeapp.generated.resources.subscribed_on_netease
import simpmusic.composeapp.generated.resources.unfollowed_toast
import simpmusic.composeapp.generated.resources.unsubscribed_on_youtube
import simpmusic.composeapp.generated.resources.unsubscribed_on_netease
import simpmusic.composeapp.generated.resources.sync_follow_failed_netease
import org.jetbrains.compose.resources.getString

class ArtistViewModel(
    private val artistRepository: ArtistRepository,
    private val songRepository: SongRepository,
    private val lyricsCanvasRepository: LyricsCanvasRepository,
    private val dataStoreManager: DataStoreManager,
) : BaseViewModel() {
    private val mutationBus: LibraryMutationBus by inject()

    // It is dynamic and can be changed by the user, so separate it from the ArtistScreenData
    private var _canvasUrl: MutableStateFlow<Pair<String, SongEntity>?> = MutableStateFlow(null)
    var canvasUrl: StateFlow<Pair<String, SongEntity>?> = _canvasUrl

    // Artist name-logo image + accent color from the hidden catalog (immersive header).
    private val _artistLogo: MutableStateFlow<ArtistLogo?> = MutableStateFlow(null)
    val artistLogo: StateFlow<ArtistLogo?> = _artistLogo

    // How many liked songs credit this artist — the "Liked songs" row above Popular (issue #2524)
    // shows only while this is above zero. Observed, so liking a song from the page updates it.
    private val _likedSongCount: MutableStateFlow<Int> = MutableStateFlow(0)
    val likedSongCount: StateFlow<Int> = _likedSongCount
    private var likedSongsJob: Job? = null

    private var _followed: MutableStateFlow<Boolean> = MutableStateFlow(false)
    var followed: StateFlow<Boolean> = _followed

    private val _remoteFollowed = MutableStateFlow<Boolean?>(null)
    val remoteFollowed: StateFlow<Boolean?> = _remoteFollowed

    private val _remoteFollowPending = MutableStateFlow(false)
    val remoteFollowPending: StateFlow<Boolean> = _remoteFollowPending

    private val _artistScreenState: MutableStateFlow<ArtistScreenState> = MutableStateFlow(Loading)
    val artistScreenState: StateFlow<ArtistScreenState> = _artistScreenState

    fun browseArtist(channelId: String) {
        _artistScreenState.value = Loading
        _canvasUrl.value = null
        _artistLogo.value = null
        _followed.value = false
        _remoteFollowed.value = null
        _remoteFollowPending.value = false
        _likedSongCount.value = 0
        likedSongsJob?.cancel()
        likedSongsJob =
            viewModelScope.launch {
                songRepository.getLikedSongsByArtist(channelId).collect { _likedSongCount.value = it.size }
            }
        viewModelScope.launch {
            artistRepository.getArtistData(channelId).collect { browse ->
                val data = browse.data
                when (browse) {
                    is Resource.Success if (data != null) -> {
                        data.channelId?.let { channelId ->
                            insertArtist(
                                ArtistEntity(
                                    channelId,
                                    data.name,
                                    data.thumbnails
                                        ?.lastOrNull()
                                        ?.url,
                                ),
                            )
                            // 关注=云端账号状态:拉到即驱动显示,并镜像本地缓存行
                            _remoteFollowed.value = data.subscribed
                            val cloudFollowed = data.subscribed
                            if (cloudFollowed != null) {
                                _followed.value = cloudFollowed
                                val localFollowed =
                                    artistRepository.getArtistById(channelId).firstOrNull()?.followed == true
                                if (localFollowed != cloudFollowed) {
                                    artistRepository.setFollowedLocal(channelId, cloudFollowed)
                                }
                            }
                        }
                        _artistScreenState.value =
                            Success(data.toArtistScreenData())
                        // Canvas comes ONLY from the single most-popular song: take the first
                        // popular result and use its canvas if it has one. If it doesn't,
                        // leave canvas null (already reset above) — no fallback to other songs.
                        data.songs?.results?.firstOrNull()?.let { topSong ->
                            val entity = songRepository.getSongById(topSong.videoId).firstOrNull()
                            val canvasUrl = entity?.canvasUrl
                            if (entity != null && canvasUrl != null) {
                                _canvasUrl.value = Pair(canvasUrl, entity)
                                log("CanvasUrl: $canvasUrl")
                            }
                        }
                    }

                    is Resource.Error ->
                        _artistScreenState.value = Error(browse.message ?: "Error")

                    else -> {
                        _artistScreenState.value = Error("Error")
                    }
                }
            }
        }
    }

    private suspend fun fetchAndCacheArtistLogo(
        channelId: String,
        artistName: String,
    ) {
        lyricsCanvasRepository.getArtistLogo(artistName).collectLatest { res ->
            if (res is Resource.Success) {
                val logo = res.data ?: return@collectLatest
                _artistLogo.value = logo
                artistRepository.updateArtistNameLogo(channelId, logo.logoUrl, logo.bgColorHex)
            }
        }
    }

    fun insertArtist(artist: ArtistEntity) {
        viewModelScope.launch {
            artistRepository.insertArtist(artist)
            artistRepository.updateArtistInLibrary(now(), artist.channelId)
            delay(100)
            artistRepository.getArtistById(artist.channelId).collect { artistEntity ->
                if (artistEntity != null) {
                    artist.thumbnails?.let {
                        artistRepository.updateArtistImage(artistEntity.channelId, it)
                    }
                    _followed.value = artistEntity.followed
                    log("insertArtist: ${artistEntity.followed}")
                    // Name-logo: reuse the cached one if present, else fetch + persist it.
                    val cachedLogoUrl = artistEntity.nameLogoUrl
                    if (cachedLogoUrl != null) {
                        _artistLogo.value =
                            ArtistLogo(
                                logoUrl = cachedLogoUrl,
                                bgColorHex = artistEntity.nameLogoColor,
                                width = 0,
                                height = 0,
                            )
                    } else {
                        launch { fetchAndCacheArtistLogo(artist.channelId, artist.name) }
                    }
                }
            }
        }
    }

    /**
     * 关注点击的唯一路径:直接切换云端账号关注。成功后镜像本地缓存(artist.followed,
     * 供库页关注的歌手分区),失败 toast。双向都发库页本地回写:取消关注→移除;
     * 关注→插入完整行(艺人页上下文即权威数据,与网络拉回的行同构)。
     */
    fun updateFollowed(
        followed: Int,
        channelId: String,
    ) {
        val target = followed == 1
        _followed.value = target
        viewModelScope.launch {
            val ok = artistRepository.setRemoteFollowedStatus(channelId, target)
            if (ok) {
                _remoteFollowed.value = target
                artistRepository.setFollowedLocal(channelId, target)
                makeToast(getString(if (target) Res.string.followed_toast else Res.string.unfollowed_toast))
                if (target) {
                    mutationBus.send(LibraryMutation.ArtistFollowed(followedArtistsResult(channelId)))
                } else {
                    // 库页本地回写:取消关注成功 → 关注分区原地移除(不做返回网络刷新)
                    mutationBus.send(LibraryMutation.ArtistUnfollowed(channelId))
                }
            } else {
                _followed.value = !target
                makeToast(
                    getString(
                        if (channelId.toLongOrNull() != null) Res.string.cloud_action_failed_netease else Res.string.cloud_action_failed_youtube,
                    ),
                )
            }
            log("updateFollowed: ${_followed.value}, ok: $ok")
        }
    }

    /** 关注事件的载荷:从页面状态构造完整艺人行(字段对齐库页 YT 艺人行的转换形状) */
    private fun followedArtistsResult(channelId: String): ArtistsResult {
        val data = (artistScreenState.value as? Success)?.data
        return ArtistsResult(
            artist = data?.title ?: "",
            browseId = channelId,
            category = "",
            radioId = "",
            resultType = "artist",
            shuffleId = "",
            thumbnails =
                data?.imageUrl.takeIf { !it.isNullOrBlank() }
                    ?.let { listOf(Thumbnail(height = 560, url = it, width = 560)) }
                    ?: emptyList(),
        )
    }

    /** Explicit source-account action; never mutates the SimpMusic-local follow flag. */
    fun setRemoteFollowed(
        followed: Boolean,
        channelId: String,
    ) {
        if (_remoteFollowPending.value) return
        viewModelScope.launch {
            _remoteFollowPending.value = true
            if (artistRepository.setRemoteFollowedStatus(channelId, followed)) {
                _remoteFollowed.value = followed
            } else {
                makeToast(
                    getString(
                        if (channelId.toLongOrNull() != null) Res.string.sync_follow_failed_netease else Res.string.sync_follow_failed,
                    ),
                )
            }
            _remoteFollowPending.value = false
        }
    }

    fun onRadioClick(endpoint: YouTubeWatchEndpoint) {
        viewModelScope.launch {
            songRepository.getRadioFromEndpoint(endpoint).collectLatest { res ->
                val data = res.data
                when (res) {
                    is Resource.Success if data != null && data.first.isNotEmpty() -> {
                        setQueueData(
                            QueueData.Data(
                                listTracks = data.first,
                                firstPlayedTrack = data.first.first(),
                                playlistId = endpoint.playlistId,
                                playlistName = "\"${artistScreenState.value.data.title}\" ${getString(Res.string.radio)}",
                                playlistType = PlaylistType.RADIO,
                                continuation = data.second,
                            ),
                        )
                        loadMediaItem(
                            data.first.first(),
                            Config.PLAYLIST_CLICK,
                            0,
                        )
                    }

                    else -> {
                        makeToast(res.message)
                    }
                }
            }
        }
    }

    fun onShuffleClick(endpoint: YouTubeWatchEndpoint) {
        viewModelScope.launch {
            songRepository.getRadioFromEndpoint(endpoint).collectLatest { res ->
                val data = res.data
                when (res) {
                    is Resource.Success if data != null && data.first.isNotEmpty() -> {
                        setQueueData(
                            QueueData.Data(
                                listTracks = data.first,
                                firstPlayedTrack = data.first.first(),
                                playlistId = endpoint.playlistId,
                                playlistName = "\"${artistScreenState.value.data.title}\" ${getString(Res.string.shuffle)}",
                                playlistType = PlaylistType.RADIO,
                                continuation = data.second,
                            ),
                        )
                        loadMediaItem(
                            data.first.first(),
                            Config.PLAYLIST_CLICK,
                            0,
                        )
                    }

                    else -> {
                        makeToast(res.message)
                    }
                }
            }
        }
    }

    /**
     * 网易艺人随机播放:shuffleParam(YouTube watch endpoint)对网易艺人恒为 null——
     * simiSong 电台是"歌曲级"相似,不适合整个艺人。直接把热门歌曲洗牌整队装载,
     * 语义对齐 YT 艺人 shuffleParam(队列=艺人电台,播完由无尽队列逻辑接管)。
     * 必须传 PLAYLIST_CLICK+index:SONG_CLICK 只装点击那一首(混合页整队装载同款教训)。
     */
    fun onNeteaseShuffleClick(
        songs: List<Track>,
        artistId: String,
        artistName: String?,
    ) {
        if (songs.isEmpty()) return
        val shuffled = songs.shuffled()
        viewModelScope.launch {
            setQueueData(
                QueueData.Data(
                    listTracks = shuffled,
                    firstPlayedTrack = shuffled.first(),
                    playlistId = "NETEASE_ARTIST_$artistId",
                    playlistName = "\"$artistName\" ${getString(Res.string.shuffle)}",
                    playlistType = PlaylistType.RADIO,
                    continuation = null,
                ),
            )
            loadMediaItem(
                shuffled.first(),
                Config.PLAYLIST_CLICK,
                0,
            )
        }
    }
}

data class ArtistScreenData(
    val title: String? = null,
    val imageUrl: String? = null,
    val subscribers: String? = null,
    val playCount: String? = null,
    val isChannel: Boolean = false,
    val channelId: String? = null,
    val radioParam: YouTubeWatchEndpoint? = null,
    val shuffleParam: YouTubeWatchEndpoint? = null,
    val description: String? = null,
    val listSongParam: String? = null,
    val popularSongs: List<Track> = emptyList(),
    val singles: Singles? = null,
    val albums: Albums? = null,
    val video: ArtistBrowse.Videos? = null,
    val related: Related? = null,
    val featuredOn: List<ResultPlaylist> = emptyList(),
)

sealed class ArtistScreenState(
    val data: ArtistScreenData = ArtistScreenData(),
    val message: String? = null,
) {
    data object Loading : ArtistScreenState()

    class Success(
        data: ArtistScreenData,
    ) : ArtistScreenState(data)

    class Error(
        message: String,
    ) : ArtistScreenState(message = message)
}
