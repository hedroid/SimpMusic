package com.maxrave.simpmusic.viewModel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import com.maxrave.common.Config
import com.maxrave.domain.data.entities.DownloadState
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.browse.artist.ResultAlbum
import com.maxrave.domain.data.model.searchResult.songs.Artist
import com.maxrave.domain.extension.now
import com.maxrave.domain.mediaservice.handler.DownloadHandler
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.repository.AlbumRepository
import com.maxrave.domain.repository.LocalPlaylistRepository
import com.maxrave.domain.repository.PlaylistRepository
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.utils.Resource
import com.maxrave.domain.utils.toAlbumEntity
import com.maxrave.domain.utils.toArrayListTrack
import com.maxrave.domain.utils.toSongEntity
import com.maxrave.logger.LogLevel
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import com.maxrave.simpmusic.viewModel.base.removeExclusiveTrackDownloads
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.inject
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.netease_action_failed
import simpmusic.composeapp.generated.resources.cloud_action_failed_netease
import simpmusic.composeapp.generated.resources.saved_toast
import simpmusic.composeapp.generated.resources.unsaved_toast
import simpmusic.composeapp.generated.resources.cloud_action_failed_youtube
import simpmusic.composeapp.generated.resources.unsubscribed_netease_album
import simpmusic.composeapp.generated.resources.album
import simpmusic.composeapp.generated.resources.downloaded
import simpmusic.composeapp.generated.resources.download_cancelled
import simpmusic.composeapp.generated.resources.error
import simpmusic.composeapp.generated.resources.playlist_is_empty
import simpmusic.composeapp.generated.resources.removed_download

