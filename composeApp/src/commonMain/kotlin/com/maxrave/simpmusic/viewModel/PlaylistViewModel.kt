@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.maxrave.simpmusic.viewModel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import com.maxrave.common.Config
import com.maxrave.data.repository.NeteaseRepositoryImpl
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.data.entities.DownloadState.STATE_DOWNLOADED
import com.maxrave.domain.data.entities.DownloadState.STATE_DOWNLOADING
import com.maxrave.domain.data.entities.DownloadState.STATE_NOT_DOWNLOADED
import com.maxrave.domain.data.entities.DownloadState.STATE_PREPARING
import com.maxrave.domain.data.entities.PlaylistEntity
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.browse.playlist.Author
import com.maxrave.domain.data.model.browse.playlist.PlaylistBrowse
import com.maxrave.domain.data.model.browse.playlist.PlaylistState
import com.maxrave.domain.extension.now
import com.maxrave.domain.mediaservice.handler.DownloadHandler
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.repository.AlbumRepository
import com.maxrave.domain.repository.LocalPlaylistRepository
import com.maxrave.domain.repository.PlaylistRepository
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.utils.Resource
import com.maxrave.domain.utils.collectLatestResource
import com.maxrave.domain.utils.isRadioPlaylistId
import com.maxrave.domain.utils.toListVideoId
import com.maxrave.domain.utils.toPlaylistEntity
import com.maxrave.domain.utils.toSongEntity
import com.maxrave.domain.utils.toTrack
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.viewModel.PlaylistUIState.Error
import com.maxrave.simpmusic.viewModel.PlaylistUIState.Loading
import com.maxrave.simpmusic.viewModel.PlaylistUIState.Success
import com.maxrave.simpmusic.viewModel.SharedViewModel
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import com.maxrave.simpmusic.viewModel.base.removeExclusiveTrackDownloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.inject
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.auto_created_by_youtube_music
import simpmusic.composeapp.generated.resources.downloading
import simpmusic.composeapp.generated.resources.removed_from_playlist
import simpmusic.composeapp.generated.resources.netease_action_failed
import simpmusic.composeapp.generated.resources.cloud_action_failed_netease
import simpmusic.composeapp.generated.resources.saved_toast
import simpmusic.composeapp.generated.resources.unsaved_toast
import simpmusic.composeapp.generated.resources.cloud_action_failed_youtube
import simpmusic.composeapp.generated.resources.deleted_playlist
import simpmusic.composeapp.generated.resources.unsubscribed_netease_playlist
import simpmusic.composeapp.generated.resources.unsubscribed_youtube_playlist
import simpmusic.composeapp.generated.resources.remove_from_playlist_failed
import simpmusic.composeapp.generated.resources.download_cancelled
import simpmusic.composeapp.generated.resources.error
import simpmusic.composeapp.generated.resources.playlist
import simpmusic.composeapp.generated.resources.playlist_is_empty
import simpmusic.composeapp.generated.resources.radio
import simpmusic.composeapp.generated.resources.radio_not_available
import simpmusic.composeapp.generated.resources.removed_download
import simpmusic.composeapp.generated.resources.shuffle
import simpmusic.composeapp.generated.resources.shuffle_not_available
import simpmusic.composeapp.generated.resources.synced
import simpmusic.composeapp.generated.resources.syncing
import simpmusic.composeapp.generated.resources.view_count

