package com.maxrave.simpmusic.ui.screen.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import com.maxrave.simpmusic.viewModel.DownloadEntryStatus
import com.maxrave.simpmusic.viewModel.DownloadManagementRow
import com.maxrave.simpmusic.ui.screen.library.DownloadViewMode
import com.maxrave.simpmusic.viewModel.sortRank
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.maxrave.common.LibraryChipType
import com.maxrave.domain.data.type.PlaylistType
import com.maxrave.domain.utils.toTrack
import com.maxrave.simpmusic.extension.copy
import com.maxrave.simpmusic.extension.scrollReportingEffect
import com.maxrave.simpmusic.ui.component.EndOfPage
import com.maxrave.simpmusic.ui.component.GridLibraryPlaylist
import com.maxrave.simpmusic.ui.component.LibraryTilingItem
import com.maxrave.simpmusic.ui.component.LibraryTilingState
import com.maxrave.simpmusic.ui.component.NowPlayingBottomSheet
import com.maxrave.simpmusic.ui.component.RippleIconButton
import com.maxrave.simpmusic.ui.component.SongFullWidthItems
import com.maxrave.simpmusic.ui.component.rememberSurfaceDarkColors
import com.maxrave.simpmusic.ui.icon.ArrowBackIosNew
import com.maxrave.simpmusic.ui.icon.Check
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.seed
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.LibraryDynamicPlaylistViewModel
import com.maxrave.simpmusic.viewModel.LibraryViewModel
import com.maxrave.simpmusic.viewModel.SharedViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.n_songs_selected
import simpmusic.composeapp.generated.resources.select_all
import simpmusic.composeapp.generated.resources.download_no_completed
import simpmusic.composeapp.generated.resources.download_no_in_progress
import simpmusic.composeapp.generated.resources.download_section_in_progress
import simpmusic.composeapp.generated.resources.download_section_completed
import simpmusic.composeapp.generated.resources.download_action_pause_all
import simpmusic.composeapp.generated.resources.download_action_resume_all
import simpmusic.composeapp.generated.resources.download_action_retry_failed
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.cancel
import simpmusic.composeapp.generated.resources.create
import simpmusic.composeapp.generated.resources.delete
import simpmusic.composeapp.generated.resources.downloaded
import simpmusic.composeapp.generated.resources.favorite
import simpmusic.composeapp.generated.resources.library_podcasts
import simpmusic.composeapp.generated.resources.no_favorite_playlists
import simpmusic.composeapp.generated.resources.no_favorite_podcasts
import simpmusic.composeapp.generated.resources.no_playlists_added
import simpmusic.composeapp.generated.resources.no_playlists_downloaded
import simpmusic.composeapp.generated.resources.playlist_name
import simpmusic.composeapp.generated.resources.playlist_name_cannot_be_empty
import simpmusic.composeapp.generated.resources.playlists
import simpmusic.composeapp.generated.resources.remove_download_message
import simpmusic.composeapp.generated.resources.remove_download_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryCollectionScreen(
    innerPadding: PaddingValues,
    navController: NavController,
    type: String,
    viewModel: LibraryViewModel = koinViewModel(),
    dynamicPlaylistViewModel: LibraryDynamicPlaylistViewModel = koinViewModel(),
    sharedViewModel: SharedViewModel = koinViewModel(),
) {
    val pageType = runCatching { LibraryChipType.valueOf(type) }.getOrDefault(LibraryChipType.LOCAL_PLAYLIST)
    val localPlaylists by viewModel.yourLocalPlaylist.collectAsStateWithLifecycle()
    val collections by viewModel.favoritePlaylist.collectAsStateWithLifecycle()
    val podcasts by viewModel.favoritePodcasts.collectAsStateWithLifecycle()
    var showCreatePlaylist by remember { mutableStateOf(false) }

    LaunchedEffect(pageType) {
        when (pageType) {
            LibraryChipType.LOCAL_PLAYLIST -> viewModel.getLocalPlaylist()
            LibraryChipType.FAVORITE_PLAYLIST -> viewModel.getPlaylistFavorite()
            LibraryChipType.DOWNLOADED_PLAYLIST -> viewModel.getDownloadedPlaylist()
            LibraryChipType.FAVORITE_PODCAST -> viewModel.getFavoritePodcasts()
            else -> Unit
        }
    }

    val title =
        when (pageType) {
            LibraryChipType.LOCAL_PLAYLIST -> stringResource(Res.string.playlists)
            LibraryChipType.FAVORITE_PLAYLIST -> stringResource(Res.string.favorite)
            LibraryChipType.DOWNLOADED_PLAYLIST -> stringResource(Res.string.downloaded)
            LibraryChipType.FAVORITE_PODCAST -> stringResource(Res.string.library_podcasts)
            else -> stringResource(Res.string.playlists)
        }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = title, style = typo().titleMedium) },
                navigationIcon = {
                    RippleIconButton(
                        imageVector = SimpIcons.ArrowBackIosNew,
                        tint = MaterialTheme.colorScheme.onBackground,
                        onClick = { navController.popBackStack() },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { scaffoldPadding ->
        val contentPadding =
            scaffoldPadding.copy(
                bottom = innerPadding.calculateBottomPadding(),
            )
        when (pageType) {
            LibraryChipType.LOCAL_PLAYLIST -> {
                GridLibraryPlaylist(
                    navController = navController,
                    contentPadding = contentPadding,
                    data = localPlaylists,
                    emptyText = Res.string.no_playlists_added,
                    createNewPlaylist = { showCreatePlaylist = true },
                    onReload = viewModel::getLocalPlaylist,
                )
            }

            LibraryChipType.FAVORITE_PLAYLIST -> {
                GridLibraryPlaylist(
                    navController = navController,
                    contentPadding = contentPadding,
                    data = collections,
                    emptyText = Res.string.no_favorite_playlists,
                    onReload = viewModel::getPlaylistFavorite,
                )
            }

            LibraryChipType.DOWNLOADED_PLAYLIST -> {
                DownloadedManagementBody(
                    topPadding = contentPadding.calculateTopPadding(),
                    bottomPadding = contentPadding.calculateBottomPadding(),
                    navController = navController,
                    viewModel = viewModel,
                    dynamicPlaylistViewModel = dynamicPlaylistViewModel,
                    sharedViewModel = sharedViewModel,
                )
            }

            LibraryChipType.FAVORITE_PODCAST -> {
                GridLibraryPlaylist(
                    navController = navController,
                    contentPadding = contentPadding,
                    data = podcasts,
                    emptyText = Res.string.no_favorite_podcasts,
                    onReload = viewModel::getFavoritePodcasts,
                )
            }

            else -> Unit
        }
    }


    if (showCreatePlaylist) {
        CreateLocalPlaylistSheet(
            viewModel = viewModel,
            onDismiss = { showCreatePlaylist = false },
        )
    }

}

/**
 * “下载管理”页体(下载中/已完成两视角):既被独立路由 [LibraryCollectionScreen] 用,也被
 * 库页 chip 页(DOWNLOADED_PLAYLIST 分支)直接内嵌——chip 页没有自己的 TopAppBar,
 * 两处共用同一份内容,各自处理顶/底 padding。
 * 2026-10-01 用户定稿:tab 不再按内容分(歌曲/视频/播客),改为按状态分——行全量混排,
 * 视角行渲染按”有无视频条目”切;统计(N首·总大小)放”已完成”磁贴副标题上。
 */
@Composable
fun DownloadedManagementBody(
    topPadding: Dp,
    bottomPadding: Dp,
    navController: NavController,
    viewModel: LibraryViewModel,
    dynamicPlaylistViewModel: LibraryDynamicPlaylistViewModel,
    sharedViewModel: SharedViewModel,
    // 库页 chip 内嵌时接顶栏收起信号(与其它 chip 页同款);独立路由不传,不参与
    onScrolling: (onTop: Boolean) -> Unit = {},
) {
    val managementRows by dynamicPlaylistViewModel.downloadManagementRows.collectAsStateWithLifecycle()
    val nowPlaying by sharedViewModel.nowPlayingState.collectAsStateWithLifecycle()
    var downloadedSection by remember { mutableStateOf(DownloadedSection.Completed) }
    var selectedDownloadedSong by remember { mutableStateOf<com.maxrave.domain.data.entities.SongEntity?>(null) }
    // 行删除/取消的确认目标(videoId+标题);在途”取消下载”也走确认防误触
    var deleteRowTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    // 多选模式(用户反馈 2026-10):长按行进入;批量删除所选
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var deleteSelectionConfirm by remember { mutableStateOf(false) }
    // 暂停全部只对 DownloadManager 在途(下载中/排队)有效;转存中(EXPORTING)不走队列停不了
    val hasPausable = managementRows.any { listOf(it.audioStatus.sortRank(), it.videoStatus?.sortRank() ?: 9).any { r -> r == 0 || r == 2 } }
    val hasPaused = managementRows.any { it.audioStatus == DownloadEntryStatus.PAUSED || it.videoStatus == DownloadEntryStatus.PAUSED }
    val hasFailed = managementRows.any { it.audioStatus == DownloadEntryStatus.FAILED || it.videoStatus == DownloadEntryStatus.FAILED }

    // 分组=两个 tab:在途(下载中/排队/暂停/失败/转存中)与已完成(含丢失/旧缓存);混排不分类
    val rankOf: (DownloadManagementRow) -> Int = { row ->
        minOf(row.audioStatus.sortRank(), row.videoStatus?.sortRank() ?: Int.MAX_VALUE)
    }
    val inFlightRows = managementRows.filter { rankOf(it) <= 4 }
    val completedRows = managementRows.filter { rankOf(it) >= 5 }
    // “已完成”磁贴统计:只数真完成(文件在=rank 6,丢失行不计也不数)
    val completedCount = completedRows.count { rankOf(it) == 6 }

    Column(modifier = Modifier.fillMaxSize().padding(top = topPadding)) {
        // "下载中"磁贴标题带动态计数(2026-10-02 用户定:页内不再有"下载中 (N)"文案,
        // 计数上磁贴;0 时不带括号)——managementRows 是实时流,增删任务自动刷新
        val inProgressTitle =
            if (inFlightRows.isNotEmpty()) {
                stringResource(Res.string.download_section_in_progress) + " (${inFlightRows.size})"
            } else {
                stringResource(Res.string.download_section_in_progress)
            }
        Row(
            // 库页统一口径:水平边距 15dp 与主页/各 chip 一致(原 10dp 与其它页不同口径)
            modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                LibraryTilingItem(
                    state = LibraryTilingState.DownloadInProgress,
                    selected = downloadedSection == DownloadedSection.InProgress,
                    titleString = inProgressTitle,
                    onClick = { downloadedSection = DownloadedSection.InProgress },
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                // 统计直接跟在标题后(2026-10-02 用户定):"已完成 (N)"——只带计数不带容量
                // (用户复定:容量可能展示不开);空库只显示"已完成"
                val completedWord = stringResource(Res.string.download_section_completed)
                LibraryTilingItem(
                    state = LibraryTilingState.DownloadCompleted,
                    selected = downloadedSection == DownloadedSection.Completed,
                    titleString = if (completedCount > 0) "$completedWord ($completedCount)" else completedWord,
                    onClick = { downloadedSection = DownloadedSection.Completed },
                )
            }
        }
        val isInProgressTab = downloadedSection == DownloadedSection.InProgress
        val tabRows = if (isInProgressTab) inFlightRows else completedRows
        Box(modifier = Modifier.weight(1f)) {
            if (tabRows.isEmpty()) {
                    // 空态=恒在顶:切页不再统一复位标题,从收起态的深滚动页切到空页时
                    // 若不上报,标题会继承收起态且没有任何滚动事件可纠正(CR-31)
                    LaunchedEffect(Unit) { onScrolling(true) }
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text =
                                stringResource(
                                    if (isInProgressTab) {
                                        Res.string.download_no_in_progress
                                    } else {
                                        Res.string.download_no_completed
                                    },
                                ),
                            style = typo().labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    // 顶栏收起信号:Songs 列表自报(与其它 chip 页同一套口径,
                    // 含几何反馈断路,见 scrollReportingEffect 注释)
                    val songsListState = rememberLazyListState()
                    songsListState.scrollReportingEffect(onScrolling)
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (selectionMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                // 通用全选控件(2026-10-01 用户定):行首圆圈+文字两态切换
                                // (全选↔清空),视觉与行多选圆圈同款;不再是纯文字按钮
                                val allIds = tabRows.map { it.song.videoId }.toSet()
                                val allSelected = allIds.isNotEmpty() && selectedIds.containsAll(allIds)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier =
                                        Modifier
                                            .clip(CircleShape)
                                            .clickable {
                                                selectedIds = if (allSelected) emptySet() else allIds
                                            }.padding(vertical = 4.dp, horizontal = 2.dp),
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(if (allSelected) seed else Color.Transparent)
                                                .border(
                                                    width = 1.5.dp,
                                                    color = if (allSelected) seed else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                    shape = CircleShape,
                                                ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (allSelected) {
                                            Icon(
                                                imageVector = SimpIcons.Check,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(Res.string.select_all),
                                        style = typo().labelMedium,
                                    )
                                }
                                Text(
                                    text = stringResource(Res.string.n_songs_selected, selectedIds.size),
                                    style = typo().labelMedium,
                                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                                )
                                TextButton(
                                    onClick = { if (selectedIds.isNotEmpty()) deleteSelectionConfirm = true },
                                ) { Text(stringResource(Res.string.delete), style = typo().labelMedium, color = MaterialTheme.colorScheme.error) }
                                TextButton(onClick = {
                                    selectionMode = false
                                    selectedIds = emptySet()
                                }) { Text(stringResource(Res.string.cancel), style = typo().labelMedium) }
                            }
                        }
                        LazyColumn(
                            state = songsListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = bottomPadding),
                        ) {
                            // 在途 tab 头:批量操作 pills(暂停全部/继续/重试失败)。
                            // "下载中 (N)"计数文案已撤(2026-10-02 用户定,计数在磁贴上),
                            // 无可操作项时整行不出
                            if (isInProgressTab && (hasPausable || hasPaused || hasFailed)) {
                                item(key = "header-inflight") {
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 15.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        if (hasPausable) {
                                            DownloadHeaderPill(text = stringResource(Res.string.download_action_pause_all)) {
                                                dynamicPlaylistViewModel.pauseAllDownloads()
                                            }
                                        }
                                        if (hasPaused) {
                                            DownloadHeaderPill(text = stringResource(Res.string.download_action_resume_all)) {
                                                dynamicPlaylistViewModel.resumeAllDownloads()
                                            }
                                        }
                                        if (hasFailed) {
                                            DownloadHeaderPill(text = stringResource(Res.string.download_action_retry_failed)) {
                                                dynamicPlaylistViewModel.retryFailedDownloads()
                                            }
                                        }
                                    }
                                }
                            }
                            items(tabRows, key = { it.song.videoId }) { row ->
                                DownloadManagementRowItem(
                                    row = row,
                                    // 正在播放判定双源(2026-10-02):track 在队列 rebuild 窗口
                                    // (updateCatalog 清空回填)里 find 落空恒 null,拿 DB 落的
                                    // songEntity 兜底——与 NowPlayingScreen 同款口径
                                    isPlaying =
                                        (nowPlaying?.songEntity?.videoId ?: nowPlaying?.track?.videoId) == row.song.videoId,
                                    // 行视角(混排后):有视频条目的行看视频条目(mp4 是它的
                                    // 落地产物,在途进度也在视频上),纯音频行看音频
                                    viewMode = if (row.videoStatus != null) DownloadViewMode.VIDEO else DownloadViewMode.AUDIO,
                                    selectionMode = selectionMode,
                                    selectedIds = selectedIds,
                                    onEnterSelection = {
                                        selectionMode = true
                                        selectedIds = setOf(row.song.videoId)
                                    },
                                    onToggle = { id ->
                                        selectedIds =
                                            if (id in selectedIds) selectedIds - id else selectedIds + id
                                    },
                                    dynamicPlaylistViewModel = dynamicPlaylistViewModel,
                                    onRedownload = {
                                        if (row.videoStatus != null) {
                                            dynamicPlaylistViewModel.redownloadVideo(row.song.videoId)
                                        } else {
                                            dynamicPlaylistViewModel.redownload(row.song.videoId)
                                        }
                                    },
                                    onDelete = { deleteRowTarget = row.song.videoId to row.song.title },
                                    onSongMenu = { selectedDownloadedSong = row.song },
                                )
                            }
                            item { EndOfPage(includeBottomBarPadding = false) }
                        }
                    }
                }
        }
    }

    selectedDownloadedSong?.let { song ->
        NowPlayingBottomSheet(
            onDismiss = { selectedDownloadedSong = null },
            navController = navController,
            song = song,
        )
    }

    if (deleteSelectionConfirm) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            onDismissRequest = { deleteSelectionConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    deleteSelectionConfirm = false
                    // 混排 tab(2026-10-01):删除=整行清理(音视频文件+条目+Room)
                    selectedIds.forEach { id ->
                        dynamicPlaylistViewModel.deleteDownload(id)
                    }
                    selectionMode = false
                    selectedIds = emptySet()
                }) {
                    Text(stringResource(Res.string.delete), style = typo().labelSmall)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteSelectionConfirm = false }) {
                    Text(stringResource(Res.string.cancel), style = typo().labelSmall)
                }
            },
            title = { Text(stringResource(Res.string.remove_download_title), style = typo().labelSmall) },
            text = {
                Text(
                    stringResource(Res.string.remove_download_message) + " (" + selectedIds.size + ")",
                    style = typo().bodyMedium,
                )
            },
        )
    }

    // 条目删除/取消下载确认(在途取消与完成删除都破坏数据,防误触)
    deleteRowTarget?.let { (videoId, title) ->
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            onDismissRequest = { deleteRowTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    deleteRowTarget = null
                    dynamicPlaylistViewModel.deleteDownload(videoId)
                }) {
                    Text(stringResource(Res.string.delete), style = typo().labelSmall)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteRowTarget = null }) {
                    Text(stringResource(Res.string.cancel), style = typo().labelSmall)
                }
            },
            title = { Text(title, style = typo().labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = { Text(stringResource(Res.string.remove_download_message), style = typo().bodyMedium) },
        )
    }

}

