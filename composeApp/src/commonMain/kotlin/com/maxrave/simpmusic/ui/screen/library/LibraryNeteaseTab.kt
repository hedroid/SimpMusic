package com.maxrave.simpmusic.ui.screen.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
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
import com.maxrave.domain.data.entities.PlaylistEntity
import com.maxrave.domain.data.model.searchResult.albums.AlbumsResult
import com.maxrave.domain.data.model.searchResult.artists.ArtistsResult
import com.maxrave.domain.utils.LocalResource
import com.maxrave.simpmusic.extension.isScrollingUp
import com.maxrave.simpmusic.ui.component.CenterLoadingBox
import com.maxrave.simpmusic.ui.component.EndOfPage
import com.maxrave.simpmusic.ui.component.HomeItemContentPlaylist
import com.maxrave.simpmusic.ui.component.rememberSurfaceDarkColors
import com.maxrave.simpmusic.ui.navigation.destination.list.PlaylistDestination
import com.maxrave.simpmusic.ui.screen.home.NeteaseAlbumRow
import com.maxrave.simpmusic.ui.screen.home.NeteaseArtistRow
import com.maxrave.simpmusic.ui.theme.LibraryGridDefaults
import com.maxrave.simpmusic.ui.theme.typo
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.cancel
import simpmusic.composeapp.generated.resources.delete_playlist_message
import simpmusic.composeapp.generated.resources.delete_playlist_title
import simpmusic.composeapp.generated.resources.delete
import simpmusic.composeapp.generated.resources.followed
import simpmusic.composeapp.generated.resources.track_count_short
import simpmusic.composeapp.generated.resources.collected_playlists
import simpmusic.composeapp.generated.resources.created_playlists
import simpmusic.composeapp.generated.resources.unsubscribe_album_message
import simpmusic.composeapp.generated.resources.unsubscribe_album_title
import simpmusic.composeapp.generated.resources.unsubscribe_playlist_message
import simpmusic.composeapp.generated.resources.unsubscribe_playlist_title
import simpmusic.composeapp.generated.resources.netease_playlists
import simpmusic.composeapp.generated.resources.no_netease_content
import simpmusic.composeapp.generated.resources.starred_albums

