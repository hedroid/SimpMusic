package com.maxrave.simpmusic.ui.screen.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.LocalPlatformContext
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.maxrave.simpmusic.ui.component.CenterLoadingBox
import com.maxrave.simpmusic.ui.component.EndOfPage
import com.maxrave.simpmusic.ui.component.NormalAppBar
import com.maxrave.simpmusic.ui.icon.ArrowBackIosNew
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.ui.utils.formatCompactCount
import com.maxrave.simpmusic.viewModel.NeteasePodcastCategoryViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.error
import simpmusic.composeapp.generated.resources.podcast_category_empty
import simpmusic.composeapp.generated.resources.podcast_hottest_radios
import simpmusic.composeapp.generated.resources.podcast_programs
import simpmusic.composeapp.generated.resources.podcast_rising_fastest
import simpmusic.composeapp.generated.resources.retry
import simpmusic.composeapp.generated.resources.subscribers

/**
 * 网易云播客分类页(官方同构,2026-09-29):双 tab 榜单——上升最快(type=0)/最热电台(type=1),
 * 两列网格(单元格=排名+方形小封面+名称+订阅数,官方一行两条);近底分页,tab 按需拉取。
 * 官方顶部"优秀新电台"横滑暂缺端点(三个候选全 404 探针实证),不做假区。
 * 顶栏随滚动收起(2026-09-30):上滑时标题行(返回+分类名)收起,只留排序 chips 贴顶,回顶展开。
 */
