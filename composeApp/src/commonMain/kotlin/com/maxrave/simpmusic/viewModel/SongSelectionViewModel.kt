package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.viewModelScope
import com.maxrave.domain.data.entities.DownloadState
import com.maxrave.domain.data.entities.LocalPlaylistEntity
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.mediaservice.handler.DownloadHandler
import com.maxrave.domain.repository.AlbumRepository
import com.maxrave.domain.repository.LocalPlaylistRepository
import com.maxrave.domain.repository.PlaylistRepository
import com.maxrave.domain.repository.SongRepository
import com.maxrave.domain.utils.collectResource
import com.maxrave.domain.utils.toTrack
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import com.maxrave.simpmusic.viewModel.base.demoteDownloadedContainers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.inject
import simpmusic.composeapp.generated.resources.synced_n_of_m
import simpmusic.composeapp.generated.resources.netease_rate_limited
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.added_to_playlist
import simpmusic.composeapp.generated.resources.added_to_queue
import simpmusic.composeapp.generated.resources.delete_song_from_playlist
import simpmusic.composeapp.generated.resources.downloading
import simpmusic.composeapp.generated.resources.error
import simpmusic.composeapp.generated.resources.error_occurred
import simpmusic.composeapp.generated.resources.play_next
import simpmusic.composeapp.generated.resources.removed_download
import simpmusic.composeapp.generated.resources.removed_from_YouTube_playlist

/**
 * Runs the bulk actions offered by
 * [com.maxrave.simpmusic.ui.component.selection.SelectedSongsBottomSheet].
 *
 * Shared by every screen that offers multi-selection: the actions only ever need videoIds, so
 * they do not depend on which screen the selection was made on. The one exception is removing
 * from a local playlist, which takes the playlist id from the screen.
 */
