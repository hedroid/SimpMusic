package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.viewModelScope
import com.maxrave.data.repository.NeteaseRepositoryImpl
import com.maxrave.netease.model.NeteaseDjRadio
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 网易云播客分类电台列表页(分类 chips 进入)。对齐官方分类页双 tab(2026-09-29):
 * **上升最快(type=0)/最热电台(type=1)**,/djradio/hot 分档名单完全不同(探针实证);
 * 两列网格近底分页。tab 按需拉取(切 tab 才请求该档),各自独立降级。
 * 官方顶部"优秀新电台"横滑暂缺端点(三个候选全 404),不做假区。
 */
class NeteasePodcastCategoryViewModel(
    private val neteaseRepository: NeteaseRepositoryImpl,
) : BaseViewModel() {
    /** 榜单分档(与 /djradio/hot 的 type 对齐) */
    enum class ChartTab(val endpointType: Int) {
        RISING(0),
        HOT(1),
    }

    data class ChartState(
        val radios: List<NeteaseDjRadio> = emptyList(),
        val loaded: Boolean = false,
        /** 首页在途(tab 按需拉取):切到未加载 tab 时置位,防重复发起+给 UI 渲染加载态
         *  (页面只看全局 loading,曾把"加载中"显示成"该分类暂无电台",五轮 CR) */
        val loading: Boolean = false,
        val hasMore: Boolean = false,
        val loadingMore: Boolean = false,
        val failed: Boolean = false,
    )

    data class UiState(
        val tab: ChartTab = ChartTab.RISING,
        val charts: Map<ChartTab, ChartState> =
            ChartTab.entries.associateWith { ChartState() },
        val loading: Boolean = true,
    ) {
        val current: ChartState get() = charts[tab] ?: ChartState()
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> get() = _uiState.asStateFlow()

    private var categoryId = 0L

    /** 换分类时递增:在途回包按代丢弃,防旧分类/旧档数据写进新状态(CR-24) */
    private var generation = 0L

    fun load(id: Long) {
        if (categoryId == id && _uiState.value.charts.any { it.value.loaded }) return
        generation++
        categoryId = id
        _uiState.update { it.copy(loading = true, charts = ChartTab.entries.associateWith { ChartState() }) }
        loadTab(ChartTab.RISING)
    }

    fun switchTab(tab: ChartTab) {
        if (_uiState.value.tab == tab) return
        _uiState.update { it.copy(tab = tab) }
        val chart = _uiState.value.charts[tab] ?: return
        // 快速来回切换对同一未加载 tab 只发一次首页请求(loading 在途即跳过)
        if (!chart.loaded && !chart.loading) {
            _uiState.update { s ->
                s.copy(charts = s.charts + (tab to chart.copy(loading = true)))
            }
            loadTab(tab)
        }
    }

    fun retry() {
        val tab = _uiState.value.tab
        val chart = _uiState.value.charts[tab] ?: return
        // 在途重试直接返回,防外部重复调用叠加首页请求
        if (chart.loading) return
        _uiState.update { state ->
            state.copy(charts = state.charts + (tab to chart.copy(failed = false, loading = true)))
        }
        loadTab(tab)
    }

    fun loadMore() {
        val state = _uiState.value
        // 请求瞬间捕获目标档,回包只写这一档——旧实现回包读 s.tab(响应时刻的当前档),
        // RISING 续页在飞时切到 HOT,数据会追加进 HOT 且 RISING 的 loadingMore 永久卡 true
        val requestedTab = state.tab
        val chart = state.current
        val gen = generation
        if (!chart.loaded || !chart.hasMore || chart.loadingMore) return
        _uiState.update { s ->
            s.copy(charts = s.charts + (requestedTab to chart.copy(loadingMore = true)))
        }
        viewModelScope.launch {
            neteaseRepository
                .getDjRadiosByCategory(categoryId, offset = chart.radios.size, type = requestedTab.endpointType)
                .fold(
                    onSuccess = { (radios, more) ->
                        _uiState.update { s ->
                            if (gen != generation) return@update s
                            val c = s.charts[requestedTab] ?: return@update s
                            val fresh = radios.filterNot { r -> c.radios.any { it.id == r.id } }
                            s.copy(
                                charts = s.charts + (requestedTab to c.copy(radios = c.radios + fresh, hasMore = more && fresh.isNotEmpty(), loadingMore = false)),
                            )
                        }
                    },
                    onFailure = {
                        _uiState.update { s ->
                            if (gen != generation) return@update s
                            val c = s.charts[requestedTab] ?: return@update s
                            s.copy(charts = s.charts + (requestedTab to c.copy(loadingMore = false)))
                        }
                    },
                )
        }
    }

    private fun loadTab(tab: ChartTab) {
        val gen = generation
        viewModelScope.launch {
            neteaseRepository
                .getDjRadiosByCategory(categoryId, offset = 0, type = tab.endpointType)
                .fold(
                    onSuccess = { (radios, more) ->
                        _uiState.update { s ->
                            if (gen != generation) return@update s
                            s.copy(
                                loading = false,
                                charts = s.charts + (tab to ChartState(radios = radios, loaded = true, hasMore = more)),
                            )
                        }
                    },
                    onFailure = {
                        log("category radios failed: $it")
                        _uiState.update { s ->
                            if (gen != generation) return@update s
                            s.copy(
                                loading = false,
                                charts = s.charts + (tab to (s.charts[tab] ?: ChartState()).copy(loaded = true, failed = true)),
                            )
                        }
                    },
                )
        }
    }
}
