package com.maxrave.simpmusic.ui.screen.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
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
import com.maxrave.simpmusic.ui.theme.typo
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.cancel
import simpmusic.composeapp.generated.resources.delete_playlist_message
import simpmusic.composeapp.generated.resources.delete_playlist_title
import simpmusic.composeapp.generated.resources.delete
import simpmusic.composeapp.generated.resources.followed
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

            else ->
                LazyVerticalGrid(
                    columns = GridCells.FixedSize(size = 132.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    contentPadding = contentPadding,
                    state = state,
                ) {
                    if (playlistList.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "netease_playlists_header") {
                            SectionHeader(stringResource(Res.string.netease_playlists))
                        }
                        items(playlistList, key = { "netease_pl_${it.id}" }) { playlist ->
                            HomeItemContentPlaylist(
                                onClick = {
                                    // 纯数字 ID 自动走网易歌单详情(M2 同页路由)
                                    navController.navigate(PlaylistDestination(playlist.id))
                                },
                                data = playlist,
                                thumbSize = 132.dp,
                                // 长按三分支:收藏歌单=取消收藏;自建非红心=删除歌单(强确认);红心歌单=无
                                onLongClick =
                                    when {
                                        playlist.id !in ownPlaylistIds -> {
                                            { unsubscribePlaylistTarget = playlist }
                                        }

                                        playlist.id != likedPlaylistId -> {
                                            { deletePlaylistTarget = playlist }
                                        }

                                        else -> null
                                    },
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
                            )
                        }
                    }

                    item(span = { GridItemSpan(maxLineSpan) }, key = "netease_end") {
                        EndOfPage()
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

/** 分区标题:对齐网易主页行标题(headlineMedium + 15dp 水平缩进) */
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
                start = 15.dp,
                top = 10.dp,
                bottom = 4.dp,
            ),
    )
}
