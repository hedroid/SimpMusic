package com.maxrave.simpmusic.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.maxrave.domain.data.model.home.Content
import com.maxrave.simpmusic.extension.isScrollingUp
import com.maxrave.simpmusic.ui.component.Chip
import com.maxrave.simpmusic.ui.component.EndOfPage
import com.maxrave.simpmusic.ui.component.HomeShimmer
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.icon.Favorite
import com.maxrave.simpmusic.ui.icon.Pause
import com.maxrave.simpmusic.ui.icon.PlayArrow
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.NeteaseMixViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.netease_home_error
import simpmusic.composeapp.generated.resources.personal_fm
import simpmusic.composeapp.generated.resources.personal_fm_pause
import simpmusic.composeapp.generated.resources.personal_fm_resume
import simpmusic.composeapp.generated.resources.personal_fm_start
import simpmusic.composeapp.generated.resources.personal_fm_subtitle
import simpmusic.composeapp.generated.resources.retry

/**
 * 网易"混合"tab 独立屏(私人FM):挂载点按 selected_source 与上游 MixForYouScreen 二选一
 * (AppNavigationGraph 分流,同 HomeDestination 模式)。
 * 骨架对照 NeteaseHomeScreen(悬浮顶栏+PullToRefresh+Crossfade 状态机);内容区两块:
 * FM hero 大卡(开始收听)+ 扩容批次卡(3 批去重 ~9 首,NeteaseSongCard 复用),以及
 * 每日推荐 30 首(主页只有推荐歌单,歌曲是本页增量)。两块并行拉取,空块隐藏。
 * 下拉刷新=换一批;FM 整批入队播放,队列耗尽由 core 的 loadMore 哨兵分支自动续批;
 * 每日推荐独立起播、播完即止。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeteaseMixScreen(
    onScrolling: (onTop: Boolean) -> Unit = {},
    viewModel: NeteaseMixViewModel = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val heartLoading by viewModel.heartLoading.collectAsStateWithLifecycle()
    val fmLoadingMore by viewModel.fmLoadingMore.collectAsStateWithLifecycle()
    val expressLoading by viewModel.expressLoading.collectAsStateWithLifecycle()
    val playback by viewModel.playbackState.collectAsStateWithLifecycle()
    val scrollState = rememberLazyListState()
    val isScrollingUp by scrollState.isScrollingUp()
    val pullToRefreshState = rememberPullToRefreshState()
    var topAppBarHeightPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.firstVisibleItemIndex }
            .collect {
                if (it <= 1) {
                    onScrolling(true)
                } else {
                    onScrolling(isScrollingUp)
                }
            }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        PullToRefreshBox(
            state = pullToRefreshState,
            onRefresh = { viewModel.refresh(force = true) },
            // 指示器=手势已受理的短确认(≤600ms),不再绑定整页 Loading 态陪跑全部请求
            isRefreshing = refreshing,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = refreshing,
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(
                                top =
                                    with(LocalDensity.current) {
                                        topAppBarHeightPx.toDp()
                                    },
                            ),
                    containerColor = PullToRefreshDefaults.indicatorContainerColor,
                    color = PullToRefreshDefaults.indicatorColor,
                    maxDistance = PullToRefreshDefaults.PositionalThreshold,
                )
            },
        ) {
            // targetKey 用 state 的"类别"而非实例:Ready→Ready'(追加/垃圾桶的 copy)
            // 不触发过渡,内容原地重组——否则每次追加整页 Crossfade 闪一下。
            Crossfade(state::class) { _ ->
                when (val current = state) {
                    is NeteaseMixViewModel.State.Loading ->
                        Column {
                            Spacer(
                                Modifier.height(
                                    with(LocalDensity.current) {
                                        topAppBarHeightPx.toDp()
                                    },
                                ),
                            )
                            HomeShimmer()
                        }

                    is NeteaseMixViewModel.State.Error ->
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = stringResource(Res.string.netease_home_error),
                                style = typo().bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { viewModel.refresh(force = true) }) {
                                Text(stringResource(Res.string.retry))
                            }
                        }

                    is NeteaseMixViewModel.State.Ready -> {
                        val fmContents = current.fmContents
                        val dailyContents = current.dailyContents
                        val expressContents = current.expressContents
                        val expressArea = current.expressArea
                        val recentContents = current.recentContents
                        LazyColumn(
                            state = scrollState,
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            item(key = "top-space") {
                                Spacer(
                                    Modifier.height(
                                        with(LocalDensity.current) {
                                            topAppBarHeightPx.toDp()
                                        },
                                    ),
                                )
                            }
                            if (fmContents.isNotEmpty()) {
                                // FM hero:首曲封面大卡 + 开始收听
                                item(key = "hero") {
                                    val heroContent =
                                        fmContents.firstOrNull { it.thumbnails.any { thumb -> thumb.url.isNotBlank() } }
                                            ?: fmContents.first()
                                    FmHeroCard(
                                        content = heroContent,
                                        isFmActive = playback.isFmQueue,
                                        isPlaying = playback.isPlaying,
                                        onStart = { viewModel.playFrom(fmContents, 0) },
                                        onToggle = viewModel::togglePlayback,
                                    )
                                }
                                // 红心电台入口:红心随机起播,播完接 FM 续批
                                item(key = "heart-radio") {
                                    HeartRadioCard(
                                        loading = heartLoading,
                                        isHeartActive = playback.isHeartQueue,
                                        isPlaying = playback.isPlaying,
                                        onClick = viewModel::playHeartRadio,
                                        onToggle = viewModel::togglePlayback,
                                    )
                                }
                                // FM 当前批次:单行横滑(主页歌单行同款排版),滑到尾自动拉下批
                                item(key = "fm-batch") {
                                    FmSongRow(
                                        contents = fmContents,
                                        loadingMore = fmLoadingMore,
                                        onTrash = viewModel::trashFm,
                                        onLoadMore = viewModel::loadMoreFm,
                                    ) { index ->
                                        viewModel.playFrom(fmContents, index)
                                    }
                                }
                            }
                            if (dailyContents.isNotEmpty()) {
                                // 每日推荐 30 首:主页只放了推荐歌单,这批歌是本页增量
                                item(key = "daily-header") {
                                    SectionHeader("每日推荐")
                                }
                                item(key = "daily-grid") {
                                    FmSongGrid(contents = dailyContents) { index ->
                                        viewModel.playSectionFrom(dailyContents, index, "每日推荐")
                                    }
                                }
                            }
                            if (expressContents.isNotEmpty() || expressLoading) {
                                // 新歌速递:编辑性新歌(与主页"推荐新歌"个性化不同源),地区 chips
                                item(key = "express-header") {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 15.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        SectionHeader("新歌速递")
                                        if (expressLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        }
                                    }
                                }
                                item(key = "express-chips") {
                                    ExpressAreaChips(
                                        selected = expressArea,
                                        enabled = !expressLoading,
                                    ) { area -> viewModel.loadExpress(area) }
                                }
                                if (expressContents.isNotEmpty()) {
                                    item(key = "express-grid") {
                                        FmSongGrid(contents = expressContents) { index ->
                                            viewModel.playSectionFrom(expressContents, index, "新歌速递")
                                        }
                                    }
                                }
                            }
                            if (recentContents.isNotEmpty()) {
                                // 最近在听:网易侧听歌周榜(按播放次数降序),FM 同款单行两列排版
                                item(key = "recent-header") {
                                    SectionHeader("最近在听")
                                }
                                item(key = "recent-row") {
                                    FmSongRow(contents = recentContents) { index ->
                                        viewModel.playSectionFrom(recentContents, index, "最近在听")
                                    }
                                }
                            }
                            item(key = "end") { EndOfPage() }
                        }
                    }
                }
            }
        }
        // 悬浮顶栏(对照 NeteaseHomeScreen:置顶透明、滚动后 surfaceContainer)
        AnimatedContent(
            targetState = scrollState.firstVisibleItemIndex == 0 && scrollState.firstVisibleItemScrollOffset == 0,
            transitionSpec = { fadeIn(tween(300)).togetherWith(fadeOut(tween(300))) },
        ) { target ->
            Column(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .then(
                            if (target) {
                                Modifier.background(Color.Transparent)
                            } else {
                                Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
                            },
                        ).onGloballyPositioned { coordinates ->
                            topAppBarHeightPx = coordinates.size.height
                        },
            ) {
                Spacer(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars),
                )
                Text(
                    text = stringResource(Res.string.personal_fm),
                    style = typo().headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** 每日推荐三行紧凑卡网格(与主页歌曲行同款) */
