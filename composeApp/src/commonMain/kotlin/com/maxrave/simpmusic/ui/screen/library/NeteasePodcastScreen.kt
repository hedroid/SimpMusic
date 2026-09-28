package com.maxrave.simpmusic.ui.screen.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.maxrave.simpmusic.ui.component.MediaRow
import com.maxrave.simpmusic.ui.navigation.destination.list.NeteaseRadioDetailDestination
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.NeteasePodcastViewModel
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.all
import simpmusic.composeapp.generated.resources.podcast_based_on_listening
import simpmusic.composeapp.generated.resources.podcast_featured_radios
import simpmusic.composeapp.generated.resources.podcast_guess_you_like
import simpmusic.composeapp.generated.resources.podcast_latest_programs
import simpmusic.composeapp.generated.resources.podcast_my_subscriptions
import simpmusic.composeapp.generated.resources.podcast_tap_to_listen
import simpmusic.composeapp.generated.resources.podcast_toplist_radios

/**
 * 网易云播客 chip 页(LibraryScreen Crossfade 分支挂载,VM 是 Koin single)。
 * 区块编排对齐 Melodia:分类 chips → 我的订阅 → 最新节目(点击即播) → 猜你喜欢(未登录隐藏)
 * → 精选电台 → 热门电台榜(带排名)。横行走统一 MediaRow 口径(15dp 页边/4dp 间距)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeteasePodcastScreen(
    innerPadding: PaddingValues,
    navController: NavController,
    viewModel: NeteasePodcastViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pullToRefreshState = rememberPullToRefreshState()

    val hasAnyContent =
        uiState.categories.isNotEmpty() || uiState.myRadios.isNotEmpty() ||
            uiState.programs.isNotEmpty() || uiState.personalizedRadios.isNotEmpty() ||
            uiState.recommendRadios.isNotEmpty() || uiState.toplistRadios.isNotEmpty()

    PullToRefreshBox(
        modifier = Modifier.fillMaxSize(),
        state = pullToRefreshState,
        onRefresh = { viewModel.refresh() },
        // 指示器=手势受理确认(≤半圈口径),后台工作静默(网易主页同款)
        isRefreshing = false,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = false,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = innerPadding.calculateTopPadding()),
                containerColor = PullToRefreshDefaults.indicatorContainerColor,
                color = PullToRefreshDefaults.indicatorColor,
                maxDistance = PullToRefreshDefaults.PositionalThreshold,
            )
        },
    ) {
        if (!hasAnyContent && uiState.programsLoading) {
            CenterLoadingBox(Modifier.fillMaxSize())
            return@PullToRefreshBox
        }
        val listState = rememberLazyListState()
        // 最新节目近底 8 行触发追加(SimilarSongs/详情页同款)
        val shouldLoadMore by androidx.compose.runtime.remember {
            androidx.compose.runtime.derivedStateOf {
                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                last >= listState.layoutInfo.totalItemsCount - 8
            }
        }
        androidx.compose.runtime.LaunchedEffect(shouldLoadMore, uiState.programsHasMore) {
            if (shouldLoadMore && uiState.programsHasMore && !uiState.programsLoadingMore && !uiState.programsLoading) {
                viewModel.loadMorePrograms()
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 8.dp,
                ),
        ) {
            // 分类 chips(全部=不过滤)
            if (uiState.categories.isNotEmpty()) {
                item(key = "podcast_categories") {
                    PodcastCategoryChipsRow(
                        categories = uiState.categories,
                        selectedId = uiState.selectedCategoryId,
                        onSelect = { viewModel.selectCategory(it) },
                    )
                }
            }

            // 我的订阅(未登录/空即隐藏)
            if (uiState.myRadios.isNotEmpty()) {
                item(key = "podcast_my_subscriptions") {
                    MediaRow(title = stringResource(Res.string.podcast_my_subscriptions)) {
                        items(uiState.myRadios, key = { "my_radio_${it.id}" }) { radio ->
                            NeteaseDjRadioCard(radio = radio) {
                                navController.navigate(
                                    NeteaseRadioDetailDestination(radioId = radio.id, radioName = radio.name),
                                )
                            }
                        }
                    }
                }
            }

            // 最新节目(点击即播;Melodia 同款标题+副标题)
            item(key = "podcast_programs_header") {
                Column(Modifier.padding(horizontal = 15.dp)) {
                    Text(
                        text = stringResource(Res.string.podcast_latest_programs),
                        style = typo().headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Text(
                        text = stringResource(Res.string.podcast_tap_to_listen),
                        style = typo().bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (uiState.programsLoading) {
                item(key = "podcast_programs_loading") {
                    Box(
                        Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    }
                }
            } else {
                items(uiState.programs, key = { "program_${it.id}" }) { program ->
                    NeteaseProgramRow(program = program) {
                        viewModel.playProgram(uiState.programs.indexOf(program))
                    }
                }
                if (uiState.programsLoadingMore) {
                    item(key = "podcast_programs_loading_more") {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // 猜你喜欢(需登录;未登录服务端回空,区块隐藏——Melodia 同款)
            if (uiState.personalizedRadios.isNotEmpty()) {
                item(key = "podcast_personalized") {
                    MediaRow(
                        title = stringResource(Res.string.podcast_guess_you_like),
                        subtitle = stringResource(Res.string.podcast_based_on_listening),
                    ) {
                        items(uiState.personalizedRadios, key = { "personalized_${it.id}" }) { radio ->
                            NeteaseDjRadioCard(radio = radio) {
                                navController.navigate(
                                    NeteaseRadioDetailDestination(radioId = radio.id, radioName = radio.name),
                                )
                            }
                        }
                    }
                }
            }

            // 精选电台
            if (uiState.recommendRadios.isNotEmpty()) {
                item(key = "podcast_recommend") {
                    MediaRow(title = stringResource(Res.string.podcast_featured_radios)) {
                        items(uiState.recommendRadios, key = { "recommend_${it.id}" }) { radio ->
                            NeteaseDjRadioCard(radio = radio) {
                                navController.navigate(
                                    NeteaseRadioDetailDestination(radioId = radio.id, radioName = radio.name),
                                )
                            }
                        }
                    }
                }
            }

            // 热门电台榜(带排名)
            if (uiState.toplistRadios.isNotEmpty()) {
                item(key = "podcast_toplist") {
                    MediaRow(title = stringResource(Res.string.podcast_toplist_radios)) {
                        itemsIndexed(uiState.toplistRadios, key = { _, r -> "toplist_${r.id}" }) { index, radio ->
                            NeteaseDjRadioCard(radio = radio, rank = index + 1) {
                                navController.navigate(
                                    NeteaseRadioDetailDestination(radioId = radio.id, radioName = radio.name),
                                )
                            }
                        }
                    }
                }
            }

            item(key = "podcast_end") {
                EndOfPage()
            }
        }
    }
}

/** 分类 chips 行(含"全部"=不过滤;选中即重拉最新节目) */
@Composable
private fun PodcastCategoryChipsRow(
    categories: List<com.maxrave.netease.model.NeteasePodcastCategory>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 15.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(
            selected = selectedId == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(Res.string.all), style = typo().labelMedium) },
        )
        categories.forEach { category ->
            FilterChip(
                selected = selectedId == category.id,
                onClick = { onSelect(category.id) },
                label = { Text(category.name, style = typo().labelMedium) },
            )
        }
    }
}

