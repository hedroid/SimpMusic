package com.maxrave.simpmusic.ui.screen.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.maxrave.domain.data.model.searchResult.albums.AlbumsResult
import com.maxrave.domain.data.model.searchResult.artists.ArtistsResult
import com.maxrave.domain.data.model.searchResult.playlists.PlaylistsResult
import com.maxrave.domain.utils.LocalResource
import com.maxrave.simpmusic.extension.isScrollingUp
import com.maxrave.simpmusic.ui.component.CenterLoadingBox
import com.maxrave.simpmusic.ui.component.EndOfPage
import com.maxrave.simpmusic.ui.component.HomeItemContentPlaylist
import com.maxrave.simpmusic.ui.navigation.destination.list.PlaylistDestination
import com.maxrave.simpmusic.ui.screen.home.NeteaseAlbumRow
import com.maxrave.simpmusic.ui.screen.home.NeteaseArtistRow
import com.maxrave.simpmusic.ui.theme.LibraryGridDefaults
import com.maxrave.simpmusic.ui.theme.typo
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.followed
import simpmusic.composeapp.generated.resources.no_YouTube_playlists
import simpmusic.composeapp.generated.resources.playlists
import simpmusic.composeapp.generated.resources.starred_albums

/**
 * "您的 YouTube Music" tab(2026-09-20 定稿):三分区单页,结构镜像"您的网易云"——
 * YouTube 歌单(云端,点赞收藏+自建) / 收藏的专辑(云端 FEmusic_liked_albums) /
 * 关注的歌手(本地关注表镜像,YT 关注双写;沉底)。
 * **不放本地歌单**——本地歌单/收藏是刻意下线的功能,入口不在这里恢复。
 *
 * 布局走 [LibraryGridDefaults] 统一口径(主页 15dp 边距);专辑/歌手行复用网易 tab 同款
 * 行组件(horizontalPadding=0,网格整体已缩进);三分区独立降级,失败分区隐藏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryYouTubeTab(
    navController: NavController,
    contentPadding: PaddingValues,
    playlists: LocalResource<List<PlaylistsResult>>,
    albums: LocalResource<List<AlbumsResult>>,
    artists: LocalResource<List<ArtistsResult>>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
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

    val playlistList = (playlists as? LocalResource.Success)?.data.orEmpty()
    val albumList = (albums as? LocalResource.Success)?.data.orEmpty()
    val artistList = (artists as? LocalResource.Success)?.data.orEmpty()
    val anyLoading = playlists is LocalResource.Loading || albums is LocalResource.Loading || artists is LocalResource.Loading
    val hasContent = playlistList.isNotEmpty() || albumList.isNotEmpty() || artistList.isNotEmpty()

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

            // 三分区全空(或全被独立降级隐藏):空态文案
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
                    if (playlistList.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "yt_cloud_header") {
                            SectionHeader(stringResource(Res.string.playlists))
                        }
                        items(playlistList, key = { "yt_${it.browseId}" }) { playlist ->
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

                    if (albumList.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "yt_albums_row") {
                            NeteaseAlbumRow(
                                title = stringResource(Res.string.starred_albums),
                                albums = albumList,
                                navController = navController,
                                horizontalPadding = 0.dp,
                            )
                        }
                    }

                    // 关注的歌手沉底:歌单 → 收藏的专辑 → 关注的歌手(与网易云 tab 同序)
                    if (artistList.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "yt_artists_row") {
                            NeteaseArtistRow(
                                title = stringResource(Res.string.followed),
                                artists = artistList,
                                showRank = false,
                                navController = navController,
                                horizontalPadding = 0.dp,
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
