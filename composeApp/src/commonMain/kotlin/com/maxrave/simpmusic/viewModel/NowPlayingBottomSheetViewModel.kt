package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.viewModelScope
import com.maxrave.common.Config
import com.maxrave.common.songRadioPlaylistId
import com.maxrave.domain.data.entities.DownloadState
import com.maxrave.domain.data.entities.LocalPlaylistEntity
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.data.model.searchResult.playlists.PlaylistsResult
import com.maxrave.domain.data.model.searchResult.songs.Album
import com.maxrave.domain.data.model.searchResult.songs.Artist
import com.maxrave.domain.data.model.streams.YouTubeWatchEndpoint
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.manager.DataStoreManager.Values.BETTER_LYRICS
import com.maxrave.domain.manager.DataStoreManager.Values.LRCLIB
import com.maxrave.domain.manager.DataStoreManager.Values.SIMPMUSIC
import com.maxrave.domain.manager.DataStoreManager.Values.YOUTUBE
import com.maxrave.domain.mediaservice.handler.DownloadHandler
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.mediaservice.handler.SleepTimerState
import com.maxrave.domain.repository.AlbumRepository
import com.maxrave.domain.repository.LocalPlaylistRepository
import com.maxrave.domain.repository.PlaylistRepository
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.utils.Resource
import com.maxrave.domain.utils.collectLatestResource
import com.maxrave.domain.utils.collectResource
import com.maxrave.domain.utils.toTrack
import com.maxrave.logger.LogLevel
import com.maxrave.simpmusic.expect.shareUrl
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.maxrave.simpmusic.viewModel.base.demoteDownloadedContainers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.inject
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.added_to_playlist
import simpmusic.composeapp.generated.resources.added_to_queue
import simpmusic.composeapp.generated.resources.added_to_youtube_playlist
import simpmusic.composeapp.generated.resources.added_to_netease_playlist
import simpmusic.composeapp.generated.resources.cloud_action_failed_netease
import simpmusic.composeapp.generated.resources.cloud_action_failed_youtube
import simpmusic.composeapp.generated.resources.netease_action_failed
import simpmusic.composeapp.generated.resources.netease_rate_limited
import com.maxrave.simpmusic.extension.neteaseWriteErrorString
import simpmusic.composeapp.generated.resources.delete_song_from_playlist
import simpmusic.composeapp.generated.resources.downloading
import simpmusic.composeapp.generated.resources.error
import simpmusic.composeapp.generated.resources.error_occurred
import simpmusic.composeapp.generated.resources.play_next
import simpmusic.composeapp.generated.resources.removed_download
import simpmusic.composeapp.generated.resources.removed_from_YouTube_playlist
import simpmusic.composeapp.generated.resources.share_url
import simpmusic.composeapp.generated.resources.sleep_timer_off_done