/**
 * "您的网易云"tab(M2 库融合 + 原主页两行迁入):三分区单页——
 * 歌单(红心置顶)132dp tile 网格 / 收藏的专辑(主页新碟上架行同款横滑) /
 * 关注的歌手(主页热门歌手行同款 3 行横滑,沉底)。三分区并行拉取、独立降级(失败的分区
 * 直接隐藏,不拖垮整页);全部分区空才显示空态。下拉刷新 force 绕过行缓存,
 * 且不清空已显示分区(原地替换)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryNeteaseTab(
    navController: NavController,
    contentPadding: PaddingValues,
    playlists: LocalResource<List<PlaylistEntity>>,
    artists: LocalResource<List<ArtistsResult>>,
    albums: LocalResource<List<AlbumsResult>>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    // 自建歌单 ID 集 + 红心歌单 id:长按三分支——收藏歌单=取消收藏,自建(非红心)=删除歌单,红心=无操作
    ownPlaylistIds: Set<String> = emptySet(),
    likedPlaylistId: String? = null,
    onUnsubscribePlaylist: (playlistId: String) -> Unit = {},
    onDeletePlaylist: (playlistId: String) -> Unit = {},
    onUnsubscribeAlbum: (albumId: String) -> Unit = {},
    onScrolling: (onTop: Boolean) -> Unit = {},
) {
    // 长按目标:待确认取消收藏/删除的歌单/专辑(均为写操作,弹窗确认;删除不可逆,文案更强)
    var unsubscribePlaylistTarget by remember { mutableStateOf<PlaylistEntity?>(null) }
    var deletePlaylistTarget by remember { mutableStateOf<PlaylistEntity?>(null) }
    var unsubscribeAlbumTarget by remember { mutableStateOf<AlbumsResult?>(null) }
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
    val artistList = (artists as? LocalResource.Success)?.data.orEmpty()
    val albumList = (albums as? LocalResource.Success)?.data.orEmpty()
    val anyLoading =
        playlists is LocalResource.Loading || artists is LocalResource.Loading || albums is LocalResource.Loading
    val hasContent = playlistList.isNotEmpty() || artistList.isNotEmpty() || albumList.isNotEmpty()

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
            // 首拉:整页 spinner(与其它库分区 tab 的 Loading 态一致)
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
                        text = stringResource(Res.string.no_netease_content),
                        style = typo().bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            else -> {
                // 红心歌单单独一行(用户 2026-09-20 定案):从网格里抽出,满行横卡置顶;
                // 其余按自建/收藏分区(用户点名:要区分自己歌单和收藏歌单)。
                // 全宽行(专辑/歌手/标题)不再自带水平 padding——网格整体已缩进。
                val heartPlaylist = playlistList.firstOrNull { it.id == likedPlaylistId }
                val otherPlaylists = playlistList.filterNot { it.id == likedPlaylistId }
                val ownPlaylists = otherPlaylists.filter { it.id in ownPlaylistIds }
                val collectedPlaylists = otherPlaylists.filter { it.id !in ownPlaylistIds }
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
                    if (heartPlaylist != null) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "netease_heart_row") {
                            HeartPlaylistRow(
                                playlist = heartPlaylist,
                                onClick = {
                                    navController.navigate(PlaylistDestination(heartPlaylist.id))
                                },
                            )
                        }
                    }
                    if (ownPlaylists.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "netease_created_header") {
                            SectionHeader(stringResource(Res.string.created_playlists))
                        }
                        items(ownPlaylists, key = { "netease_pl_${it.id}" }) { playlist ->
                            NeteasePlaylistTile(
                                playlist = playlist,
                                likedPlaylistId = likedPlaylistId,
                                ownPlaylistIds = ownPlaylistIds,
                                navController = navController,
                                onUnsubscribe = { unsubscribePlaylistTarget = playlist },
                                onDelete = { deletePlaylistTarget = playlist },
                            )
                        }
                    }

                    if (collectedPlaylists.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "netease_collected_header") {
                            SectionHeader(stringResource(Res.string.collected_playlists))
                        }
                        items(collectedPlaylists, key = { "netease_pl_${it.id}" }) { playlist ->
                            NeteasePlaylistTile(
                                playlist = playlist,
                                likedPlaylistId = likedPlaylistId,
                                ownPlaylistIds = ownPlaylistIds,
                                navController = navController,
                                onUnsubscribe = { unsubscribePlaylistTarget = playlist },
                                onDelete = { deletePlaylistTarget = playlist },
                            )
                        }
                    }

                    if (albumList.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "netease_albums_row") {
                            NeteaseAlbumRow(
                                title = stringResource(Res.string.starred_albums),
                                albums = albumList,
                                navController = navController,
                                onAlbumLongClick = { album -> unsubscribeAlbumTarget = album },
                                horizontalPadding = 0.dp,
                            )
                        }
                    }

                    // 关注的歌手沉底(用户偏好):歌单 → 收藏的专辑 → 关注的歌手
                    if (artistList.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "netease_artists_row") {
                            NeteaseArtistRow(
                                title = stringResource(Res.string.followed),
                                artists = artistList,
                                showRank = false,
                                navController = navController,
                                horizontalPadding = 0.dp,
                            )
                        }
                    }

                    item(span = { GridItemSpan(maxLineSpan) }, key = "netease_end") {
                        EndOfPage()
                    }
                }
            }
        }
    }

    // 取消收藏确认(写操作):与库页"移除下载"弹窗同款结构
    unsubscribePlaylistTarget?.let { target ->
        NeteaseUnsubscribeDialog(
            title = stringResource(Res.string.unsubscribe_playlist_title),
            message = stringResource(Res.string.unsubscribe_playlist_message, target.title),
            onConfirm = {
                onUnsubscribePlaylist(target.id)
                unsubscribePlaylistTarget = null
            },
            onDismiss = { unsubscribePlaylistTarget = null },
        )
    }
    deletePlaylistTarget?.let { target ->
        NeteaseUnsubscribeDialog(
            title = stringResource(Res.string.delete_playlist_title),
            message = stringResource(Res.string.delete_playlist_message, target.title),
            onConfirm = {
                onDeletePlaylist(target.id)
                deletePlaylistTarget = null
            },
            onDismiss = { deletePlaylistTarget = null },
        )
    }
    unsubscribeAlbumTarget?.let { target ->
        NeteaseUnsubscribeDialog(
            title = stringResource(Res.string.unsubscribe_album_title),
            message = stringResource(Res.string.unsubscribe_album_message, target.title),
            onConfirm = {
                onUnsubscribeAlbum(target.browseId)
                unsubscribeAlbumTarget = null
            },
            onDismiss = { unsubscribeAlbumTarget = null },
        )
    }
}

@Composable
private fun NeteaseUnsubscribeDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        containerColor = rememberSurfaceDarkColors().container,
        titleContentColor = rememberSurfaceDarkColors().content,
        textContentColor = rememberSurfaceDarkColors().content,
        title = { Text(text = title) },
        text = { Text(text = message) },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(Res.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.cancel))
            }
        },
    )
}

/** 分区标题:对齐网易主页行标题(headlineMedium);网格已统一 15dp 边距,行内不再水平 pad */
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

/** 歌单 tile(自建/收藏两分区共用):长按三分支——收藏=取消收藏,自建非红心=删除,红心=无 */
@Composable
private fun NeteasePlaylistTile(
    playlist: PlaylistEntity,
    likedPlaylistId: String?,
    ownPlaylistIds: Set<String>,
    navController: NavController,
    onUnsubscribe: () -> Unit,
    onDelete: () -> Unit,
) {
    HomeItemContentPlaylist(
        onClick = {
            // 纯数字 ID 自动走网易歌单详情(M2 同页路由)
            navController.navigate(PlaylistDestination(playlist.id))
        },
        data = playlist,
        onLongClick =
            when {
                playlist.id !in ownPlaylistIds -> onUnsubscribe
                playlist.id != likedPlaylistId -> onDelete
                else -> null
            },
        fillWidth = true,
    )
}

/**
 * 红心歌单满行横卡(用户 2026-09-20 定案"单独在一行"):封面 + 标题 + 曲目数。
 * 播放页红心按钮/红心电台与此同一份数据;点击进红心歌单详情页。
 */
@Composable
private fun HeartPlaylistRow(
    playlist: PlaylistEntity,
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
                    .data(playlist.thumbnails)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .diskCacheKey(playlist.thumbnails)
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
            Text(
                text = stringResource(Res.string.track_count_short, playlist.trackCount.toString()),
                style = typo().bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