/** "下载中"节头右侧的操作(用户定稿:纯文字无底色,primary 色提示可点) */
@Composable
private fun DownloadHeaderPill(
    text: String,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = typo().labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** 两组节共用的行渲染(避免下载中/已完成两段 items 重复二十行接线) */
@Composable
private fun DownloadManagementRowItem(
    row: com.maxrave.simpmusic.viewModel.DownloadManagementRow,
    isPlaying: Boolean,
    viewMode: DownloadViewMode,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onEnterSelection: () -> Unit,
    onToggle: (String) -> Unit,
    dynamicPlaylistViewModel: LibraryDynamicPlaylistViewModel,
    onRedownload: () -> Unit,
    onDelete: () -> Unit,
    onSongMenu: () -> Unit,
) {
    DownloadManagementItem(
        row = row,
        isPlaying = isPlaying,
        viewMode = viewMode,
        selectionMode = selectionMode,
        isSelected = row.song.videoId in selectedIds,
        onLongClick = onEnterSelection,
        onSelectToggle = { onToggle(row.song.videoId) },
        onPlay = {
            dynamicPlaylistViewModel.playSong(
                row.song.videoId,
                LibraryDynamicPlaylistType.Downloaded,
            )
        },
        onPause = { dynamicPlaylistViewModel.pauseDownload(row.song.videoId) },
        onResume = { dynamicPlaylistViewModel.resumeDownload(row.song.videoId) },
        onRetry = { dynamicPlaylistViewModel.retryDownload(row.song.videoId) },
        onRedownload = onRedownload,
        onDelete = onDelete,
        onSongMenu = onSongMenu,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 下载管理页两视角(2026-10-01 用户定):下载中/已完成,行混排不按内容分类 */
internal enum class DownloadedSection {
    InProgress,
    Completed,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateLocalPlaylistSheet(
    viewModel: LibraryViewModel,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val close = {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        contentColor = Color.Transparent,
        dragHandle = null,
        scrimColor = Color.Black.copy(alpha = .5f),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.width(60.dp).height(4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(50),
                ) {}
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(text = stringResource(Res.string.playlist_name)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
                TextButton(
                    onClick = {
                        if (title.isBlank()) {
                            viewModel.makeToast(runBlocking { getString(Res.string.playlist_name_cannot_be_empty) })
                        } else {
                            viewModel.createPlaylist(title)
                            close()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(Res.string.create))
                }
            }
        }
    }
}