class AlbumViewModel(
    private val songRepository: SongRepository,
    private val albumRepository: AlbumRepository,
) : BaseViewModel() {
    private val downloadUtils: DownloadHandler by inject<DownloadHandler>()
    private val playlistRepository: PlaylistRepository by inject<PlaylistRepository>()
    private val neteaseRepository: com.maxrave.data.repository.NeteaseRepositoryImpl by inject()
    private val dataStoreManager: DataStoreManager by inject()

    /** 专辑页"更多"菜单取消收藏网易专辑(/album/sub t=0),云端成功后 toast;本地 liked 同步熄灭 */
    fun unsubscribeNeteaseAlbum(albumId: String) {
        if (albumId.toLongOrNull() == null) return
        viewModelScope.launch {
            neteaseRepository
                .subscribeNeteaseAlbum(albumId, subscribe = false)
                .fold(
                    onSuccess = { ok ->
                        makeToast(
                            getString(
                                if (ok) Res.string.unsubscribed_netease_album else Res.string.netease_action_failed,
                            ),
                        )
                    },
                    onFailure = { makeToast(getString(Res.string.netease_action_failed)) },
                )
        }
    }
    private val localPlaylistRepository: LocalPlaylistRepository by inject<LocalPlaylistRepository>()
    private val _uiState: MutableStateFlow<AlbumUIState> = MutableStateFlow(AlbumUIState.initial())
    val uiState: StateFlow<AlbumUIState> = _uiState

    private var job: Job? = null
    private var collectDownloadStateJob: Job? = null

    fun updateBrowseId(browseId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(browseId = browseId) }
            albumRepository.getAlbumData(browseId).collectLatest { res ->
                when (res) {
                    is Resource.Success -> {
                        val data = res.data
                        if (data != null) {
                            _uiState.update {
                                it.copy(
                                    browseId = browseId,
                                    audioPlaylistId = data.audioPlaylistId,
                                    title = data.title,
                                    thumbnail = data.thumbnails?.lastOrNull()?.url,
                                    artist =
                                        data.artists.firstOrNull() ?: Artist(
                                            id = null,
                                            name = "",
                                        ),
                                    year = data.year?.takeIf { it.isNotBlank() } ?: now().year.toString(),
                                    trackCount = data.trackCount,
                                    description = data.description,
                                    length = data.duration ?: "",
                                    listTrack = data.tracks,
                                    otherVersion = data.otherVersion,
                                    loadState = LocalPlaylistState.PlaylistLoadState.Success,
                                )
                            }
                            val localAlbum = albumRepository.getAlbum(browseId).lastOrNull()
                            if (localAlbum != null) {
                                _uiState.update {
                                    it.copy(
                                        downloadState = localAlbum.downloadState,
                                        liked = localAlbum.liked,
                                    )
                                }
                                albumRepository.updateAlbumInLibrary(now(), browseId)
                            } else {
                                albumRepository.insertAlbum(data.toAlbumEntity(browseId)).singleOrNull().let {
                                    log("Insert Album $it")
                                    data.tracks.forEach { track ->
                                        songRepository
                                            .insertSong(
                                                track.toSongEntity().copy(
                                                    inLibrary = Config.REMOVED_SONG_DATE_TIME,
                                                ),
                                            ).singleOrNull()
                                            ?.let {
                                                log("Insert Song $it")
                                            }
                                    }
                                }
                            }
                            getAlbumFlow(browseId)
                            refreshRemoteSavedState()
                        } else {
                            makeToast(getString(Res.string.error) + ": Null data")
                            _uiState.update {
                                it.copy(
                                    loadState = LocalPlaylistState.PlaylistLoadState.Error,
                                )
                            }
                        }
                    }

                    is Resource.Error -> {
                        albumRepository.getAlbum(browseId).singleOrNull().let { albumEntity ->
                            if (albumEntity != null) {
                                _uiState.update {
                                    it.copy(
                                        browseId = browseId,
                                        title = albumEntity.title,
                                        thumbnail = albumEntity.thumbnails,
                                        artist =
                                            Artist(
                                                id = albumEntity.artistId?.firstOrNull(),
                                                name = albumEntity.artistName?.firstOrNull() ?: "",
                                            ),
                                        // Older installs stored the literal "null" while the
                                        // year parser was locale-broken — treat it as missing too.
                                        year = albumEntity.year
                                            ?.takeIf { it.isNotBlank() && it != "null" }
                                            ?: now().year.toString(),
                                        trackCount = albumEntity.trackCount,
                                        description = albumEntity.description,
                                        length = albumEntity.duration ?: "",
                                        listTrack =
                                            (
                                                songRepository
                                                    .getSongsByListVideoId(albumEntity.tracks ?: emptyList())
                                                    .singleOrNull() ?: emptyList()
                                            ).toArrayListTrack(),
                                        loadState = LocalPlaylistState.PlaylistLoadState.Success,
                                    )
                                }
                            } else {
                                log("Error: ${res.message}", LogLevel.ERROR)
                                makeToast(getString(Res.string.error) + ": ${res.message}")
                                _uiState.update {
                                    it.copy(
                                        loadState = LocalPlaylistState.PlaylistLoadState.Error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun setBrush(brush: List<Color>) {
        _uiState.update {
            it.copy(
                colors = brush,
            )
        }
    }

    fun setAlbumLike() {
        // 收藏心=云端账号状态:直接转发云端切换,本地行随结果镜像
        setRemoteSaved(!uiState.value.liked)
    }

    /** 收藏心=云端账号状态:拉到即驱动显示,并镜像本地缓存行(库页收藏分区读它)。 */
    private fun refreshRemoteSavedState() {
        viewModelScope.launch {
            _uiState.update { it.copy(remoteSaved = null) }
            val remote =
                albumRepository.getRemoteSavedState(
                    uiState.value.browseId,
                    uiState.value.audioPlaylistId,
                )
            if (remote != null) {
                if (uiState.value.liked != remote) {
                    albumRepository.updateAlbumLiked(uiState.value.browseId, if (remote) 1 else 0)
                }
                _uiState.update { it.copy(remoteSaved = remote, liked = remote) }
            } else {
                _uiState.update { it.copy(remoteSaved = null) }
            }
        }
    }

    /** 收藏心点击的唯一路径:直接切换云端账号收藏;成功镜像本地行+中性 toast。 */
    fun setRemoteSaved(saved: Boolean) {
        viewModelScope.launch {
            val isNeteaseId = uiState.value.browseId.toLongOrNull() != null
            _uiState.update { it.copy(remoteSavePending = true) }
            val ok =
                albumRepository.setRemoteSavedState(
                    uiState.value.browseId,
                    uiState.value.audioPlaylistId,
                    saved,
                )
            _uiState.update {
                it.copy(
                    remoteSaved = if (ok) saved else it.remoteSaved,
                    remoteSavePending = false,
                    liked = if (ok) saved else it.liked,
                )
            }
            if (ok) {
                albumRepository.updateAlbumLiked(uiState.value.browseId, if (saved) 1 else 0)
                makeToast(getString(if (saved) Res.string.saved_toast else Res.string.unsaved_toast))
            } else {
                makeToast(getString(if (isNeteaseId) Res.string.cloud_action_failed_netease else Res.string.cloud_action_failed_youtube))
            }
        }
    }

    private fun getAlbumFlow(browseId: String) {
        job?.cancel()
        collectDownloadStateJob?.cancel()
        job =
            viewModelScope.launch {
                albumRepository.getAlbumAsFlow(browseId).collectLatest { album ->
                    if (album != null) {
                        _uiState.update {
                            it.copy(
                                downloadState = album.downloadState,
                                liked = album.liked,
                            )
                        }
                    }
                }
            }
        collectDownloadStateJob =
            viewModelScope.launch {
                downloadUtils.downloadTask.collectLatest { downloadTask ->
                    var count = 0
                    uiState.value.listTrack.forEach { track ->
                        if (downloadTask[track.videoId] == DownloadState.STATE_DOWNLOADED) {
                            count++
                        }
                    }
                    if (count == uiState.value.listTrack.size) {
                        albumRepository.updateAlbumDownloadState(uiState.value.browseId, DownloadState.STATE_DOWNLOADED)
                        _uiState.update {
                            it.copy(
                                downloadState = DownloadState.STATE_DOWNLOADED,
                            )
                        }
                    }
                }
            }
    }

    fun playTrack(track: Track) {
        setQueueData(
            QueueData.Data(
                listTracks = uiState.value.listTrack.toCollection(ArrayList()),
                firstPlayedTrack = track,
                playlistId = uiState.value.browseId.replaceFirst("VL", ""),
                playlistName = "${getString(Res.string.album)} \"${uiState.value.title}\"",
                playlistType = PlaylistType.ALBUM,
                continuation = null,
            ),
        )
        val index = uiState.value.listTrack.indexOf(track)
        loadMediaItem(track, Config.ALBUM_CLICK, if (index == -1) 0 else index)
    }

    fun shuffle() {
        if (uiState.value.listTrack.isEmpty()) {
            makeToast(getString(Res.string.playlist_is_empty))
            return
        }
        val shuffleList = uiState.value.listTrack.shuffled()
        val randomIndex = shuffleList.indices.random()
        setQueueData(
            QueueData.Data(
                listTracks = shuffleList.toCollection(ArrayList()),
                firstPlayedTrack = shuffleList[randomIndex],
                playlistId = uiState.value.browseId.replaceFirst("VL", ""),
                playlistName = "${getString(Res.string.album)} \"${uiState.value.title}\"",
                // Not ALBUM: shuffling has already thrown away the running order, so the reason to
                // keep transitions gapless is gone. Crossfade behaves as it would for any playlist.
                playlistType = PlaylistType.PLAYLIST,
                continuation = null,
            ),
        )
        loadMediaItem(shuffleList[randomIndex], Config.ALBUM_CLICK, randomIndex)
    }

    fun downloadFullAlbum() {
        viewModelScope.launch {
            // Insert all song to database
            uiState.value.listTrack.forEach { track ->
                songRepository.insertSong(track.toSongEntity()).singleOrNull()?.let {
                    log("Insert Song $it")
                }
            }
            val fullListSong =
                songRepository
                    .getSongsByListVideoId(uiState.value.listTrack.map { it.videoId })
                    .singleOrNull() ?: emptyList()
            log("Full list song: $fullListSong")
            if (fullListSong.isEmpty()) {
                makeToast(getString(Res.string.playlist_is_empty))
                return@launch
            }
            val listJob = fullListSong.filter { it.downloadState != DownloadState.STATE_DOWNLOADED }
            log("List job: $listJob")
            if (listJob.isEmpty()) {
                makeToast(getString(Res.string.downloaded))
                return@launch
            }
            albumRepository.updateAlbumDownloadState(uiState.value.browseId, DownloadState.STATE_DOWNLOADING)
            listJob.forEach {
                log("Download: ${it.videoId} ${it.thumbnails}")
                downloadUtils.downloadTrack(
                    it.videoId,
                    it.title,
                    it.thumbnails ?: "",
                )
            }
        }
    }

    /** Stop an in-flight album download; finished tracks keep their files. */
    fun cancelDownloadingAlbum() {
        viewModelScope.launch {
            albumRepository.updateAlbumDownloadState(uiState.value.browseId, DownloadState.STATE_NOT_DOWNLOADED)
            _uiState.update {
                it.copy(
                    downloadState = DownloadState.STATE_NOT_DOWNLOADED,
                )
            }
            val fullListSong =
                songRepository
                    .getSongsByListVideoId(uiState.value.listTrack.map { it.videoId })
                    .singleOrNull() ?: emptyList()
            fullListSong.forEach { song ->
                if (song.downloadState == DownloadState.STATE_PREPARING || song.downloadState == DownloadState.STATE_DOWNLOADING) {
                    downloadUtils.removeDownload(song.videoId)
                    songRepository.updateDownloadState(song.videoId, DownloadState.STATE_NOT_DOWNLOADED)
                }
            }
            makeToast(getString(Res.string.download_cancelled))
        }
    }

    fun removeDownloadedAlbum() {
        viewModelScope.launch {
            albumRepository.updateAlbumDownloadState(uiState.value.browseId, DownloadState.STATE_NOT_DOWNLOADED)
            _uiState.update {
                it.copy(
                    downloadState = DownloadState.STATE_NOT_DOWNLOADED,
                )
            }
            // Only songs no other downloaded container references are actually removed.
            removeExclusiveTrackDownloads(
                tracks = uiState.value.listTrack.map { it.videoId },
                songRepository = songRepository,
                downloadUtils = downloadUtils,
                playlistRepository = playlistRepository,
                albumRepository = albumRepository,
                localPlaylistRepository = localPlaylistRepository,
            )
            makeToast(getString(Res.string.removed_download))
        }
    }
}

data class AlbumUIState(
    val browseId: String = "",
    val audioPlaylistId: String? = null,
    val title: String = "",
    val thumbnail: String? = null,
    val colors: List<Color> = listOf(Color.Black, Color.Black),
    val artist: Artist =
        Artist(
            id = null,
            name = "",
        ),
    val year: String = now().year.toString(),
    val downloadState: Int = DownloadState.STATE_NOT_DOWNLOADED,
    val liked: Boolean = false,
    val remoteSaved: Boolean? = null,
    val remoteSavePending: Boolean = false,
    val trackCount: Int = 0,
    val description: String? = null,
    val length: String = "",
    val listTrack: List<Track> = emptyList(),
    val otherVersion: List<ResultAlbum> = emptyList(),
    val loadState: LocalPlaylistState.PlaylistLoadState = LocalPlaylistState.PlaylistLoadState.Loading,
) {
    companion object {
        fun initial(): AlbumUIState = AlbumUIState()
    }
}
