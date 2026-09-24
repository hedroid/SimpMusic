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

/** 网易艺人"全部歌曲"分页页(MoreSongsScreen):/v1/artist/songs 原生分页,
 *  热门/最新两档排序切换,近底追加。艺人页人气区只展示热门 50,这里给全量。 */
class MoreSongsViewModel(
    private val neteaseRepository: NeteaseRepositoryImpl,
    private val sharedViewModel: SharedViewModel,
) : BaseViewModel() {
    private val _uiState = MutableStateFlow<MoreSongsUIState>(MoreSongsUIState.Loading)
    val uiState: StateFlow<MoreSongsUIState> get() = _uiState

    /** 列表快照:切排序/进页面重建时供点击起播拿"整队" */
    private var loadedSongs: List<ResultSong> = emptyList()
    private var artistId: String = ""
    private var artistName: String = ""

    fun load(
        id: String,
        name: String,
        order: String = ORDER_HOT,
    ) {
        artistId = id
        artistName = name
        viewModelScope.launch {
            _uiState.value = MoreSongsUIState.Loading
            neteaseRepository.getArtistSongsPage(id, order = order, offset = 0).fold(
                onSuccess = { (songs, hasMore) ->
                    loadedSongs = songs
                    _uiState.value =
                        if (songs.isEmpty()) {
                            MoreSongsUIState.Error(message = getString(Res.string.error))
                        } else {
                            MoreSongsUIState.Success(
                                title = name,
                                order = order,
                                songs = songs,
                                hasMore = hasMore,
                                loadingMore = false,
                            )
                        }
                },
                onFailure = {
                    _uiState.value = MoreSongsUIState.Error(message = getString(Res.string.error))
                },
            )
        }
    }

    fun setOrder(order: String) {
        val state = _uiState.value as? MoreSongsUIState.Success ?: return
        if (state.order == order) return
        load(artistId, artistName, order)
    }

    fun loadMore() {
        val state = _uiState.value as? MoreSongsUIState.Success ?: return
        if (!state.hasMore || state.loadingMore) return
        _uiState.value = state.copy(loadingMore = true)
        viewModelScope.launch {
            neteaseRepository.getArtistSongsPage(
                artistId,
                order = state.order,
                offset = loadedSongs.size,
            ).fold(
                onSuccess = { (songs, hasMore) ->
                    loadedSongs = loadedSongs + songs
                    _uiState.value =
                        MoreSongsUIState.Success(
                            title = artistName,
                            order = state.order,
                            songs = loadedSongs,
                            hasMore = hasMore,
                            loadingMore = false,
                        )
                },
                onFailure = {
                    // 追加失败回退到可再试状态,不整页转 Error(已加载内容保持)
                    _uiState.value = state.copy(loadingMore = false)
                },
            )
        }
    }

    /** 点歌=整队起播(从点击首开始):队列名带艺人名,与艺人页人气区"列表语义"对齐 */
    fun playFrom(index: Int) {
        val tracks = loadedSongs.map { it.toTrack() }
        val first = tracks.getOrNull(index) ?: return
        setQueueData(
            QueueData.Data(
                listTracks = ArrayList(tracks),
                firstPlayedTrack = first,
                playlistId = artistId,
                playlistName = artistName,
                playlistType = PlaylistType.PLAYLIST,
                continuation = null,
            ),
        )
        sharedViewModel.loadMediaItemFromTrack(first, Config.PLAYLIST_CLICK, index)
    }

    companion object {
        const val ORDER_HOT = "hot"
        const val ORDER_TIME = "time"
    }
}

sealed class MoreSongsUIState {
    data class Success(
        val title: String,
        val order: String,
        val songs: List<ResultSong>,
        val hasMore: Boolean,
        val loadingMore: Boolean,
    ) : MoreSongsUIState()

    data class Error(
        val message: String,
    ) : MoreSongsUIState()

    data object Loading : MoreSongsUIState()
}
