package com.maxrave.simpmusic.ui.screen.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
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
import simpmusic.composeapp.generated.resources.collected_playlists
import simpmusic.composeapp.generated.resources.created_playlists
import simpmusic.composeapp.generated.resources.delete_playlist_title
import simpmusic.composeapp.generated.resources.delete_youtube_playlist_message
import simpmusic.composeapp.generated.resources.no_YouTube_playlists
import simpmusic.composeapp.generated.resources.starred_albums
import simpmusic.composeapp.generated.resources.unsubscribe_playlist_title
import simpmusic.composeapp.generated.resources.unsubscribe_youtube_playlist_message

/**
 * "您的 YouTube Music" tab(2026-09-20 定稿):分区镜像"您的网易云"——
 * 系统歌单置顶满行(红心歌单 Liked Music 等,browseId 固定) / 创建的歌单(云端自建) /
 * 收藏的歌单(他人创建,tab2) / 收藏的专辑(云端) / 关注的歌手(本地镜像;沉底)。
 * **不放本地歌单**——本地歌单/收藏是刻意下线的功能,入口不在这里恢复。
 *
 * 布局走 [LibraryGridDefaults] 统一口径(主页 15dp 边距);专辑/歌手行复用网易 tab 同款
 * 行组件(horizontalPadding=0,网格整体已缩进);分区独立降级,失败分区隐藏。
 * 分区数据来自 [com.maxrave.domain.repository.PlaylistRepository.getLibraryPlaylistSplit]
 * (YTM App"已创建/已喜欢"筛选同源 tab;单 tab 响应退化见其注释)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryYouTubeTab(
    navController: NavController,
    contentPadding: PaddingValues,
    playlists: LocalResource<List<PlaylistsResult>>,
    likedPlaylists: LocalResource<List<PlaylistsResult>> = LocalResource.Loading(),
    autoPlaylists: LocalResource<List<PlaylistsResult>> = LocalResource.Loading(),
    albums: LocalResource<List<AlbumsResult>>,
    artists: LocalResource<List<ArtistsResult>>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    // 歌单移除操作对齐网易逻辑(2026-09-21):自建=删除、收藏=取消收藏,长按确认弹窗;
    // 系统歌单(LM/SE)置顶行不参与。两端同一 playlist/delete 端点,语义由归属决定
    onDeletePlaylist: (playlistId: String) -> Unit = {},
    onUnsubscribePlaylist: (playlistId: String) -> Unit = {},
    // "创建的歌单"分区固定入口:弹窗在本文件,建单走 VM(成功后静默刷新)
    onCreatePlaylist: (name: String) -> Unit = {},
    onScrolling: (onTop: Boolean) -> Unit = {},
) {
    // 长按目标:待确认删除(自建)/取消收藏(他人歌单)的写操作,均弹窗确认
    var deletePlaylistTarget by remember { mutableStateOf<PlaylistsResult?>(null) }
    var unsubscribePlaylistTarget by remember { mutableStateOf<PlaylistsResult?>(null) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
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
    val likedList = (likedPlaylists as? LocalResource.Success)?.data.orEmpty()
    val autoList = (autoPlaylists as? LocalResource.Success)?.data.orEmpty()
    val albumList = (albums as? LocalResource.Success)?.data.orEmpty()
    val artistList = (artists as? LocalResource.Success)?.data.orEmpty()
    val anyLoading =
        playlists is LocalResource.Loading ||
            likedPlaylists is LocalResource.Loading ||
            autoPlaylists is LocalResource.Loading ||
            albums is LocalResource.Loading ||
            artists is LocalResource.Loading
    val hasContent =
        playlistList.isNotEmpty() || likedList.isNotEmpty() || autoList.isNotEmpty() ||
            albumList.isNotEmpty() || artistList.isNotEmpty()

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
                    // 系统歌单置顶满行(红心歌单 Liked Music 等),不与自建混排
                    if (autoList.isNotEmpty()) {
                        items(autoList, span = { GridItemSpan(maxLineSpan) }, key = { "yt_auto_${it.browseId}" }) { playlist ->
                            PinnedPlaylistRow(
                                playlist = playlist,
                                onClick = {
                                    navController.navigate(
                                        PlaylistDestination(playlist.browseId, isYourYouTubePlaylist = true),
                                    )
                                },
                            )
                        }
                    }

                    // "创建的歌单"分区常驻(与网易 tab 同构):固定"新建歌单"入口 tile 置顶
                    item(span = { GridItemSpan(maxLineSpan) }, key = "yt_cloud_header") {
                        SectionHeader(stringResource(Res.string.created_playlists))
                    }
                    item(key = "yt_create_playlist") {
                        CreatePlaylistTile(onClick = { showCreatePlaylist = true })
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
                            onLongClick = { deletePlaylistTarget = playlist },
                        )
                    }

                    if (likedList.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "yt_liked_header") {
                            SectionHeader(stringResource(Res.string.collected_playlists))
                        }
                        items(likedList, key = { "yt_liked_${it.browseId}" }) { playlist ->
                            HomeItemContentPlaylist(
                                onClick = {
                                    navController.navigate(
                                        PlaylistDestination(playlist.browseId, isYourYouTubePlaylist = false),
                                    )
                                },
                                data = playlist,
                                fillWidth = true,
                                onLongClick = { unsubscribePlaylistTarget = playlist },
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

    // 删除/取消收藏确认(写操作,共享组件,与网易 tab 同款结构)
    deletePlaylistTarget?.let { target ->
        LibraryRemoveConfirmDialog(
            title = stringResource(Res.string.delete_playlist_title),
            message = stringResource(Res.string.delete_youtube_playlist_message, target.title),
            onConfirm = {
                onDeletePlaylist(target.browseId)
                deletePlaylistTarget = null
            },
            onDismiss = { deletePlaylistTarget = null },
        )
    }
    unsubscribePlaylistTarget?.let { target ->
        LibraryRemoveConfirmDialog(
            title = stringResource(Res.string.unsubscribe_playlist_title),
            message = stringResource(Res.string.unsubscribe_youtube_playlist_message, target.title),
            onConfirm = {
                onUnsubscribePlaylist(target.browseId)
                unsubscribePlaylistTarget = null
            },
            onDismiss = { unsubscribePlaylistTarget = null },
        )
    }
    if (showCreatePlaylist) {
        CreatePlaylistDialog(
            onCreate = {
                showCreatePlaylist = false
                onCreatePlaylist(it)
            },
            onDismiss = { showCreatePlaylist = false },
        )
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

/** 置顶系统歌单满行横卡(同网易 tab 红心行样式):封面 + 标题 + 创建者副标题 */
@Composable
private fun PinnedPlaylistRow(
    playlist: PlaylistsResult,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model =
                ImageRequest
                    .Builder(LocalPlatformContext.current)
                    .data(playlist.thumbnails.lastOrNull()?.url)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(550)
                    .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(112.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp)),
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
        ) {
            Text(
                text = playlist.title,
                style = typo().titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (playlist.author.isNotBlank()) {
                Text(
                    text = playlist.author,
                    style = typo().bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
