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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import coil3.compose.AsyncImage
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
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
import com.maxrave.simpmusic.ui.component.ItemArtistChart
import com.maxrave.simpmusic.ui.component.rememberHolderPainter
import com.maxrave.simpmusic.extension.isScrollingUp
import com.maxrave.simpmusic.ui.component.Chip
import com.maxrave.simpmusic.ui.component.EndOfPage
import com.maxrave.simpmusic.ui.component.HomeItemContentPlaylist
import com.maxrave.simpmusic.ui.component.HomeItemSong
import com.maxrave.simpmusic.ui.component.MoodMomentAndGenreHomeItem
import com.maxrave.simpmusic.ui.component.HomeShimmer
import com.maxrave.simpmusic.ui.icon.Delete
import com.maxrave.simpmusic.ui.icon.SimpIcons
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
            isRefreshing = false, // 行级懒加载:刷新是同步重置到占位,各行自行转圈
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = false,
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
                // 行级懒加载:固定行序,每行独立槽位(Loading 占位→滚到才拉→Ready/Failed)
                NeteaseHomeViewModel.Row.entries.forEach { row ->
                    item(key = row.name) {
                        NeteaseHomeRowSlot(
                            row = row,
                            ui = state.rows[row] ?: NeteaseHomeViewModel.RowUi.Loading,
                            viewModel = viewModel,
                            navController = navController,
                            newAlbumsArea = state.newAlbumsArea,
                        )
                    }
                }
                item(key = "end") { EndOfPage() }
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
                        isAnimated = false,
                        isSelected = false,
                        text = stringResource(Res.string.all),
                    ) { }
                    viewModel.chips.forEach { tag ->
                        Chip(
                            isAnimated = false,
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
        val songContents = contents.filterNotNull().filter { it.videoId != null }
        if (songContents.isNotEmpty()) {
            // 歌曲行:3 行紧凑卡网格(120dp 方图+两行小字,避免大卡被行高裁切)
            val gridState = rememberLazyGridState()
            LazyHorizontalGrid(
                rows = GridCells.Fixed(3),
                modifier =
                    Modifier
                        .padding(top = 8.dp)
                        .height(460.dp),
                state = gridState,
            ) {
                items(songContents, key = { it.videoId ?: it.title }) { content ->
                    NeteaseSongCard(
                        content = content,
                        onClick = { onSongClick(content) },
                    )
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                items(contents.filterNotNull(), key = { it.playlistId ?: it.title }) { content ->
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



/** 网格/单行通用紧凑歌曲卡:方图 + 标题/艺人两行小字。cardWidth 默认 96dp(3 行网格用),
 *  FM 单行(主页歌单行样式)传 120dp。NeteaseMixScreen(私人FM)同包复用,勿改回 private。
 *  onTrash 非空时图上叠垃圾桶角标(仅 FM 卡用;主页/每日推荐不传,视觉零变化)。 */
@Composable
internal fun NeteaseSongCard(
    content: com.maxrave.domain.data.model.home.Content,
    onClick: () -> Unit,
    onTrash: (() -> Unit)? = null,
    cardWidth: androidx.compose.ui.unit.Dp = 96.dp,
) {
    Column(
        modifier =
            Modifier
                .width(cardWidth)
                .padding(4.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            AsyncImage(
                model = content.thumbnails.lastOrNull()?.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp)),
            )
            if (onTrash != null) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable(onClick = onTrash),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = SimpIcons.Delete,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
        Text(
            text = content.title,
            style = typo().titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = content.artists?.joinToString(", ") { it.name }.orEmpty(),
            style = typo().bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 热门歌手行:100dp 圆形头像卡,点击进艺人页(M6 数字 ID 同页路由) */
@Composable
private fun NeteaseArtistRow(
    title: String,
    artists: List<com.maxrave.domain.data.model.searchResult.artists.ArtistsResult>,
    showRank: Boolean,
    navController: NavController,
) {
    // YTM 主页"热门艺人"(排行榜 shelf 艺人榜)同款:3 行 240dp 横滑网格 + ItemArtistChart
    // (排名+60dp 圆头像+名字+副标题)。网易无订阅数,副标题留空隐藏。
    var rowWidthDp by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val lazyGridState = rememberLazyGridState()
    val snapperFlingBehavior =
        rememberSnapFlingBehavior(SnapLayoutInfoProvider(lazyGridState = lazyGridState))
    Column(Modifier.padding(horizontal = 15.dp)) {
        Text(
            text = title,
            style = typo().headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Column(
            Modifier
                .onGloballyPositioned { coordinates ->
                    with(density) {
                        rowWidthDp = (coordinates.size.width).toDp()
                    }
                },
        ) {
            LazyHorizontalGrid(
                rows = GridCells.Fixed(3),
                modifier = Modifier.height(240.dp),
                state = lazyGridState,
                flingBehavior = snapperFlingBehavior,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(artists.size, key = { artists[it].browseId }) { index ->
                    val artist = artists[index]
                    ItemArtistChart(
                        onClick = {
                            navController.navigate(
                                com.maxrave.simpmusic.ui.navigation.destination.list.ArtistDestination(artist.browseId),
                            )
                        },
                        data =
                            com.maxrave.domain.data.model.home.chart.ItemArtist(
                                browseId = artist.browseId,
                                rank = if (showRank) "${index + 1}" else "",
                                subscribers = "",
                                thumbnails = artist.thumbnails,
                                title = artist.artist,
                                trend = "",
                            ),
                        widthDp = rowWidthDp,
                    )
                }
            }
        }
    }
}

/** 新碟上架行:YT 主页同款 HomeItemContentPlaylist(160dp 方卡,一屏两列),
 *  点击进专辑页(M6 数字 ID 同页路由)。area 非空时显示地区 chips(分区缓存即时切换)。 */
@Composable
private fun NeteaseAlbumRow(
    title: String,
    albums: List<com.maxrave.domain.data.model.searchResult.albums.AlbumsResult>,
    navController: NavController,
    area: String? = null,
    onAreaSelect: ((String) -> Unit)? = null,
) {
    Column(Modifier.padding(horizontal = 15.dp)) {
        Text(
            text = title,
            style = typo().headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (area != null && onAreaSelect != null) {
            NewAlbumAreaChips(selected = area) { onAreaSelect(it) }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            items(albums, key = { it.browseId }) { album ->
                HomeItemContentPlaylist(
                    onClick = {
                        navController.navigate(
                            com.maxrave.simpmusic.ui.navigation.destination.list.AlbumDestination(album.browseId),
                        )
                    },
                    data = album,
                )
            }
        }
    }
}

/** 行槽位:按行状态渲染占位或内容。Loading 占位被组合即触发拉取(滚到才加载)。
 *  占位高度对齐真实行高,内容落地不跳滚动位置。 */
@Composable
private fun NeteaseHomeRowSlot(
    row: NeteaseHomeViewModel.Row,
    ui: NeteaseHomeViewModel.RowUi,
    viewModel: NeteaseHomeViewModel,
    navController: NavController,
    newAlbumsArea: String,
) {
    when (ui) {
        is NeteaseHomeViewModel.RowUi.Loading -> {
            // 进入组合即触发本行拉取(下拉刷新重置后重新进入 Loading 也会再触发)
            LaunchedEffect(Unit) { viewModel.ensureRowLoaded(row) }
            RowPlaceholder(
                title = row.title,
                height = row.placeholderHeight(),
                retry = false,
            )
        }

        is NeteaseHomeViewModel.RowUi.Failed -> {
            RowPlaceholder(
                title = row.title,
                height = 120.dp,
                retry = true,
                onRetry = { viewModel.retryRow(row) },
            )
        }

        is NeteaseHomeViewModel.RowUi.Ready -> {
            when (val content = ui.content) {
                is NeteaseHomeViewModel.RowContent.Feed ->
                    NeteaseHomeRow(
                        title = content.item.title,
                        contents = content.item.contents,
                        navController = navController,
                        onSongClick = viewModel::playSong,
                    )

                is NeteaseHomeViewModel.RowContent.ChartRow ->
                    Column(Modifier.padding(horizontal = 15.dp)) {
                        Text(
                            text = content.chart.listChartItem.firstOrNull()?.title ?: "排行榜",
                            style = typo().headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            items(
                                content.chart.listChartItem.firstOrNull()?.playlists ?: emptyList(),
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

                is NeteaseHomeViewModel.RowContent.ArtistsRow ->
                    NeteaseArtistRow(
                        title = row.title,
                        artists = content.list,
                        showRank = row == NeteaseHomeViewModel.Row.TOP_ARTISTS,
                        navController = navController,
                    )

                is NeteaseHomeViewModel.RowContent.AlbumsRow ->
                    if (row == NeteaseHomeViewModel.Row.NEW_ALBUMS) {
                        NeteaseAlbumRow(
                            title = row.title,
                            albums = content.list,
                            navController = navController,
                            area = newAlbumsArea,
                            onAreaSelect = viewModel::loadNewAlbumsArea,
                        )
                    } else {
                        NeteaseAlbumRow(
                            title = row.title,
                            albums = content.list,
                            navController = navController,
                        )
                    }

                is NeteaseHomeViewModel.RowContent.SectionsRow ->
                    NeteaseCategorySections(
                        sections = content.mood.sections,
                        navController = navController,
                    )
            }
        }
    }
}

/** 行占位:标题 + 居中转圈(失败态换成重试按钮) */
@Composable
private fun RowPlaceholder(
    title: String,
    height: androidx.compose.ui.unit.Dp,
    retry: Boolean,
    onRetry: () -> Unit = {},
) {
    Column(Modifier.padding(horizontal = 15.dp)) {
        Text(
            text = title,
            style = typo().headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(height)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            if (retry) {
                Button(onClick = onRetry) {
                    Text(stringResource(Res.string.retry))
                }
            } else {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.5.dp,
                )
            }
        }
    }
}

/** 占位高度对齐真实行高:歌单/榜单/歌手/专辑行 ~250dp,歌曲三行网格 460dp,分类区块更高 */
private fun NeteaseHomeViewModel.Row.placeholderHeight(): androidx.compose.ui.unit.Dp =
    when (this) {
        NeteaseHomeViewModel.Row.RADAR_SONGS,
        NeteaseHomeViewModel.Row.NEW_SONGS,
        -> 460.dp
        NeteaseHomeViewModel.Row.SECTIONS -> 640.dp
        else -> 250.dp
    }

/** 新碟上架地区 chips(ALL/华语/欧美/韩语/日语),命中分区缓存即时切换 */
@Composable
private fun NewAlbumAreaChips(
    selected: String,
    onSelect: (String) -> Unit,
) {
    val areas = listOf("ALL" to "全部", "ZH" to "华语", "EA" to "欧美", "KR" to "韩语", "JP" to "日语")
    Row(
        modifier =
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        areas.forEach { (code, label) ->
            Chip(
                isAnimated = false,
                isSelected = code == selected,
                text = label,
            ) { onSelect(code) }
        }
    }
}