class NowPlayingBottomSheetViewModel(
    private val dataStoreManager: DataStoreManager,
    private val localPlaylistRepository: LocalPlaylistRepository,
    private val playlistRepository: PlaylistRepository,
    private val songRepository: SongRepository,
) : BaseViewModel() {
    private val downloadUtils: DownloadHandler by inject()
    private val albumRepository: AlbumRepository by inject()
    private val neteaseRepository: com.maxrave.data.repository.NeteaseRepositoryImpl by inject()
    private val _uiState: MutableStateFlow<NowPlayingBottomSheetUIState> =
        MutableStateFlow(
            NowPlayingBottomSheetUIState(
                listLocalPlaylist = emptyList(),
                listYouTubePlaylist = emptyList(),
                listNeteasePlaylist = emptyList(),
                mainLyricsProvider = SIMPMUSIC,
                sleepTimer =
                    SleepTimerState(
                        false,
                        0,
                    ),
            ),
        )
    val uiState: StateFlow<NowPlayingBottomSheetUIState> get() = _uiState.asStateFlow()

    private var getSongAsFlow: Job? = null

    init {
        viewModelScope.launch {
            val sleepTimerJob =
                launch {
                    mediaPlayerHandler.sleepTimerState.collectLatest { sl ->
                        _uiState.update { it.copy(sleepTimer = sl) }
                    }
                }
            val listLocalPlaylistJob =
                launch {
                    localPlaylistRepository.getAllLocalPlaylists().collectLatest { list ->
                        _uiState.update { it.copy(listLocalPlaylist = list) }
                    }
                }
            val listYouTubePlaylistJob =
                launch {
                    playlistRepository.getLibraryPlaylist().collect { data ->
                        _uiState.update { state ->
                            state.copy(
                                listYouTubePlaylist =
                                    data?.filter {
                                        it.browseId != "VLLM"
                                    } ?: emptyList(),
                            )
                        }
                    }
                }
            val mainLyricsProviderJob =
                launch {
                    dataStoreManager.lyricsProvider.collectLatest { lyricsProvider ->
                        when (lyricsProvider) {
                            SIMPMUSIC -> {
                                _uiState.update { it.copy(mainLyricsProvider = SIMPMUSIC) }
                            }

                            YOUTUBE -> {
                                _uiState.update { it.copy(mainLyricsProvider = YOUTUBE) }
                            }

                            LRCLIB -> {
                                _uiState.update { it.copy(mainLyricsProvider = LRCLIB) }
                            }

                            BETTER_LYRICS -> {
                                _uiState.update { it.copy(mainLyricsProvider = BETTER_LYRICS) }
                            }

                            else -> {
                                log("Unknown lyrics provider", LogLevel.ERROR)
                            }
                        }
                    }
                }
            sleepTimerJob.join()
            listLocalPlaylistJob.join()
            listYouTubePlaylistJob.join()
            mainLyricsProviderJob.join()
        }
    }

    fun resetPlaylists() {
        viewModelScope.launch {
            localPlaylistRepository.getAllLocalPlaylists().collectLatest { list ->
                _uiState.update { it.copy(listLocalPlaylist = list) }
            }
        }
        viewModelScope.launch {
            playlistRepository.getLibraryPlaylist().collect { data ->
                _uiState.update { state ->
                    state.copy(
                        listYouTubePlaylist =
                            data?.filter {
                                it.browseId != "VLLM"
                            } ?: emptyList(),
                    )
                }
            }
        }
    }

    fun setSongEntity(songEntity: SongEntity?) {
        val songOrNowPlaying = songEntity ?: (mediaPlayerHandler.nowPlayingState.value.songEntity ?: return)
        viewModelScope.launch {
            _uiState.update { it.copy(listNeteasePlaylist = emptyList()) }
            if (songOrNowPlaying.videoId.toLongOrNull() != null) {
                _uiState.update {
                    it.copy(listNeteasePlaylist = neteaseRepository.getOwnNeteasePlaylists())
                }
            }
            songOrNowPlaying.videoId.let {
                _uiState.update { state ->
                    state.copy(
                        songUIState =
                            state.songUIState.copy(
                                isAddedToYouTubeLiked = false,
                            ),
                    )
                }
                songRepository.getSongById(it).lastOrNull().let { song ->
                    if (song != null) {
                        getSongEntityFlow(videoId = song.videoId)
                    } else {
                        songRepository.insertSong(songOrNowPlaying).singleOrNull()?.let {
                            getSongEntityFlow(videoId = songOrNowPlaying.videoId)
                        }
                    }
                }
            }
        }
    }

    /**
     * 云端账号红心态(三点菜单"喜欢到云端账号"行):登录才拉得到,未登录保持 null=不显示行。
     * 与本地红心完全独立——显式操作云端,不 adopt 不镜像。
     */
    private val _cloudLiked = MutableStateFlow<Boolean?>(null)
    val cloudLiked: StateFlow<Boolean?> = _cloudLiked

    private fun refreshCloudLiked(videoId: String) {
        viewModelScope.launch {
            _cloudLiked.value = songRepository.getRemoteLikeStatus(videoId)
        }
    }

    private fun getSongEntityFlow(videoId: String) {
        getSongAsFlow?.cancel()
        if (videoId.isEmpty()) return
        refreshCloudLiked(videoId)
        getSongAsFlow =
            viewModelScope.launch {
                songRepository.getSongAsFlow(videoId).collectLatest { song ->
                    log("getSongEntityFlow: $song", LogLevel.WARN)
                    if (song != null) {
                        _uiState.update { state ->
                            state.copy(
                                songUIState =
                                    NowPlayingBottomSheetUIState.SongUIState(
                                        videoId = song.videoId,
                                        title = song.title,
                                        listArtists =
                                            song.artistName?.mapIndexed { i, name ->
                                                Artist(name = name, id = song.artistId?.getOrNull(i) ?: "")
                                            } ?: emptyList(),
                                        thumbnails = song.thumbnails,
                                        liked = song.liked,
                                        downloadState = song.downloadState,
                                        album =
                                            song.albumName?.takeIf { it.isNotEmpty() }?.let { name ->
                                                Album(name = name, id = song.albumId ?: "")
                                            },
                                    ),
                            )
                        }
                    }
                }
            }
    }

    /**
     * After the song's own download was removed, every downloaded container referencing it must
     * drop out of "downloaded" too, or its re-download watcher queues the song right back.
     */
    private suspend fun demoteDownloadedContainersOf(videoId: String) =
        demoteDownloadedContainers(
            videoId = videoId,
            playlistRepository = playlistRepository,
            albumRepository = albumRepository,
            localPlaylistRepository = localPlaylistRepository,
        )

    fun onUIEvent(ev: NowPlayingBottomSheetUIEvent) {
        val songUIState = uiState.value.songUIState
        if (songUIState.videoId.isEmpty()) return
        viewModelScope.launch {
            when (ev) {
                is NowPlayingBottomSheetUIEvent.DeleteFromPlaylist -> {
                    localPlaylistRepository
                        .removeTrackFromLocalPlaylist(
                            id = ev.playlistId,
                            song =
                                songRepository.getSongById(ev.videoId).lastOrNull() ?: run {
                                    makeToast(getString(Res.string.error_occurred))
                                    return@launch
                                },
                            successMessage = getString(Res.string.delete_song_from_playlist),
                            updatedYtMessage = getString(Res.string.removed_from_YouTube_playlist),
                            errorMessage = getString(Res.string.error_occurred),
                        ).collectResource(
                            onSuccess = {
                                makeToast(it ?: getString(Res.string.delete_song_from_playlist))
                            },
                            onError = {
                                makeToast(it)
                            },
                        )
                }

                is NowPlayingBottomSheetUIEvent.AddToYouTubePlaylist -> {
                    localPlaylistRepository
                        .addYouTubePlaylistItem(
                            youtubePlaylistId = ev.browseId,
                            videoId = songUIState.videoId,
                        ).collectLatestResource(
                            // Success 载荷是响应里的状态枚举名("STATUS_SUCCEEDED"),不是给人读的文案,
                            // 曾被直接 toast 出裸 key;失败载荷同理是仓库写死的 "FAILED"
                            onSuccess = {
                                makeToast(getString(Res.string.added_to_youtube_playlist))
                            },
                            onError = {
                                makeToast(getString(Res.string.error_occurred))
                            },
                        )
                }

                is NowPlayingBottomSheetUIEvent.AddToNeteasePlaylist -> {
                    neteaseRepository
                        .addTracksToNeteasePlaylist(ev.playlistId, listOf(songUIState.videoId))
                        .fold(
                            onSuccess = { ok ->
                                makeToast(getString(if (ok) Res.string.added_to_netease_playlist else Res.string.netease_action_failed))
                            },
                            onFailure = { makeToast(getString(neteaseWriteErrorString(it, Res.string.netease_action_failed))) },
                        )
                }

                is NowPlayingBottomSheetUIEvent.ToggleLike -> {
                    // 点赞=云端账号红心;成功后镜像本地缓存行(库页"喜欢的歌曲"读它)
                    val target = !(_cloudLiked.value ?: songUIState.liked)
                    val result = songRepository.setRemoteLikeStatus(songUIState.videoId, target)
                    val ok = result.getOrDefault(false)
                    if (ok) {
                        _cloudLiked.value = target
                        songRepository.setLikedLocal(songUIState.videoId, if (target) 1 else 0)
                    } else {
                        makeToast(
                            getString(
                                when {
                                    result.exceptionOrNull() is com.maxrave.netease.NeteaseRateLimitException ->
                                        Res.string.netease_rate_limited
                                    songUIState.videoId.toLongOrNull() != null -> Res.string.cloud_action_failed_netease
                                    else -> Res.string.cloud_action_failed_youtube
                                },
                            ),
                        )
                    }
                }


                is NowPlayingBottomSheetUIEvent.Download -> {
                    when (songUIState.downloadState) {
                        DownloadState.STATE_NOT_DOWNLOADED -> {
                            songRepository.updateDownloadState(
                                videoId = songUIState.videoId,
                                downloadState = DownloadState.STATE_PREPARING,
                            )
                            downloadUtils.downloadTrack(
                                videoId = songUIState.videoId,
                                title = songUIState.title,
                                thumbnail = songUIState.thumbnails ?: "",
                            )
                            makeToast(getString(Res.string.downloading))
                        }

                        DownloadState.STATE_PREPARING, DownloadState.STATE_DOWNLOADING -> {
                            // Demote FIRST: while a referencing container still claims to be
                            // downloaded, its re-download watcher can observe the song vanishing
                            // and queue it right back — undoing the removal the user just asked
                            // for. Once demoted, nothing watches the song anymore.
                            demoteDownloadedContainersOf(songUIState.videoId)
                            downloadUtils.removeDownload(songUIState.videoId)
                            songRepository.updateDownloadState(
                                songUIState.videoId,
                                DownloadState.STATE_NOT_DOWNLOADED,
                            )
                            makeToast(getString(Res.string.removed_download))
                        }

                        DownloadState.STATE_DOWNLOADED -> {
                            demoteDownloadedContainersOf(songUIState.videoId)
                            downloadUtils.removeDownload(songUIState.videoId)
                            songRepository.updateDownloadState(
                                songUIState.videoId,
                                DownloadState.STATE_NOT_DOWNLOADED,
                            )
                            makeToast(getString(Res.string.removed_download))
                        }
                    }
                }

                is NowPlayingBottomSheetUIEvent.AddToPlaylist -> {
                    val targetPlaylist = uiState.value.listLocalPlaylist.find { it.id == ev.playlistId } ?: return@launch
                    val newList = (targetPlaylist.tracks ?: emptyList<String>()).toMutableList()
                    if (newList.contains(songUIState.videoId)) {
                        return@launch
                    } else {
                        val songEntity = songRepository.getSongById(songUIState.videoId).singleOrNull() ?: return@launch
                        localPlaylistRepository
                            .addTrackToLocalPlaylist(
                                id = ev.playlistId,
                                song = songEntity,
                                successMessage = getString(Res.string.added_to_playlist),
                                updatedYtMessage = getString(Res.string.added_to_youtube_playlist),
                                errorMessage = getString(Res.string.error),
                            ).collectLatestResource(
                                onSuccess = {
                                    makeToast(it ?: getString(Res.string.added_to_playlist))
                                },
                                onError = {
                                    makeToast(it)
                                },
                            )
                    }
                }

                is NowPlayingBottomSheetUIEvent.PlayNext -> {
                    val songEntity = songRepository.getSongById(songUIState.videoId).singleOrNull() ?: return@launch
                    mediaPlayerHandler.playNext(songEntity.toTrack())
                    makeToast(getString(Res.string.play_next))
                }

                is NowPlayingBottomSheetUIEvent.AddToQueue -> {
                    val songEntity = songRepository.getSongById(songUIState.videoId).singleOrNull() ?: return@launch
                    mediaPlayerHandler.loadMoreCatalog(arrayListOf(songEntity.toTrack()), isAddToQueue = true)
                    makeToast(getString(Res.string.added_to_queue))
                }

                is NowPlayingBottomSheetUIEvent.ChangeLyricsProvider -> {
                    if (listOf(SIMPMUSIC, YOUTUBE, LRCLIB, BETTER_LYRICS).contains(ev.lyricsProvider)) {
                        dataStoreManager.setLyricsProvider(ev.lyricsProvider)
                    } else {
                        return@launch
                    }
                }

                is NowPlayingBottomSheetUIEvent.SetSleepTimer -> {
                    if (ev.cancel) {
                        mediaPlayerHandler.sleepStop()
                        makeToast(getString(Res.string.sleep_timer_off_done))
                    } else if (ev.minutes > 0) {
                        mediaPlayerHandler.sleepStart(ev.minutes)
                    }
                }

                is NowPlayingBottomSheetUIEvent.ChangePlaybackSpeedPitch -> {
                    dataStoreManager.setPlaybackSpeed(ev.speed)
                    dataStoreManager.setPitch(ev.pitch)
                }

                is NowPlayingBottomSheetUIEvent.Share -> {
                    // 网易数字 ID 拼进 YT 链接是无效地址;按 ID 形状分源拼分享链接
                    val url =
                        if (songUIState.videoId.toLongOrNull() != null) {
                            "https://music.163.com/song?id=${songUIState.videoId}"
                        } else {
                            "https://music.youtube.com/watch?v=${songUIState.videoId}"
                        }
                    shareUrl(
                        title = getString(Res.string.share_url),
                        url,
                    )
                }

                is NowPlayingBottomSheetUIEvent.StartRadio -> {
                    songRepository
                        .getRadioFromEndpoint(
                            YouTubeWatchEndpoint(
                                videoId = ev.videoId,
                                playlistId = songRadioPlaylistId(ev.videoId),
                            ),
                        ).collectLatest { res ->
                            val data = res.data
                            when (res) {
                                is Resource.Success if (data != null && data.first.isNotEmpty()) -> {
                                    setQueueData(
                                        QueueData.Data(
                                            listTracks = data.first,
                                            firstPlayedTrack = data.first.first(),
                                            playlistId = songRadioPlaylistId(ev.videoId),
                                            playlistName = ev.name,
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
                                    makeToast(res.message ?: getString(Res.string.error))
                                }
                            }
                        }
                }
            }
        }
    }
}

data class NowPlayingBottomSheetUIState(
    val songUIState: SongUIState = SongUIState(),
    val listLocalPlaylist: List<LocalPlaylistEntity>,
    val listYouTubePlaylist: List<PlaylistsResult>,
    val listNeteasePlaylist: List<PlaylistsResult>,
    val mainLyricsProvider: String,
    val sleepTimer: SleepTimerState,
) {
    data class SongUIState(
        val videoId: String = "",
        val title: String = "",
        val listArtists: List<Artist> = emptyList(),
        val thumbnails: String? = null,
        val liked: Boolean = false,
        val isAddedToYouTubeLiked: Boolean = false,
        val downloadState: Int = DownloadState.STATE_NOT_DOWNLOADED,
        val album: Album? = null,
    )
}

sealed class NowPlayingBottomSheetUIEvent {
    data class DeleteFromPlaylist(
        val videoId: String,
        val playlistId: Long,
    ) : NowPlayingBottomSheetUIEvent()

    data object ToggleLike : NowPlayingBottomSheetUIEvent()


    data object Download : NowPlayingBottomSheetUIEvent()

    data class AddToPlaylist(
        val playlistId: Long,
    ) : NowPlayingBottomSheetUIEvent()

    data class AddToYouTubePlaylist(
        val browseId: String,
    ) : NowPlayingBottomSheetUIEvent()

    data class AddToNeteasePlaylist(
        val playlistId: String,
    ) : NowPlayingBottomSheetUIEvent()

    data object PlayNext : NowPlayingBottomSheetUIEvent()

    data object AddToQueue : NowPlayingBottomSheetUIEvent()

    data class ChangeLyricsProvider(
        val lyricsProvider: String,
    ) : NowPlayingBottomSheetUIEvent()

    data class SetSleepTimer(
        val cancel: Boolean = false,
        val minutes: Int = 0,
    ) : NowPlayingBottomSheetUIEvent()

    data class ChangePlaybackSpeedPitch(
        val speed: Float,
        val pitch: Int,
    ) : NowPlayingBottomSheetUIEvent()

    data class StartRadio(
        val videoId: String,
        val name: String,
    ) : NowPlayingBottomSheetUIEvent()

    data object Share : NowPlayingBottomSheetUIEvent()
}
