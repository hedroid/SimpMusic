package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.viewModelScope
import com.maxrave.common.Config
import com.maxrave.data.repository.NeteaseRepositoryImpl
import com.maxrave.domain.data.model.browse.artist.ResultSong
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.utils.toTrack
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.error

/** 网易单曲"相似歌曲"分页页(SimilarSongsScreen):/v1/discovery/simiSong offset 分页,
 *  近底追加;翻页撞重复(整页无新增)即收尾,防 offset 死循环。 */
class SimilarSongsViewModel(
    private val neteaseRepository: NeteaseRepositoryImpl,
    private val sharedViewModel: SharedViewModel,
) : BaseViewModel() {
    private val _uiState = MutableStateFlow<SimilarSongsUIState>(SimilarSongsUIState.Loading)
    val uiState: StateFlow<SimilarSongsUIState> get() = _uiState

    private var loadedSongs: List<ResultSong> = emptyList()
    private var songId: String = ""
    private var songTitle: String = ""

    fun load(
        id: String,
        title: String,
    ) {
        songId = id
        songTitle = title
        viewModelScope.launch {
            _uiState.value = SimilarSongsUIState.Loading
            neteaseRepository.getSimilarSongsPage(id, offset = 0).fold(
                onSuccess = { (songs, hasMore) ->
                    loadedSongs = songs
                    _uiState.value =
                        if (songs.isEmpty()) {
                            SimilarSongsUIState.Error(message = getString(Res.string.error))
                        } else {
                            SimilarSongsUIState.Success(songs = songs, hasMore = hasMore, loadingMore = false)
                        }
                },
                onFailure = {
                    _uiState.value = SimilarSongsUIState.Error(message = getString(Res.string.error))
                },
            )
        }
    }

    fun loadMore() {
        val state = _uiState.value as? SimilarSongsUIState.Success ?: return
        if (!state.hasMore || state.loadingMore) return
        _uiState.value = state.copy(loadingMore = true)
        viewModelScope.launch {
            neteaseRepository
                .getSimilarSongsPage(songId, offset = loadedSongs.size)
                .fold(
                    onSuccess = { (songs, hasMore) ->
                        val fresh = songs.filterNot { new -> loadedSongs.any { it.videoId == new.videoId } }
                        // 整页无新增=服务端在重复发同一批,收尾防 offset 死循环
                        loadedSongs = loadedSongs + fresh
                        _uiState.value =
                            SimilarSongsUIState.Success(
                                songs = loadedSongs,
                                hasMore = hasMore && fresh.isNotEmpty(),
                                loadingMore = false,
                            )
                    },
                    onFailure = {
                        _uiState.value = state.copy(loadingMore = false)
                    },
                )
        }
    }

    /** 点歌=整页列表从点击首起播(普通队列播完即止;"继续发现"走三点菜单的电台入口) */
    fun playFrom(index: Int) {
        val tracks = loadedSongs.map { it.toTrack() }
        val first = tracks.getOrNull(index) ?: return
        setQueueData(
            QueueData.Data(
                listTracks = ArrayList(tracks),
                firstPlayedTrack = first,
                playlistId = songId,
                playlistName = songTitle,
                playlistType = PlaylistType.PLAYLIST,
                continuation = null,
            ),
        )
        sharedViewModel.loadMediaItemFromTrack(first, Config.PLAYLIST_CLICK, index)
    }
}

sealed class SimilarSongsUIState {
    data class Success(
        val songs: List<ResultSong>,
        val hasMore: Boolean,
        val loadingMore: Boolean,
    ) : SimilarSongsUIState()

    data class Error(
        val message: String,
    ) : SimilarSongsUIState()

    data object Loading : SimilarSongsUIState()
}
