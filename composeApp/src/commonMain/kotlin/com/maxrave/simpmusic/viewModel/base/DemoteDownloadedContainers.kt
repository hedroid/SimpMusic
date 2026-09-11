package com.maxrave.simpmusic.viewModel.base

import com.maxrave.domain.data.entities.AlbumEntity
import com.maxrave.domain.data.entities.DownloadState
import com.maxrave.domain.data.entities.PlaylistEntity
import com.maxrave.domain.mediaservice.handler.DownloadHandler
import com.maxrave.domain.repository.AlbumRepository
import com.maxrave.domain.repository.LocalPlaylistRepository
import com.maxrave.domain.repository.PlaylistRepository
import com.maxrave.domain.repository.SongRepository
import kotlinx.coroutines.flow.first

/**
 * Demote every downloaded playlist/album that references [videoId] to NOT_DOWNLOADED, for use
 * right after that song's own download was removed.
 *
 * Without this the container still claims to be fully downloaded, and its re-download watcher
 * queues the song right back the moment its screen is open — making the removal look like it
 * never happened. Its other tracks keep their files; re-downloading the container only fills the
 * gap.
 */
suspend fun demoteDownloadedContainers(
    videoId: String,
    playlistRepository: PlaylistRepository,
    albumRepository: AlbumRepository,
    localPlaylistRepository: LocalPlaylistRepository,
) {
    playlistRepository.getAllDownloadedPlaylist().first().forEach { container ->
        when (container) {
            is PlaylistEntity -> {
                if (container.tracks?.contains(videoId) == true) {
                    playlistRepository.updatePlaylistDownloadState(container.id, DownloadState.STATE_NOT_DOWNLOADED)
                }
            }

            is AlbumEntity -> {
                if (container.tracks?.contains(videoId) == true) {
                    albumRepository.updateAlbumDownloadState(container.browseId, DownloadState.STATE_NOT_DOWNLOADED)
                }
            }

            else -> {}
        }
    }
    localPlaylistRepository.getDownloadedLocalPlaylists().first().forEach { playlist ->
        if (playlist.tracks?.contains(videoId) == true) {
            localPlaylistRepository.updateLocalPlaylistDownloadState(DownloadState.STATE_NOT_DOWNLOADED, playlist.id)
        }
    }
}

/**
 * Remove the downloads of the songs one container owns exclusively — the payload of "remove this
 * playlist's download".
 *
 * Songs any OTHER downloaded playlist/album still references keep their files: deleting container
 * A must not silently break container B's offline copy (and B's re-download watcher would queue
 * them right back, burning data the user never asked for).
 *
 * Must be called AFTER the container itself was reset to NOT_DOWNLOADED: the shared set is read
 * off "all downloaded containers", which by then no longer includes it.
 */
suspend fun removeExclusiveTrackDownloads(
    tracks: List<String>,
    songRepository: SongRepository,
    downloadUtils: DownloadHandler,
    playlistRepository: PlaylistRepository,
    albumRepository: AlbumRepository,
    localPlaylistRepository: LocalPlaylistRepository,
) {
    if (tracks.isEmpty()) return
    val shared = buildSet {
        playlistRepository.getAllDownloadedPlaylist().first().forEach { container ->
            when (container) {
                is PlaylistEntity -> container.tracks?.let(::addAll)
                is AlbumEntity -> container.tracks?.let(::addAll)
                else -> {}
            }
        }
        localPlaylistRepository.getDownloadedLocalPlaylists().first().forEach { playlist ->
            playlist.tracks?.let(::addAll)
        }
    }
    tracks.forEach { videoId ->
        if (videoId !in shared) {
            downloadUtils.removeDownload(videoId)
            songRepository.updateDownloadState(videoId, DownloadState.STATE_NOT_DOWNLOADED)
        }
    }
}