@Composable
fun NeteasePodcastCategoryScreen(
    navController: NavController,
    categoryId: Long,
    categoryName: String,
    innerPadding: PaddingValues,
    viewModel: NeteasePodcastCategoryViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(categoryId) {
        viewModel.load(categoryId)
    }

    val gridState = rememberLazyGridState()
    // 上滑收起(用户 2026-09-30):网格滚离顶部时标题行(返回+分类名)收起,只留
    // 排序 chips 贴顶;回到顶部重新展开。位置驱动——纯 derivedStateOf 读网格状态,
    // 收起是纯覆盖层(padding 锁定,内容零位移),无"几何变化→滚动上报"反馈回路。
    val atTop by remember {
        derivedStateOf { gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0 }
    }
    // 锁定展开态顶栏高度给网格 contentPadding(库页同款):收起时覆盖层变矮,padding
    // 不跟随逐帧变化,防 LazyGrid 每帧重锚定引发振荡。
    var topBarHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize()) {
        if (uiState.loading) {
            CenterLoadingBox(Modifier.fillMaxSize())
        } else {
            val chart = uiState.current
            val shouldLoadMore by remember {
                derivedStateOf {
                    val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    last >= gridState.layoutInfo.totalItemsCount - 6
                }
            }
            LaunchedEffect(shouldLoadMore, chart.hasMore) {
                if (shouldLoadMore && chart.hasMore && !chart.loadingMore) {
                    viewModel.loadMore()
                }
            }

            LazyVerticalGrid(
                // 单列信息行:右侧要放 播主·节目数·订阅数+描述,半宽格放不下(用户定案 2026-09-29)
                columns = GridCells.Fixed(1),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = 15.dp,
                        end = 15.dp,
                        top = topBarHeight + 4.dp,
                        bottom = innerPadding.calculateBottomPadding() + 8.dp,
                    ),
            ) {
                if (chart.radios.isEmpty()) {
                    item(key = "category_state", span = { GridItemSpan(2) }) {
                        Column(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        // 僵尸分类(娱乐/其他服务端 0 条)≠加载失败
                                        if (chart.failed) Res.string.error else Res.string.podcast_category_empty,
                                    ),
                                style = typo().bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (chart.failed) {
                                Button(
                                    onClick = { viewModel.retry() },
                                    modifier = Modifier.padding(top = 12.dp),
                                ) {
                                    Text(stringResource(Res.string.retry), style = typo().labelMedium)
                                }
                            }
                        }
                    }
                }
                itemsIndexed(chart.radios, key = { _, r -> "category_radio_${r.id}" }) { index, radio ->
                    CategoryRadioCell(
                        rank = index + 1,
                        radio = radio,
                    ) {
                        navController.navigate(
                            com.maxrave.simpmusic.ui.navigation.destination.list.NeteaseRadioDetailDestination(
                                radioId = radio.id,
                                radioName = radio.name,
                            ),
                        )
                    }
                }
                if (chart.loadingMore) {
                    item(key = "category_loading_more", span = { GridItemSpan(2) }) {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        }
                    }
                }
                item(key = "category_end", span = { GridItemSpan(2) }) {
                    // 网格 contentPadding 已含 scaffold 底栏让位,页尾不双叠
                    EndOfPage(includeBottomBarPadding = false)
                }
            }
        }

        // 顶栏覆盖层:标题行可收起,chips 行恒贴顶(库页标题行同款双 AnimatedVisibility)
        Column(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .background(
                        // 滚动后电台行从覆盖层下面穿过,必须有实底(电台详情页 stickyHeader 同款)
                        if (atTop) Color.Transparent else MaterialTheme.colorScheme.surface,
                    ).onGloballyPositioned { coordinates ->
                        val measured = with(density) { coordinates.size.height.toDp() }
                        topBarHeight = maxOf(topBarHeight, measured)
                    },
        ) {
            AnimatedVisibility(
                visible = atTop,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                NormalAppBar(
                    title = {
                        Text(
                            text = categoryName,
                            style = typo().titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leftIcon = {
                        androidx.compose.material3.IconButton(onClick = { navController.navigateUp() }) {
                            androidx.compose.material3.Icon(SimpIcons.ArrowBackIosNew, contentDescription = "Back")
                        }
                    },
                )
            }
            // 收起态由同尺寸的状态栏 Spacer 占位,chips 不钻到状态栏下面
            AnimatedVisibility(
                visible = !atTop,
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
            if (!uiState.loading) {
                // 双 tab(上升最快/最热电台,官方同款)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 15.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    NeteasePodcastCategoryViewModel.ChartTab.entries.forEach { tab ->
                        FilterChip(
                            selected = uiState.tab == tab,
                            onClick = { viewModel.switchTab(tab) },
                            label = {
                                Text(
                                    stringResource(if (tab == NeteasePodcastCategoryViewModel.ChartTab.RISING) Res.string.podcast_rising_fastest else Res.string.podcast_hottest_radios),
                                    style = typo().labelMedium,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 榜单行(单列):排名 + 小封面 + 电台名 + 数据行(播主·N节目·N订阅) + 描述/rcmdtext 两行。
 *  列表端点字段残缺时逐行跳过(播主/描述缺失不占位),半宽格时代只剩一个订阅数太空。 */
@Composable
private fun CategoryRadioCell(
    rank: Int,
    radio: com.maxrave.netease.model.NeteaseDjRadio,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = rank.toString(),
            style = typo().titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 10.dp),
        )
        AsyncImage(
            model =
                ImageRequest
                    .Builder(LocalPlatformContext.current)
                    .data(radio.coverUrl)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(550)
                    .build(),
            contentDescription = radio.name,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Text(
                text = radio.name,
                style = typo().titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta =
                listOfNotNull(
                    radio.djNickname?.takeIf { it.isNotBlank() },
                    radio.programCount.takeIf { it > 0 }?.let { count ->
                        "${formatCompactCount(count.toLong())} ${stringResource(Res.string.podcast_programs)}"
                    },
                    radio.subCount?.let { stringResource(Res.string.subscribers, formatCompactCount(it)) },
                ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = typo().bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val desc =
                radio.description?.takeIf { it.isNotBlank() }
                    ?: radio.rcmdtext?.takeIf { it.isNotBlank() }
            if (desc != null) {
                Text(
                    text = desc,
                    style = typo().bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
