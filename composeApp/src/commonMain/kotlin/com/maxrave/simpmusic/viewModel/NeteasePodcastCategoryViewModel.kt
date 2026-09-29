package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.viewModelScope
import com.maxrave.common.Config
import com.maxrave.data.repository.NeteaseRepositoryImpl
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.netease.model.NeteaseDjRadio
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 网易云播客分类电台列表页(分类 chips 进入,/djradio/hot offset 分页,近底追加)。entry-scope */
class NeteasePodcastCategoryViewModel(
    private val neteaseRepository: NeteaseRepositoryImpl,
) : BaseViewModel() {
    data class UiState(
        val radios: List<NeteaseDjRadio> = emptyList(),
        val loading: Boolean = true,
        val loadingMore: Boolean = false,
        val hasMore: Boolean = false,
        val unavailable: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> get() = _uiState.asStateFlow()

    private var categoryId = 0L

    fun load(id: Long) {
        if (categoryId == id && !_uiState.value.loading) return
        categoryId = id
        _uiState.update { it.copy(loading = true, unavailable = false) }
        viewModelScope.launch { loadPage() }
    }

    fun retry() {
        _uiState.update { it.copy(loading = true, unavailable = false) }
        viewModelScope.launch { loadPage() }
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.hasMore || state.loadingMore || state.loading) return
        _uiState.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            neteaseRepository
                .getDjRadiosByCategory(categoryId, offset = state.radios.size)
                .fold(
                    onSuccess = { (radios, more) ->
                        _uiState.update { current ->
                            val fresh = radios.filterNot { r -> current.radios.any { it.id == r.id } }
                            current.copy(
                                radios = current.radios + fresh,
                                hasMore = more && fresh.isNotEmpty(),
                                loadingMore = false,
                            )
                        }
                    },
                    onFailure = {
                        _uiState.update { it.copy(loadingMore = false) }
                    },
                )
        }
    }

    private suspend fun loadPage() {
        neteaseRepository.getDjRadiosByCategory(categoryId, offset = 0).fold(
            onSuccess = { (radios, more) ->
                _uiState.update { it.copy(radios = radios, hasMore = more, loading = false) }
            },
            onFailure = {
                log("category radios failed: $it")
                _uiState.update { it.copy(loading = false, unavailable = true) }
            },
        )
    }
}
