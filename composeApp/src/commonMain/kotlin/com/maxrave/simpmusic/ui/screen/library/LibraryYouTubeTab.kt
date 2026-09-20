package com.maxrave.simpmusic.ui.screen.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.maxrave.domain.data.entities.LocalPlaylistEntity
import com.maxrave.domain.data.model.searchResult.playlists.PlaylistsResult
import com.maxrave.domain.utils.LocalResource
import com.maxrave.simpmusic.extension.angledGradientBackground
import com.maxrave.simpmusic.extension.isScrollingUp
import com.maxrave.simpmusic.ui.component.CenterLoadingBox
import com.maxrave.simpmusic.ui.component.EndOfPage
import com.maxrave.simpmusic.ui.component.HomeItemContentPlaylist
import com.maxrave.simpmusic.ui.icon.Add
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.navigation.destination.list.LocalPlaylistDestination
import com.maxrave.simpmusic.ui.navigation.destination.list.PlaylistDestination
import com.maxrave.simpmusic.ui.theme.LibraryGridDefaults
import com.maxrave.simpmusic.ui.theme.seed
import com.maxrave.simpmusic.ui.theme.typo
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.create
import simpmusic.composeapp.generated.resources.no_YouTube_playlists
import simpmusic.composeapp.generated.resources.playlists
import simpmusic.composeapp.generated.resources.your_playlists

/**
 * "您的 YouTube Music" tab(2026-09-20 改版):仿"您的网易云"的多分区单页——
 * [YouTube 歌单](云端,FEmusic_liked_playlists=点赞收藏+自建) +
 * [本地歌单](含创建 tile;"您的库"chip 下线后本地歌单失去唯一入口,在此恢复)。
 *
 * 布局走 [LibraryGridDefaults] 统一口径(与主页 15dp 边距一致)。云端分区空则隐藏
 * (未登录 YT 时 chip 本就不显示,不会落到只有云分区空的形态);本地分区常驻——
 * 创建入口是本 tab 的存在理由之一。下拉刷新同时重拉云端与本地。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryYouTubeTab(
    navController: NavController,
    contentPadding: PaddingValues,
    cloudPlaylists: LocalResource<List<PlaylistsResult>>,
    localPlaylists: LocalResource<List<LocalPlaylistEntity>>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onCreateLocalPlaylist: () -> Unit,
    onScrolling: (onTop: Boolean) -> Unit = {},
) {
    val state = rememberLazyGridState()
    val isScrollingUp by state.isScrollingUp()
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }
            .collect {
                if (it <= 1) {
                    onScrolling.invoke(true)
                } else {
                    onScrolling.invoke(isScrollingUp)
                }
            }
    }
    val pullToRefreshState = rememberPullToRefreshState()

    val cloudList = (cloudPlaylists as? LocalResource.Success)?.data.orEmpty()
    val localList = (localPlaylists as? LocalResource.Success)?.data.orEmpty()
    val anyLoading = cloudPlaylists is LocalResource.Loading || localPlaylists is LocalResource.Loading
    val hasContent = cloudList.isNotEmpty() || localList.isNotEmpty()

    PullToRefreshBox(
        modifier = Modifier.fillMaxSize(),
        state = pullToRefreshState,
        onRefresh = onRefresh,
        isRefreshing = isRefreshing,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = contentPadding.calculateTopPadding()),
                containerColor = PullToRefreshDefaults.indicatorContainerColor,
                color = PullToRefreshDefaults.indicatorColor,
                maxDistance = PullToRefreshDefaults.PositionalThreshold,
            )
        },
    ) {
        when {
            // 首拉:整页 spinner(与其它库分区 tab 一致)
            !hasContent && anyLoading -> CenterLoadingBox(Modifier.fillMaxSize())

            !hasContent ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.no_YouTube_playlists),
                        style = typo().bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            else -> {
                val layoutDirection = LocalLayoutDirection.current
                val gridContentPadding =
                    PaddingValues(
                        start = contentPadding.calculateStartPadding(layoutDirection) + LibraryGridDefaults.horizontalPadding,
                        top = contentPadding.calculateTopPadding() + 4.dp,
                        end = contentPadding.calculateEndPadding(layoutDirection) + LibraryGridDefaults.horizontalPadding,
                        bottom = contentPadding.calculateBottomPadding() + 8.dp,
                    )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = LibraryGridDefaults.minTileSize),
                    horizontalArrangement = LibraryGridDefaults.horizontalArrangement,
                    verticalArrangement = LibraryGridDefaults.verticalArrangement,
                    contentPadding = gridContentPadding,
                    state = state,
                ) {
                    if (cloudList.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "yt_cloud_header") {
                            SectionHeader(stringResource(Res.string.playlists))
                        }
                        items(cloudList, key = { "yt_${it.browseId}" }) { playlist ->
                            HomeItemContentPlaylist(
                                onClick = {
                                    navController.navigate(
                                        PlaylistDestination(playlist.browseId, isYourYouTubePlaylist = true),
                                    )
                                },
                                data = playlist,
                                fillWidth = true,
                            )
                        }
                    }

                    // 本地歌单分区常驻:创建 tile 是入口,列表可空
                    item(span = { GridItemSpan(maxLineSpan) }, key = "yt_local_header") {
                        SectionHeader(stringResource(Res.string.your_playlists))
                    }
                    item(key = "yt_local_create") {
                        CreateLocalPlaylistTile(onClick = onCreateLocalPlaylist)
                    }
                    if (localList.isNotEmpty()) {
                        items(localList, key = { "local_${it.id}" }) { playlist ->
                            HomeItemContentPlaylist(
                                onClick = {
                                    navController.navigate(LocalPlaylistDestination(playlist.id))
                                },
                                data = playlist,
                                fillWidth = true,
                            )
                        }
                    }

                    item(span = { GridItemSpan(maxLineSpan) }, key = "yt_end") {
                        EndOfPage()
                    }
                }
            }
        }
    }
}

/** 分区标题:与 LibraryNeteaseTab 同款(网格已统一 15dp 边距,行内不再水平 pad) */
@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = typo().headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier =
            modifier.padding(
                top = 10.dp,
                bottom = 4.dp,
            ),
    )
}

/** 创建本地歌单 tile:GridLibraryPlaylist 原创建卡同款视觉(渐变底+加号),宽度铺满槽位 */
@Composable
private fun CreateLocalPlaylistTile(onClick: () -> Unit) {
    Box(modifier = Modifier.clickable(onClick = onClick)) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .angledGradientBackground(
                        colors =
                            listOf(
                                seed,
                                Color.White.copy(alpha = 0.8f),
                            ),
                        degrees = 45f,
                    ),
                Alignment.Center,
            ) {
                Icon(
                    modifier = Modifier.size(84.dp),
                    imageVector = SimpIcons.Add,
                    tint = Color.White,
                    contentDescription = null,
                )
            }
            Text(
                text = stringResource(Res.string.create),
                style = typo().titleSmall,
                // 画在页面背景上,跟随主题色(白字在浅色主题会消失,原组件同款注释)
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
            )
        }
    }
}