@Composable
private fun FmSongGrid(    contents: List<Content>,
    onPlay: (index: Int) -> Unit,
) {
    val gridState = rememberLazyGridState()
    LazyHorizontalGrid(
        rows = GridCells.Fixed(3),
        modifier =
            Modifier
                .padding(horizontal = 15.dp)
                .height(460.dp),
        state = gridState,
    ) {
        gridItemsIndexed(contents, key = { _, content -> content.videoId ?: content.title }) { index, content ->
            NeteaseSongCard(
                content = content,
                onClick = { onPlay(index) },
            )
        }
    }
}

/** 单行两列横滑卡(主页"每日推荐歌单"行同款排版,190dp 大卡)。FM 用完整形态:
 *  滑到尾自动拉下一批(onLoadMore)+卡带垃圾桶角标;最近在听复用排版,两个参数
 *  传 null 即无角标无增量(数据一次到位)。 */
@Composable
private fun FmSongRow(
    contents: List<Content>,
    loadingMore: Boolean = false,
    onTrash: ((Content) -> Unit)? = null,
    onLoadMore: (() -> Unit)? = null,
    onPlay: (index: Int) -> Unit,
) {
    val rowState = rememberLazyListState()
    // 只在滚动(手势+fling 惯性)完全停止时判定一次是否在尾部:滚动过程中追加会连环
    // 触发多次请求(fling 追不上 append);且手势→fling 交接处 isScrollInProgress 有
    // 瞬间 false,静止需延时复核——一次手势最多拉一批。初始无滚动事件,天然不触发。
    if (onLoadMore != null) {
        LaunchedEffect(rowState) {
            snapshotFlow { rowState.isScrollInProgress }
                .distinctUntilChanged()
            .collectLatest { scrolling ->
                if (!scrolling) {
                    delay(250)
                    if (rowState.isScrollInProgress) return@collectLatest
                    val info = rowState.layoutInfo
                    val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                    val hasScrolled =
                        rowState.firstVisibleItemIndex > 0 || rowState.firstVisibleItemScrollOffset > 0
                    if (hasScrolled && info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 1) {
                        onLoadMore?.invoke()
                    }
                }
            }
        }
    }
    LazyRow(
        state = rowState,
        modifier = Modifier.padding(horizontal = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(contents, key = { _, content -> content.videoId ?: content.title }) { index, content ->
            NeteaseSongCard(
                content = content,
                onClick = { onPlay(index) },
                onTrash = onTrash?.let { trash -> { trash(content) } },
                // 190dp ≈ 一屏两列(主页歌单行同款的双列视觉),横滑翻页
                cardWidth = 190.dp,
            )
        }
        if (loadingMore) {
            item(key = "fm-loading-more") {
                Box(
                    modifier =
                        Modifier
                            .width(190.dp)
                            .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp,
                    )
                }
            }
        }
    }
}