class PlaylistViewModel(
    private val songRepository: SongRepository,
    private val localPlaylistRepository: LocalPlaylistRepository,
    private val playlistRepository: PlaylistRepository,
    private val neteaseRepository: NeteaseRepositoryImpl,
    private val dataStoreManager: DataStoreManager,
    private val sharedViewModel: SharedViewModel,
) : BaseViewModel() {
    // 红心歌单页不做"取消红心→立即剔歌/计数-1"(用户 2026-09-22 定案:页面会闪动,
    // 效果差;云端为准,下次进入自然更新)。曾经有 B1 下降沿联动+onTrackLikeChanged
    // 即时剔歌,已整体撤除;三选一菜单的"从歌单移除"是显式移除操作,保留剔歌。
    val downloadUtils: DownloadHandler by inject<DownloadHandler>()
    private val albumRepository: AlbumRepository by inject<AlbumRepository>()
    private val mutationBus: LibraryMutationBus by inject()
    private var _uiState = MutableStateFlow<PlaylistUIState>(Loading)
    val uiState: StateFlow<PlaylistUIState> = _uiState

    private var _listColors = MutableStateFlow<List<Color>>(emptyList())
    val listColors: StateFlow<List<Color>> = _listColors

    private var _continuation = MutableStateFlow<String?>(null)
    val continuation: StateFlow<String?> = _continuation

    private var _playlistEntity: MutableStateFlow<PlaylistEntity?> = MutableStateFlow(null)
    var playlistEntity: StateFlow<PlaylistEntity?> = _playlistEntity

    val downloadState = _playlistEntity.map { it?.downloadState ?: 0 }.stateIn(viewModelScope, WhileSubscribed(1000), 0)
    val liked = _playlistEntity.map { it?.liked == true }.stateIn(viewModelScope, WhileSubscribed(1000), false)

    private val _remoteSaved = MutableStateFlow<Boolean?>(null)
    val remoteSaved: StateFlow<Boolean?> = _remoteSaved
    private val _remoteSavePending = MutableStateFlow(false)
    val remoteSavePending: StateFlow<Boolean> = _remoteSavePending

    private var _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks

    /** 是否红心歌单("我喜欢的音乐"):不可删除/不可取消收藏,详情页两个入口都排除 */
    suspend fun isNeteaseLikedPlaylist(): Boolean {
        val id = (uiState.value as? Success)?.data?.id ?: return false
        return id.toLongOrNull() != null && neteaseRepository.isNeteaseLikedPlaylist(id)
    }

    /** 当前歌单是否网易自建(歌曲菜单露"从歌单移除"的判定;收藏歌单/雷达/YT 恒 false) */
    suspend fun isNeteaseOwnPlaylist(): Boolean {
        val id = (uiState.value as? Success)?.data?.id ?: return false
        return id.toLongOrNull() != null && neteaseRepository.isOwnNeteasePlaylist(id)
    }

    /** 收藏心=云端账号状态:拉到即驱动显示,并镜像本地缓存行(库页收藏分区读它)。 */
    private fun maybeAdoptRemoteSaved(playlistId: String) {
        viewModelScope.launch {
            val cloud = playlistRepository.getRemoteSavedState(playlistId)
            _remoteSaved.value = cloud
            if (cloud != null) {
                val localLiked = _playlistEntity.value?.liked == true
                if (localLiked != cloud) {
                    playlistRepository.updatePlaylistLiked(playlistId, if (cloud) 1 else 0)
                }
                _playlistEntity.update { it?.copy(liked = cloud) }
            }
        }
    }

    private fun refreshRemoteSavedState(playlistId: String) {
        _remoteSaved.value = null
        viewModelScope.launch {
            _remoteSaved.value = playlistRepository.getRemoteSavedState(playlistId)
        }
    }

    /**
     * 收藏心点击的唯一路径:直接切换云端账号收藏。成功后镜像本地缓存行并给中性 toast,
     * 失败按平台 toast。自建网易歌单无"收藏"概念,心已在 UI 隐藏不会走到这里。
     */
    fun setRemoteSaved(saved: Boolean) {
        val id = (uiState.value as? Success)?.data?.id ?: return
        val isNeteaseId = id.toLongOrNull() != null
        viewModelScope.launch {
            _remoteSavePending.value = true
            // 网易分支失败会抛出服务端原文(如"操作过于频繁,请稍后再试"),直接 toast 更可读
            val outcome = runCatching { playlistRepository.setRemoteSavedState(id, saved) }
            if (outcome.getOrDefault(false)) {
                _remoteSaved.value = saved
                playlistRepository.updatePlaylistLiked(id, if (saved) 1 else 0)
                _playlistEntity.update { it?.copy(liked = saved) }
                makeToast(getString(if (saved) Res.string.saved_toast else Res.string.unsaved_toast))
                if (!saved) {
                    // 取消收藏成功 → 库页歌单分区原地移除(本地回写;收藏方向下次拉取自然出现)
                    mutationBus.send(LibraryMutation.PlaylistRemoved(id))
                }
            } else {
                makeToast(
                    outcome.exceptionOrNull()?.message
                        ?: getString(if (isNeteaseId) Res.string.cloud_action_failed_netease else Res.string.cloud_action_failed_youtube),
                )
            }
            _remoteSavePending.value = false
        }
    }

    /**
     * 删除自己的网易歌单(/playlist/delete,不可逆)。成功后清本地 liked(红心熄灭);
     * Room 缓存行随下次访问自然失效(歌单已不存在)。导航返回由调用方(UI)处理。
     */
    fun deleteNeteasePlaylist() {
        val id = (uiState.value as? Success)?.data?.id ?: return
        if (id.toLongOrNull() == null) return
        viewModelScope.launch {
            neteaseRepository
                .deleteNeteasePlaylist(id)
                .fold(
                    onSuccess = { ok ->
                        if (ok) {
                            playlistRepository.updatePlaylistLiked(id, 0)
                            _playlistEntity.update { it?.copy(liked = false) }
                            makeToast(getString(Res.string.deleted_playlist))
                            mutationBus.send(LibraryMutation.PlaylistRemoved(id))
                        } else {
                            makeToast(getString(Res.string.netease_action_failed))
                        }
                    },
                    onFailure = { makeToast(getString(Res.string.netease_action_failed)) },
                )
        }
    }

    /**
     * 收藏歌单详情页"取消收藏"(YT):like/removelike 移出资料库,成功后发库页本地回写。
     * 不自动返回导航(歌单还在云端,调用方留在当前页)。
     */
    fun unsubscribeYouTubePlaylist() {
        val rawId = (uiState.value as? Success)?.data?.id ?: return
        if (rawId.toLongOrNull() != null) return
        val id = if (rawId.startsWith("VL")) rawId else "VL$rawId"
        viewModelScope.launch {
            if (playlistRepository.removeYouTubePlaylistFromLibrary(id)) {
                _playlistEntity.update { it?.copy(liked = false) }
                _remoteSaved.value = false
                makeToast(getString(Res.string.unsubscribed_youtube_playlist))
                mutationBus.send(LibraryMutation.PlaylistRemoved(id))
            } else {
                makeToast(getString(Res.string.netease_action_failed))
            }
        }
    }

    /**
     * 删除自己的 YT 歌单(playlist/delete,不可逆;2026-09-22 补齐——自建歌单详情页
     * "更多"菜单,与网易自建同款入口)。成功后发库页本地回写。
     */
    fun deleteYouTubePlaylist() {
        val rawId = (uiState.value as? Success)?.data?.id ?: return
        if (rawId.toLongOrNull() != null) return
        // 详情页 id 可能缺 VL 前缀(两个来源形状不一);playlist/delete 要求原样 browseId,
        // 统一补齐(Metrolist 同款透传形状)
        val id = if (rawId.startsWith("VL")) rawId else "VL$rawId"
        viewModelScope.launch {
            if (playlistRepository.deleteYouTubePlaylist(id)) {
                makeToast(getString(Res.string.deleted_playlist))
                println("QQQ sending PlaylistRemoved(YouTube): $id")
                mutationBus.send(LibraryMutation.PlaylistRemoved(id))
            } else {
                makeToast(getString(Res.string.netease_action_failed))
            }
        }
    }

    /**
     * 歌单页"更多"菜单取消收藏网易歌单:云端 subscribe t=0 + 本地 liked 清零(红心熄灭),
     * 与库页长按路径行为一致(那边不持有页面状态,只清云端)。
     */
    fun unsubscribeNeteasePlaylist() {
        val id = (uiState.value as? Success)?.data?.id ?: return
        if (id.toLongOrNull() == null) return
        viewModelScope.launch {
            neteaseRepository
                .subscribeNeteasePlaylist(id, subscribe = false)
                .fold(
                    onSuccess = { ok ->
                        if (ok) {
                            playlistRepository.updatePlaylistLiked(id, 0)
                            _playlistEntity.update { it?.copy(liked = false) }
                            makeToast(getString(Res.string.unsubscribed_netease_playlist))
                            mutationBus.send(LibraryMutation.PlaylistRemoved(id))
                        } else {
                            makeToast(getString(Res.string.netease_action_failed))
                        }
                    },
                    onFailure = { makeToast(getString(Res.string.netease_action_failed)) },
                )
        }
    }

    /** 红心歌单的"移除歌单"=取消红心:云村 unlike + 本地 liked 清零 + 内存列表剔掉 */
    private fun unlikeNeteaseSong(videoId: String) {
        viewModelScope.launch {
            neteaseRepository
                .setSongLiked(videoId, like = false)
                .fold(
                    onSuccess = { ok ->
                        if (ok) {
                            songRepository.setLikedLocal(videoId, 0)
                            _tracks.update { list -> list.filterNot { it.videoId == videoId } }
                            (uiState.value as? Success)?.data?.let { state ->
                                _uiState.value =
                                    Success(state.copy(trackCount = (state.trackCount - 1).coerceAtLeast(0)))
                            }
                            makeToast(getString(Res.string.removed_from_playlist))
                            // 库页红心歌单行计数 -1(本地回写)
                            mutationBus.send(LibraryMutation.NeteaseHeartCountChanged(-1))
                        } else {
                            makeToast(getString(Res.string.remove_from_playlist_failed))
                        }
                    },
                    onFailure = { makeToast(getString(Res.string.remove_from_playlist_failed)) },
                )
        }
    }

    /**
     * 从网易自建歌单移除一首歌。**红心歌单特例:移除=取消红心**(/song/like t=0 + 本地
     * liked 清零),与播放页红心按钮同一逻辑——歌不在红心歌单里了,红心自然也没了。
     * 其余自建歌单走 manipulate op=del:云端删成功后从内存列表剔掉,Room 缓存行随下次
     * 整单拉取/EXHAUST 回写自然收敛。
     */
    fun removeTrackFromNeteasePlaylist(videoId: String) {
        val id = (uiState.value as? Success)?.data?.id ?: return
        if (id.toLongOrNull() == null) return
        if (neteaseRepository.isNeteaseLikedPlaylist(id)) {
            unlikeNeteaseSong(videoId)
            return
        }
        viewModelScope.launch {
            neteaseRepository
                .removeTracksFromNeteasePlaylist(id, listOf(videoId))
                .fold(
                    onSuccess = { ok ->
                        if (ok) {
                            _tracks.update { list -> list.filterNot { it.videoId == videoId } }
                            // 表头计数跟着减(PlaylistState.trackCount 是服务端元数据快照,
                            // 不改会一直显示删除前的数字;Room 缓存行随下次整单拉取收敛)
                            (uiState.value as? Success)?.data?.let { state ->
                                _uiState.value =
                                    Success(
                                        state.copy(trackCount = (state.trackCount - 1).coerceAtLeast(0)),
                                    )
                            }
                            makeToast(getString(Res.string.removed_from_playlist))
                        } else {
                            makeToast(getString(Res.string.remove_from_playlist_failed))
                        }
                    },
                    onFailure = { makeToast(getString(Res.string.remove_from_playlist_failed)) },
                )
        }
    }

    private var _tracksListState = MutableStateFlow<ListState>(ListState.IDLE)
    val tracksListState: StateFlow<ListState> = _tracksListState

    private var collectDownloadedJob: Job? = null
    private var _downloadedList = MutableStateFlow<List<String>>(emptyList())
    val downloadedList: StateFlow<List<String>> = _downloadedList

    private var playlistEntityJob: Job? = null
    private var newUpdateJob: Job? = null
    private var checkDownloadedPlaylist: Job? = null

    init {
        viewModelScope.launch {
            val listTrackStringJob =
                launch(Dispatchers.IO) {
                    downloadState
                        .collectLatest { state ->
                            newUpdateJob?.cancel()
                            val id = playlistEntity.value?.id ?: return@collectLatest
                            if (state == STATE_DOWNLOADING || state == STATE_DOWNLOADED) {
                                getFullTracks { tracks ->
                                    // The fetch outlives this collector's cancellation, so an
                                    // in-flight callback can land after removeDownloadedPlaylist()
                                    // already reset the playlist to NOT_DOWNLOADED — with the
                                    // captured "downloaded" state it would queue every track right
                                    // back. Re-read the live state instead.
                                    val liveState = downloadState.value
                                    if (liveState != STATE_DOWNLOADING && liveState != STATE_DOWNLOADED) {
                                        return@getFullTracks
                                    }
                                    newUpdateJob =
                                        launch {
                                            val listSongs = songRepository.getSongsByListVideoId(tracks.toListVideoId()).firstOrNull() ?: emptyList()
                                            if (liveState == STATE_DOWNLOADED && listSongs.isNotEmpty()) {
                                                listSongs.filter { it.downloadState != STATE_DOWNLOADED }.let { notDownloaded ->
                                                    if (notDownloaded.isNotEmpty()) {
                                                        downloadTracks(notDownloaded.map { it.videoId })
                                                        updatePlaylistDownloadState(id, STATE_DOWNLOADING)
                                                    } else {
                                                        updatePlaylistDownloadState(id, STATE_DOWNLOADED)
                                                    }
                                                }
                                            }
                                            downloadUtils.downloads.collectLatest { downloads ->
                                                // Same guard as the callback above: a playlist
                                                // demoted mid-flight must not be written back.
                                                val live = downloadState.value
                                                if (live != STATE_DOWNLOADING && live != STATE_DOWNLOADED) {
                                                    return@collectLatest
                                                }
                                                var count = 0
                                                tracks.forEachIndexed { index, track ->
                                                    val trackDownloadState = downloads[track.videoId]?.first?.state
                                                    val videoDownloadState =
                                                        downloads[track.videoId]?.second?.state ?: DownloadHandler.State.STATE_COMPLETED
                                                    if (trackDownloadState == DownloadHandler.State.STATE_DOWNLOADING ||
                                                        videoDownloadState == DownloadHandler.State.STATE_DOWNLOADING
                                                    ) {
                                                        updatePlaylistDownloadState(id, STATE_DOWNLOADING)
                                                    } else if (trackDownloadState == DownloadHandler.State.STATE_COMPLETED &&
                                                        videoDownloadState == DownloadHandler.State.STATE_COMPLETED
                                                    ) {
                                                        count++
                                                    }
                                                    if (count == tracks.size) {
                                                        updatePlaylistDownloadState(id, STATE_DOWNLOADED)
                                                    }
                                                }
                                            }
                                        }
                                }
                            }
                        }
                }
            listTrackStringJob.join()
        }
    }

    private fun updatePlaylistDownloadState(
        id: String,
        state: Int,
    ) {
        viewModelScope.launch {
            playlistRepository.updatePlaylistDownloadState(id, state)
            delay(500)
            _playlistEntity.update {
                it?.copy(downloadState = state)
            }
        }
    }

    private fun downloadTracks(listJob: List<String>) {
        viewModelScope.launch {
            listJob.forEach { videoId ->
                songRepository.getSongById(videoId).singleOrNull()?.let { song ->
                    if (song.downloadState != STATE_DOWNLOADED) {
                        downloadUtils.downloadTrack(videoId, song.title, song.thumbnails ?: "")
                    }
                }
            }
        }
    }

    private fun resetData() {
        _uiState.value = Loading
        _playlistEntity.value = null
        _downloadedList.value = emptyList()
        _listColors.value = emptyList()
        checkDownloadedPlaylist?.cancel()
        checkDownloadedPlaylist = null
    }

    fun getData(id: String) {
        resetData()
        viewModelScope.launch {
            // Check radio
            if (id.isRadioPlaylistId()) {
                playlistRepository
                    .getRadio(
                        id,
                        radioString = getString(Res.string.radio),
                        defaultDescription = getString(Res.string.auto_created_by_youtube_music),
                        viewString = getString(Res.string.view_count),
                    ).collect { res ->
                        val data = res.data
                        when (res) {
                            is Resource.Success if (data != null) -> {
                                Logger.d(tag, "Radio data: $data")
                                _uiState.value =
                                    Success(
                                        data =
                                            PlaylistState(
                                                id = data.first.id,
                                                title = data.first.title,
                                                isRadio = true,
                                                author = data.first.author,
                                                thumbnail =
                                                    data.first.thumbnails
                                                        .lastOrNull()
                                                        ?.url,
                                                description = data.first.description,
                                                trackCount = data.first.trackCount,
                                                year = data.first.year,
                                            ),
                                    )
                                _tracks.value = data.first.tracks
                                _continuation.value = data.second
                                if (data.second.isNullOrEmpty()) _tracksListState.value = ListState.PAGINATION_EXHAUST
                                playlistRepository.insertRadioPlaylist(data.first.toPlaylistEntity())
                            }

                            else -> {
                                _uiState.value = Error(res.message ?: "Empty response")
                            }
                        }
                    }
            } else {
                // This is an online playlist
                playlistRepository
                    .getPlaylistData(id, getString(Res.string.view_count))
                    .collect { res ->
                        val data = res.data
                        when (res) {
                            is Resource.Success if (data != null) -> {
                                Logger.d(tag, "Playlist data: $data")
                                log("Playlist endpoint: ${data.first.shuffleEndpoint}")
                                _uiState.value =
                                    Success(
                                        data =
                                            PlaylistState(
                                                id = data.first.id,
                                                title = data.first.title,
                                                isRadio = false,
                                                author = data.first.author,
                                                thumbnail =
                                                    data.first.thumbnails
                                                        .lastOrNull()
                                                        ?.url,
                                                description = data.first.description,
                                                trackCount = data.first.trackCount,
                                                year = data.first.year,
                                                shuffleEndpoint = data.first.shuffleEndpoint,
                                                radioEndpoint = data.first.radioEndpoint,
                                            ),
                                    )
                                _tracks.value = data.first.tracks
                                _continuation.value = data.second
                                if (data.second.isNullOrEmpty()) _tracksListState.value = ListState.PAGINATION_EXHAUST
                                getPlaylistEntity(id = data.first.id, playlistBrowse = data.first)
                                maybeAdoptRemoteSaved(data.first.id)
                                refreshRemoteSavedState(data.first.id)
                            }

                            else -> {
                                getPlaylistEntity(id)
                            }
                        }
                    }
            }
        }
    }

    fun getContinuationTrack(
        playlistId: String,
        continuation: String?,
    ) {
        viewModelScope.launch {
            if (continuation.isNullOrEmpty()) {
                _tracksListState.value = ListState.PAGINATION_EXHAUST
                return@launch
            } else {
                _tracksListState.value = ListState.PAGINATING
                songRepository
                    .getContinueTrack(
                        playlistId,
                        continuation,
                        fromPlaylist = true,
                    ).collectLatest { res ->
                        res.first?.forEach { track ->
                            songRepository
                                .insertSong(
                                    track
                                        .toSongEntity()
                                        .copy(
                                            inLibrary = Config.REMOVED_SONG_DATE_TIME,
                                        ),
                                ).singleOrNull()
                                ?.let {
                                    log("Insert song: $it")
                                }
                        }
                        _tracks.update {
                            val newList = it.toMutableList()
                            newList.addAll(res.first ?: emptyList())
                            newList
                        }
                        if (res.second.isNullOrEmpty()) {
                            _continuation.value = null
                            _tracksListState.value = ListState.PAGINATION_EXHAUST
                        } else {
                            _continuation.value = res.second
                            _tracksListState.value = ListState.IDLE
                        }
                    }
            }
        }
    }

    private fun getPlaylistEntity(
        id: String,
        playlistBrowse: PlaylistBrowse? = null,
    ) {
        playlistEntityJob?.cancel()
        playlistEntityJob =
            viewModelScope.launch {
                val playlistEntity = playlistRepository.getPlaylist(id).firstOrNull()
                if (playlistBrowse != null) {
                    if (playlistEntity == null) {
                        playlistRepository.insertAndReplacePlaylist(
                            playlistBrowse.toPlaylistEntity(),
                        )
                        delay(500)
                        playlistRepository.getPlaylist(id).collectLatest { playlist ->
                            _playlistEntity.value = playlist
                            playlistRepository.updatePlaylistInLibrary(
                                playlistId = id,
                                inLibrary = now(),
                            )
                        }
                    } else {
                        _playlistEntity.value = playlistEntity
                        playlistRepository.updatePlaylistInLibrary(
                            playlistId = id,
                            inLibrary = now(),
                        )
                    }
                    playlistBrowse.tracks.forEach { tracks ->
                        songRepository
                            .insertSong(
                                tracks.toSongEntity().copy(
                                    inLibrary = Config.REMOVED_SONG_DATE_TIME,
                                ),
                            ).firstOrNull()
                            ?.let {
                                log("Insert song: $it")
                            }
                    }
                } else if (playlistEntity != null) {
                    _playlistEntity.value = playlistEntity
                    playlistRepository.updatePlaylistInLibrary(
                        playlistId = id,
                        inLibrary = now(),
                    )
                    _uiState.value =
                        Success(
                            data =
                                PlaylistState(
                                    id = playlistEntity.id,
                                    title = playlistEntity.title,
                                    isRadio = false,
                                    author =
                                        Author(
                                            id = "",
                                            name = playlistEntity.author ?: "",
                                        ),
                                    thumbnail = playlistEntity.thumbnails,
                                    description = playlistEntity.description,
                                    trackCount = playlistEntity.trackCount,
                                    year = playlistEntity.year ?: now().year.toString(),
                                ),
                        )
                    _tracksListState.value = ListState.LOADING
                    playlistEntity.tracks?.let {
                        songRepository
                            .getSongsByListVideoId(it)
                            .singleOrNull()
                            ?.let { song ->
                                _tracks.value = song.map { it.toTrack() }
                            }
                    }
                    _tracksListState.value = ListState.PAGINATION_EXHAUST
                    if (playlistEntity.downloadState != STATE_DOWNLOADED) {
                        checkDownloadedPlaylist =
                            launch {
                                val listSong =
                                    songRepository
                                        .getSongsByListVideoId(
                                            playlistEntity.tracks ?: emptyList(),
                                        ).firstOrNull()
                                Logger.d(tag, "List song: $listSong")
                                if (!listSong.isNullOrEmpty() && listSong.size == playlistEntity.tracks?.size &&
                                    listSong.all {
                                        it.downloadState == STATE_DOWNLOADED
                                    }
                                ) {
                                    updatePlaylistDownloadState(playlistEntity.id, STATE_DOWNLOADED)
                                }
                            }
                    }
                } else {
                    _uiState.value = Error("Empty response")
                }
            }
    }

    fun setBrush(listColors: List<Color>) {
        _listColors.value = listColors
    }

    fun updatePlaylistLiked(
        liked: Boolean,
        id: String,
    ) {
        // 收藏心=云端账号状态:直接转发云端切换,本地行随结果镜像
        setRemoteSaved(liked)
    }

    fun onUIEvent(event: PlaylistUIEvent) {
        val data = uiState.value.data ?: return
        when (event) {
            is PlaylistUIEvent.ItemClick -> {
                val videoId = event.videoId
                val loadedList = tracks.value
                val clickedSong = loadedList.first { it.videoId == videoId }
                val index = loadedList.indexOf(clickedSong)
                setQueueData(
                    QueueData.Data(
                        listTracks = loadedList.toCollection(arrayListOf<Track>()),
                        firstPlayedTrack = clickedSong,
                        playlistId = data.id,
                        playlistName = "${
                            getString(
                                Res.string.playlist,
                            )
                        } \"${data.title}\"",
                        playlistType = PlaylistType.PLAYLIST,
                        continuation = continuation.value,
                    ),
                )
                loadMediaItem(
                    clickedSong,
                    Config.PLAYLIST_CLICK,
                    index,
                )
            }

            PlaylistUIEvent.PlayAll -> {
                val loadedList = tracks.value
                if (loadedList.isEmpty()) {
                    makeToast(
                        getString(Res.string.playlist_is_empty),
                    )
                    return
                }
                val clickedSong = loadedList.first()
                setQueueData(
                    QueueData.Data(
                        listTracks = loadedList.toCollection(arrayListOf<Track>()),
                        firstPlayedTrack = clickedSong,
                        playlistId = data.id,
                        playlistName = "${
                            getString(
                                Res.string.playlist,
                            )
                        } \"${data.title}\"",
                        playlistType = PlaylistType.PLAYLIST,
                        continuation = continuation.value,
                    ),
                )
                loadMediaItem(
                    clickedSong,
                    Config.PLAYLIST_CLICK,
                    0,
                )
            }

            PlaylistUIEvent.Shuffle -> {
                val shuffleEndpoint = data.shuffleEndpoint
                if (shuffleEndpoint == null && data.id.toLongOrNull() != null) {
                    // 网易歌单没有 YT 的 shuffleEndpoint:本地洗牌已加载曲目直接起播
                    // (与艺人页随机播放同款,35c997bd);分页未拉完先洗已加载部分,
                    // 后续仍走队列 continuation 续拉
                    val loaded = tracks.value
                    if (loaded.isEmpty()) {
                        makeToast(getString(Res.string.playlist_is_empty))
                        return
                    }
                    val shuffled = loaded.shuffled()
                    setQueueData(
                        QueueData.Data(
                            listTracks = shuffled.toCollection(arrayListOf<Track>()),
                            firstPlayedTrack = shuffled.first(),
                            playlistId = data.id,
                            playlistName = "\"${data.title}\" ${getString(Res.string.shuffle)}",
                            playlistType = PlaylistType.PLAYLIST,
                            continuation = continuation.value,
                        ),
                    )
                    loadMediaItem(shuffled.first(), Config.PLAYLIST_CLICK, 0)
                    return
                }
                if (shuffleEndpoint == null) {
                    makeToast(
                        getString(Res.string.shuffle_not_available),
                    )
                    return
                } else {
                    viewModelScope.launch {
                        songRepository.getRadioFromEndpoint(shuffleEndpoint).collectLatest { res ->
                            val result = res.data
                            when (res) {
                                is Resource.Success if (result != null) -> {
                                    Logger.d(tag, "Shuffle data: ${result.first.size}")
                                    setQueueData(
                                        QueueData.Data(
                                            listTracks = result.first.toCollection(arrayListOf<Track>()),
                                            firstPlayedTrack = result.first.firstOrNull() ?: return@collectLatest,
                                            playlistId = shuffleEndpoint.playlistId,
                                            playlistName = "\"${data.title}\" ${getString(Res.string.shuffle)}",
                                            playlistType = PlaylistType.RADIO,
                                            continuation = result.second,
                                        ),
                                    )
                                    loadMediaItem(
                                        result.first.firstOrNull() ?: return@collectLatest,
                                        Config.RADIO_CLICK,
                                        0,
                                    )
                                }

                                else -> {
                                    makeToast(
                                        res.message ?: getString(Res.string.error),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            PlaylistUIEvent.StartRadio -> {
                val radioEndpoint = data.radioEndpoint
                if (radioEndpoint == null) {
                    makeToast(
                        getString(Res.string.radio_not_available),
                    )
                    return
                } else {
                    viewModelScope.launch {
                        songRepository.getRadioFromEndpoint(radioEndpoint).collectLatest { res ->
                            val result = res.data
                            when (res) {
                                is Resource.Success if (result != null) -> {
                                    Logger.d(tag, "Radio data: ${result.first.size}")
                                    setQueueData(
                                        QueueData.Data(
                                            listTracks = result.first.toCollection(arrayListOf<Track>()),
                                            firstPlayedTrack = result.first.firstOrNull() ?: return@collectLatest,
                                            playlistId = radioEndpoint.playlistId,
                                            playlistName = "\"${data.title}\" ${getString(Res.string.radio)}",
                                            playlistType = PlaylistType.RADIO,
                                            continuation = result.second,
                                        ),
                                    )
                                    loadMediaItem(
                                        result.first.firstOrNull() ?: return@collectLatest,
                                        Config.RADIO_CLICK,
                                        0,
                                    )
                                }

                                else -> {
                                    makeToast(
                                        res.message ?: getString(Res.string.error),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            PlaylistUIEvent.Download -> {
                downloadFullPlaylist()
            }

            PlaylistUIEvent.Favorite -> {
                updatePlaylistLiked(!liked.value, data.id)
            }
        }
    }

    fun getFullTracks(callback: (List<Track>) -> Unit) {
        viewModelScope.launch {
            if (tracksListState.value == ListState.PAGINATION_EXHAUST) {
                _playlistEntity.value
                    ?.copy(
                        tracks = tracks.value.toListVideoId(),
                        trackCount = tracks.value.size,
                    )?.let {
                        playlistRepository.insertAndReplacePlaylist(it)
                    }
                callback(tracks.value)
            } else {
                val id = uiState.value.data?.id ?: return@launch
                tracksListState.collectLatest { state ->
                    if (state == ListState.PAGINATION_EXHAUST) {
                        _playlistEntity.value
                            ?.copy(
                                tracks = tracks.value.toListVideoId(),
                                trackCount = tracks.value.size,
                            )?.let {
                                playlistRepository.insertAndReplacePlaylist(it)
                            }
                        callback(tracks.value)
                    } else if (state != ListState.PAGINATING) {
                        getContinuationTrack(id, continuation.value)
                    }
                }
            }
        }
    }

    fun downloadFullPlaylist() {
        viewModelScope.launch {
            val id = playlistEntity.value?.id ?: return@launch
            makeToast(getString(Res.string.downloading))
            updatePlaylistDownloadState(id, STATE_DOWNLOADING)
            getFullTracks { tracks ->
                tracks.forEach {
                    viewModelScope.launch {
                        downloadUtils.downloadTrack(it.videoId, it.title, it.thumbnails?.lastOrNull()?.url ?: "")
                    }
                }
            }
        }
    }

    /**
     * Mirror of [downloadFullPlaylist]: reset the playlist's state first so the init-block watcher
     * (which re-downloads missing tracks while the playlist claims to be downloaded) cancels,
     * then drop every track's DownloadManager entry and DB state.
     */
    fun removeDownloadedPlaylist() {
        viewModelScope.launch {
            val id = playlistEntity.value?.id ?: return@launch
            updatePlaylistDownloadState(id, STATE_NOT_DOWNLOADED)
            // Same beat as LocalPlaylistViewModel: let the NOT_DOWNLOADED flip reach the init
            // watcher (which would otherwise re-queue missing tracks) before rows change.
            delay(500)
            getFullTracks { tracks ->
                // Only songs no other downloaded container references are actually removed.
                viewModelScope.launch {
                    removeExclusiveTrackDownloads(
                        tracks = tracks.toListVideoId(),
                        songRepository = songRepository,
                        downloadUtils = downloadUtils,
                        playlistRepository = playlistRepository,
                        albumRepository = albumRepository,
                        localPlaylistRepository = localPlaylistRepository,
                    )
                }
            }
            makeToast(getString(Res.string.removed_download))
        }
    }

    /**
     * Stop an in-flight full-playlist download from the header button: reset the playlist first
     * (kills the init watcher), then cancel only tracks still preparing/downloading — songs that
     * already finished keep their files, so resuming later only fetches the gap.
     */
    fun cancelDownloadingPlaylist() {
        viewModelScope.launch {
            val id = playlistEntity.value?.id ?: return@launch
            updatePlaylistDownloadState(id, STATE_NOT_DOWNLOADED)
            delay(500)
            getFullTracks { tracks ->
                viewModelScope.launch {
                    songRepository.getSongsByListVideoId(tracks.toListVideoId()).firstOrNull()?.forEach { song ->
                        if (song.downloadState == STATE_PREPARING || song.downloadState == STATE_DOWNLOADING) {
                            downloadUtils.removeDownload(song.videoId)
                            songRepository.updateDownloadState(song.videoId, STATE_NOT_DOWNLOADED)
                        }
                    }
                }
            }
            makeToast(getString(Res.string.download_cancelled))
        }
    }

    fun updatePlaylistTitle(
        title: String,
        id: String,
    ) {
        viewModelScope.launch {
            playlistRepository
                .updateYourYouTubePlaylistTitle(
                    id,
                    title,
                ).collect {
                    when (it) {
                        is Resource.Success -> {
                            getData(id)
                            makeToast(it.data ?: getString(Res.string.synced))
                        }

                        is Resource.Error -> {
                            makeToast(it.message ?: getString(Res.string.error))
                        }
                    }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        collectDownloadedJob?.cancel()
        playlistEntityJob?.cancel()
    }
}

sealed class PlaylistUIState(
    val data: PlaylistState? = null,
    val message: String? = null,
) {
    data object Loading : PlaylistUIState()

    class Success(
        data: PlaylistState,
    ) : PlaylistUIState(
            data = data,
        )

    class Error(
        message: String? = null,
    ) : PlaylistUIState(
            message = message,
        )
}

sealed class PlaylistUIEvent {
    data object PlayAll : PlaylistUIEvent()

    data object Shuffle : PlaylistUIEvent()

    data object StartRadio : PlaylistUIEvent()

    data class ItemClick(
        val videoId: String,
    ) : PlaylistUIEvent()

    data object Favorite : PlaylistUIEvent()

    data object Download : PlaylistUIEvent()
}

enum class ListState {
    IDLE,
    LOADING,
    PAGINATING,
    ERROR,
    PAGINATION_EXHAUST,
}