/** 电台横滑卡(160dp 定宽,与主页货架 tile 同尺寸;榜单带排名角标) */
@Composable
internal fun NeteaseDjRadioCard(
    radio: com.maxrave.netease.model.NeteaseDjRadio,
    rank: Int? = null,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(160.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
    ) {
        Box {
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
                        .size(160.dp)
                        .clip(RoundedCornerShape(10.dp)),
            )
            if (rank != null) {
                Text(
                    text = rank.toString(),
                    style = typo().titleMedium,
                    color = Color.White,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            text = radio.name,
            style = typo().titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = radio.djNickname.orEmpty(),
            style = typo().bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 节目整行(封面 56dp + 标题 + 电台·主播 + 时长);点击=从该节目整队起播 */
@Composable
internal fun NeteaseProgramRow(
    program: com.maxrave.netease.model.NeteaseDjProgram,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 15.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model =
                ImageRequest
                    .Builder(LocalPlatformContext.current)
                    .data(program.coverUrl)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(550)
                    .build(),
            contentDescription = program.name,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
        ) {
            Text(
                text = program.name,
                style = typo().titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(program.radioName, program.djNickname).joinToString(" · "),
                style = typo().bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = formatProgramDuration(program.durationMs),
            style = typo().bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatProgramDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