/** 红心电台入口卡:红心随机 30 首起播,播完接私人FM(心动模式端点已死,本地替代方案)。
 *  红心队列处于活动态时整行点击=暂停/恢复,尾图标随播放态切 Pause/PlayArrow。 */
@Composable
private fun HeartRadioCard(
    loading: Boolean,
    isHeartActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(onClick = if (isHeartActive) onToggle else onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = SimpIcons.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "红心电台",
                style = typo().titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "从你的红心歌单随机出发，播完接私人FM",
                style = typo().bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = if (isHeartActive && isPlaying) SimpIcons.Pause else SimpIcons.PlayArrow,
                contentDescription = null,
                tint = if (isHeartActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** FM hero 大卡:首曲封面铺底 + 暗部渐变 + 标题/副标题/收听按钮。
 *  FM 队列处于活动态时按钮/整卡点击=暂停/恢复(开始收听→暂停/继续收听),否则起播 FM 批次。 */
@Composable
private fun FmHeroCard(
    content: Content,
    isFmActive: Boolean,
    isPlaying: Boolean,
    onStart: () -> Unit,
    onToggle: () -> Unit,
) {
    val cardAction = if (isFmActive) onToggle else onStart
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp)
                .height(300.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = cardAction),
    ) {
        AsyncImage(
            model = content.thumbnails.lastOrNull()?.url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                    ),
                ),
        )
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp),
        ) {
            Text(
                text = stringResource(Res.string.personal_fm),
                style = typo().headlineLarge,
                color = Color.White,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(Res.string.personal_fm_subtitle),
                style = typo().bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = cardAction) {
                Icon(
                    imageVector = if (isFmActive && isPlaying) SimpIcons.Pause else SimpIcons.PlayArrow,
                    contentDescription = null,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        isFmActive && isPlaying -> stringResource(Res.string.personal_fm_pause)
                        isFmActive -> stringResource(Res.string.personal_fm_resume)
                        else -> stringResource(Res.string.personal_fm_start)
                    },
                )
            }
        }
    }
}

/** 分区标题(每日推荐/最近在听等) */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = typo().headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 15.dp),
    )
}

/** 新歌速递地区 chips(网易原生 areaId);切换期间禁用防连点 */
@Composable
private fun ExpressAreaChips(
    selected: Int,
    enabled: Boolean,
    onSelect: (area: Int) -> Unit,
) {
    val areas = listOf(0 to "全部", 7 to "华语", 96 to "欧美", 8 to "日语", 16 to "韩语")
    Row(
        modifier =
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        areas.forEach { (area, label) ->
            Chip(
                isAnimated = !enabled,
                isSelected = area == selected,
                text = label,
            ) {
                if (enabled) onSelect(area)
            }
        }
    }
}
