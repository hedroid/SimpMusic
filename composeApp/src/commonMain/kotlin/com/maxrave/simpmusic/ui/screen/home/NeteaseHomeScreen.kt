package com.maxrave.simpmusic.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.maxrave.simpmusic.extension.isScrollingUp
import com.maxrave.simpmusic.ui.component.Chip
import com.maxrave.simpmusic.ui.component.EndOfPage
import com.maxrave.simpmusic.ui.component.HomeItemContentPlaylist
import com.maxrave.simpmusic.ui.component.HomeItemSong
import com.maxrave.simpmusic.ui.component.MoodMomentAndGenreHomeItem
import com.maxrave.simpmusic.ui.component.HomeShimmer
import com.maxrave.simpmusic.ui.navigation.destination.home.NeteaseTagDestination
import com.maxrave.simpmusic.ui.navigation.destination.list.PlaylistDestination
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.NeteaseHomeViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.all
import simpmusic.composeapp.generated.resources.let_s_pick_a_playlist_for_you
import simpmusic.composeapp.generated.resources.netease_home_error
import simpmusic.composeapp.generated.resources.retry

/**
 * 网易主页(独立页):挂载点按 selected_source 与上游 HomeScreen 二选一。
 * 上游文件零改动;数据来自 NeteaseHomeViewModel(独立,不实例化上游 HomeViewModel)。
 * 布局对照上游骨架(悬浮顶栏+chips+PullToRefresh)+ 网易自己的内容区
 * (账户卡/feed 行/榜单/五组分类),没有的元素根本不存在,无需条件隐藏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeteaseHomeScreen(
    onScrolling: (onTop: Boolean) -> Unit = {},
    viewModel: NeteaseHomeViewModel = koinViewModel(),
    navController: NavController,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accountInfo by viewModel.accountInfo.collectAsStateWithLifecycle()
    val scrollState = rememberLazyListState()
    val isScrollingUp by scrollState.isScrollingUp()
    val pullToRefreshState = rememberPullToRefreshState()
    val chipRowState = rememberScrollState()
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
            onRefresh = viewModel::refresh,
            isRefreshing = state is NeteaseHomeViewModel.State.Loading,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = state is NeteaseHomeViewModel.State.Loading,
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
            Crossfade(state) { current ->
                when (current) {
                    is NeteaseHomeViewModel.State.Loading ->
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

                    is NeteaseHomeViewModel.State.Error ->
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
                            Button(onClick = viewModel::refresh) {
                                Text(stringResource(Res.string.retry))
                            }
                        }

                    is NeteaseHomeViewModel.State.Ready -> {
                        val ready = current.data
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
                            // 账户卡(登录时)
                            accountInfo?.let { info ->
                                item(key = "account") {
                                    Box(Modifier.padding(horizontal = 15.dp)) {
                                        AccountLayout(
                                            accountName = info.first ?: "",
                                            url = info.second ?: "",
                                        )
                                    }
                                }
                            }
                            // feed 行:歌单行 + 歌曲行
                            items(ready.rows, key = { "row_${it.title}" }) { row ->
                                NeteaseHomeRow(
                                    title = row.title,
                                    contents = row.contents,
                                    navController = navController,
                                    onSongClick = viewModel::playSong,
                                )
                            }
                            // 榜单区块
                            ready.chart?.let { chart ->
                                item(key = "chart") {
                                    Column(Modifier.padding(horizontal = 15.dp)) {
                                        Text(
                                            text = chart.listChartItem.firstOrNull()?.title ?: "排行榜",
                                            style = typo().headlineMedium,
                                            color = MaterialTheme.colorScheme.onBackground,
                                        )
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(top = 8.dp),
                                        ) {
                                            items(
                                                chart.listChartItem.firstOrNull()?.playlists ?: emptyList(),
                                                key = { it.id },
                                            ) { pl ->
                                                HomeItemContentPlaylist(
                                                    onClick = {
                                                        navController.navigate(
                                                            PlaylistDestination(playlistId = pl.id),
                                                        )
                                                    },
                                                    data = pl,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            // 五组分类区块:YT 同款 —— 每组 3 行横向网格(210dp),卡片带色条
                            ready.sections?.let { mood ->
                                item(key = "sections") {
                                    NeteaseCategorySections(
                                        sections = mood.sections,
                                        navController = navController,
                                    )
                                }
                            }
                            item(key = "end") { EndOfPage() }
                        }
                    }
                }
            }
        }
        // 悬浮顶栏 + chips(对照上游骨架)
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
                AnimatedVisibility(
                    visible = isScrollingUp,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    HomeTopAppBar(navController = navController)
                }
                AnimatedVisibility(
                    visible = !isScrollingUp,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Spacer(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.statusBars),
                    )
                }
                Row(
                    modifier =
                        Modifier
                            .horizontalScroll(chipRowState)
                            .padding(vertical = 8.dp, horizontal = 15.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Chip(
                        isAnimated = state is NeteaseHomeViewModel.State.Loading,
                        isSelected = false,
                        text = stringResource(Res.string.all),
                    ) { }
                    viewModel.chips.forEach { tag ->
                        Chip(
                            isAnimated = state is NeteaseHomeViewModel.State.Loading,
                            isSelected = false,
                            text = tag,
                        ) {
                            navController.navigate(NeteaseTagDestination(tag = tag))
                        }
                    }
                }
            }
        }
    }
}

/** 一行内容:歌单卡跳歌单页,歌曲卡播放 */
@Composable
private fun NeteaseHomeRow(
    title: String,
    contents: List<com.maxrave.domain.data.model.home.Content?>,
    navController: NavController,
    onSongClick: (com.maxrave.domain.data.model.home.Content) -> Unit,
) {
    Column(Modifier.padding(horizontal = 15.dp)) {
        Text(
            text = title,
            style = typo().headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            items(contents.filterNotNull(), key = { it.videoId ?: it.playlistId ?: it.title }) { content ->
                if (content.videoId != null) {
                    HomeItemSong(
                        onClick = { onSongClick(content) },
                        onLongClick = { },
                        data = content,
                    )
                } else {
                    HomeItemContentPlaylist(
                        onClick = {
                            content.playlistId?.let { id ->
                                navController.navigate(PlaylistDestination(playlistId = id))
                            }
                        },
                        data = content,
                    )
                }
            }
        }
    }
}


/** 分类区块:布局逐项对照上游 MoodMomentAndGenre(副标题+组标题+3 行横向网格),仅导航目标不同 */
@Composable
private fun NeteaseCategorySections(
    sections: List<com.maxrave.domain.data.model.mood.MoodSection>,
    navController: NavController,
) {
    Column(
        Modifier
            .padding(horizontal = 15.dp)
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = stringResource(Res.string.let_s_pick_a_playlist_for_you),
            style = typo().bodyMedium,
        )
        sections.forEach { section ->
            val gridState = rememberLazyGridState()
            val flingBehavior = rememberSnapFlingBehavior(SnapLayoutInfoProvider(lazyGridState = gridState))
            Text(
                text = section.title,
                style = typo().headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
            )
            LazyHorizontalGrid(
                rows = GridCells.Fixed(3),
                modifier = Modifier.height(210.dp),
                state = gridState,
                flingBehavior = flingBehavior,
            ) {
                items(section.items, key = { it.params }) { tag ->
                    MoodMomentAndGenreHomeItem(
                        title = tag.title,
                        stripeColor = tag.stripeColor,
                    ) {
                        navController.navigate(NeteaseTagDestination(tag = tag.title))
                    }
                }
            }
        }
    }
}