class SongSelectionViewModel(
    private val songRepository: SongRepository,
    private val localPlaylistRepository: LocalPlaylistRepository,
) : BaseViewModel() {
    private val downloadUtils: DownloadHandler by inject()
    private val playlistRepository: PlaylistRepository by inject()
    private val albumRepository: AlbumRepository by inject()

    val listLocalPlaylist: StateFlow<List<LocalPlaylistEntity>> =
        localPlaylistRepository
            .getAllLocalPlaylists()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Whether EVERY song in the last checked selection is on disk, recomputed by
     * [checkAllDownloaded] when a screen opens its selection sheet. Drives the sheet's download
     * row: all downloaded → "downloaded" (blue, removes the batch); otherwise → plain "download"
     * that only fills in what is missing.
     */
    private val _allSelectedDownloaded = MutableStateFlow(false)
    val allSelectedDownloaded: StateFlow<Boolean> = _allSelectedDownloaded.asStateFlow()

    fun checkAllDownloaded(videoIds: List<String>) {
        viewModelScope.launch {
            val songs = songsOf(videoIds)
            _allSelectedDownloaded.value =
                songs.isNotEmpty() && songs.all { it.downloadState == DownloadState.STATE_DOWNLOADED }
        }
    }

    /**
     * Each call inserts right after the current track, so playing them in order would leave the
     * queue reversed — the last one inserted ends up first. Walking the list backwards puts them
     * in the order the user saw them.
     */
    fun playNext(videoIds: List<String>) {
        viewModelScope.launch {
            val songs = songsOf(videoIds)
            if (songs.isEmpty()) {
                makeToast(getString(Res.string.error_occurred))
                return@launch
            }
            songs.asReversed().forEach { mediaPlayerHandler.playNext(it.toTrack()) }
            makeToast(getString(Res.string.play_next))
        }
    }

    fun addToQueue(videoIds: List<String>) {
        viewModelScope.launch {
            val songs = songsOf(videoIds)
            if (songs.isEmpty()) {
                makeToast(getString(Res.string.error_occurred))
                return@launch
            }
            mediaPlayerHandler.loadMoreCatalog(
                ArrayList(songs.map { it.toTrack() }),
                isAddToQueue = true,
            )
            makeToast(getString(Res.string.added_to_queue))
        }
    }

    /**
     * Only starts songs that are not already downloaded or in flight — unlike the single-song
     * menu, this is not a toggle: a selection of 25 will usually mix downloaded and not, and
     * toggling would silently delete the ones already on disk.
     */
    fun download(videoIds: List<String>) {
        viewModelScope.launch {
            val pending =
                songsOf(videoIds).filter {
                    it.downloadState == DownloadState.STATE_NOT_DOWNLOADED
                }
            if (pending.isEmpty()) return@launch
            pending.forEach { song ->
                songRepository.updateDownloadState(
                    videoId = song.videoId,
                    downloadState = DownloadState.STATE_PREPARING,
                )
                downloadUtils.downloadTrack(
                    videoId = song.videoId,
                    title = song.title,
                    thumbnail = song.thumbnails ?: "",
                )
            }
            makeToast(getString(Res.string.downloading))
        }
    }

    /**
     * The removal counterpart of [download], offered by the downloaded-songs grid where every
     * selection is already on disk: drops the selected downloads and demotes the containers that
     * referenced them, so their re-download watchers do not queue the songs right back.
     */
    fun removeDownload(videoIds: List<String>) {
        viewModelScope.launch {
            val downloaded =
                songsOf(videoIds).filter { it.downloadState == DownloadState.STATE_DOWNLOADED }
            if (downloaded.isEmpty()) return@launch
            downloaded.forEach { song ->
                // Demote before deleting, same as the single-song menu: a container still
                // claiming "downloaded" would re-queue the song the moment it vanishes.
                demoteDownloadedContainers(song.videoId, playlistRepository, albumRepository, localPlaylistRepository)
                downloadUtils.removeDownload(song.videoId)
                songRepository.updateDownloadState(song.videoId, DownloadState.STATE_NOT_DOWNLOADED)
            }
            makeToast(getString(Res.string.removed_download))
        }
    }

    fun addToFavorite(videoIds: List<String>) {
        viewModelScope.launch {
            // 点赞=云端账号红心;本地行随结果镜像,失败计入汇总
            var succeeded = 0
            var attempted = 0
            songsOf(videoIds)
                .filterNot { it.liked }
                .forEach { song ->
                    attempted++
                    val result = songRepository.setRemoteLikeStatus(song.videoId, true)
                    if (result.exceptionOrNull() is com.maxrave.netease.NeteaseRateLimitException) {
                        // 405 频控窗口内整批都会失败,别把 N 条全戳一遍(重试会续期窗口)
                        makeToast(getString(Res.string.netease_rate_limited))
                        return@launch
                    }
                    if (result.getOrDefault(false)) {
                        songRepository.setLikedLocal(song.videoId, 1)
                        succeeded++
                    }
                }
            if (attempted > 0) {
                makeToast(org.jetbrains.compose.resources.getString(Res.string.synced_n_of_m, succeeded, attempted))
            }
        }
    }

    fun addToPlaylist(
        playlistId: Long,
        videoIds: List<String>,
    ) {
        viewModelScope.launch {
            val playlist = localPlaylistRepository.getAllLocalPlaylists().firstOrNull()?.find { it.id == playlistId }
            val already = playlist?.tracks.orEmpty()
            val toAdd = songsOf(videoIds).filterNot { already.contains(it.videoId) }
            if (toAdd.isEmpty()) return@launch
            var added = 0
            toAdd.forEach { song ->
                localPlaylistRepository
                    .addTrackToLocalPlaylist(
                        id = playlistId,
                        song = song,
                        successMessage = getString(Res.string.added_to_playlist),
                        updatedYtMessage = getString(Res.string.added_to_playlist),
                        errorMessage = getString(Res.string.error),
                    ).collectResource(
                        onSuccess = { added++ },
                        onError = { },
                    )
            }
            // One toast for the batch: 25 songs would otherwise stack 25 toasts.
            makeToast(
                if (added > 0) getString(Res.string.added_to_playlist) else getString(Res.string.error),
            )
        }
    }

    fun removeFromLocalPlaylist(
        playlistId: Long,
        videoIds: List<String>,
    ) {
        viewModelScope.launch {
            val songs = songsOf(videoIds)
            if (songs.isEmpty()) {
                makeToast(getString(Res.string.error_occurred))
                return@launch
            }
            songs.forEach { song ->
                localPlaylistRepository
                    .removeTrackFromLocalPlaylist(
                        id = playlistId,
                        song = song,
                        successMessage = getString(Res.string.delete_song_from_playlist),
                        updatedYtMessage = getString(Res.string.removed_from_YouTube_playlist),
                        errorMessage = getString(Res.string.error_occurred),
                    ).collectResource(
                        onSuccess = { },
                        onError = { },
                    )
            }
            makeToast(getString(Res.string.delete_song_from_playlist))
        }
    }

    /**
     * The database returns rows in its own order, so the result is re-sorted back into the order
     * the user picked them in — which is what makes "play next" land in the expected sequence.
     */
    private suspend fun songsOf(videoIds: List<String>): List<SongEntity> =
        songRepository
            .getSongsByListVideoId(videoIds)
            .firstOrNull()
            .orEmpty()
            .sortedBy { videoIds.indexOf(it.videoId) }
}
