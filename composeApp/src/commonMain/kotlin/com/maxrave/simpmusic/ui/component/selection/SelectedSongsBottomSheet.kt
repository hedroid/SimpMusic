package com.maxrave.simpmusic.ui.component.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maxrave.simpmusic.ui.component.ActionButton
import com.maxrave.simpmusic.ui.component.EndOfModalBottomSheet
import com.maxrave.simpmusic.ui.component.rememberSurfaceDarkColors
import com.maxrave.simpmusic.ui.icon.Download
import com.maxrave.simpmusic.ui.icon.DownloadForOffline
import com.maxrave.simpmusic.ui.icon.Favorite
import com.maxrave.simpmusic.ui.icon.PlaylistAdd
import com.maxrave.simpmusic.ui.icon.QueueMusic
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.icon.SkipNext
import com.maxrave.simpmusic.ui.theme.typo
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.add_to_a_playlist
import simpmusic.composeapp.generated.resources.add_to_queue
import simpmusic.composeapp.generated.resources.cancel
import simpmusic.composeapp.generated.resources.delete
import simpmusic.composeapp.generated.resources.download
import simpmusic.composeapp.generated.resources.downloaded
import simpmusic.composeapp.generated.resources.like
import simpmusic.composeapp.generated.resources.n_songs_selected
import simpmusic.composeapp.generated.resources.mixed_source_selection
import simpmusic.composeapp.generated.resources.netease
import simpmusic.composeapp.generated.resources.login_required_short
import simpmusic.composeapp.generated.resources.play_next
import simpmusic.composeapp.generated.resources.remove_download_message
import simpmusic.composeapp.generated.resources.remove_download_title

/**
 * One row in [SelectedSongsBottomSheet] that only some screens have — "remove from playlist"
 * means nothing on an album or in search results, so it is passed in rather than hardcoded here.
 */
data class SongSelectionAction(
    val icon: ImageVector,
    val label: String,
    val tint: Color? = null,
    val onClick: () -> Unit,
)

/**
 * Actions for the current selection, opened from the overflow button in [SongSelectionTopAppBar].
 * A null callback hides its row, so a screen only offers what it can actually do.
 *
 * The download row has two faces: with [allDownloaded] false (or [onRemoveDownload] null) it is
 * the plain "download" that only fills in what is missing; with both set — the downloaded-songs
 * grid, where every pick is already on disk — it reads "downloaded" in the accent blue and
 * removes the batch instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectedSongsBottomSheet(
    count: Int,
    onDismiss: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    // 当前选中集合的 videoId(按 id 形状判源):混源时"添加到歌单"置灰——歌单按源互斥,
    // 混选加歌只会丢一半,宁可不做(2026-09-24 用户定案)
    selectionIds: List<String> = emptyList(),
    onDownload: (() -> Unit)? = null,
    allDownloaded: Boolean = false,
    onRemoveDownload: (() -> Unit)? = null,
    onAddToFavorite: (() -> Unit)? = null,
    extraActions: List<SongSelectionAction> = emptyList(),
) {
    val coroutineScope = rememberCoroutineScope()
    val colors = rememberSurfaceDarkColors()
    val mixedSources =
        selectionIds.map { it.toLongOrNull() != null }.distinct().size > 1
    // 涉及源未登录也挡(与点赞同款严格门控):登录态在组件内读,7 个调用方零改动
    val dataStoreManager: com.maxrave.domain.manager.DataStoreManager = org.koin.compose.koinInject()
    val neteaseCookie by dataStoreManager.neteaseCookie.collectAsStateWithLifecycle("")
    val ytLoggedIn by dataStoreManager.loggedIn.collectAsStateWithLifecycle(null)
    val loggedOutSource =
        selectionIds.any { id ->
            (id.toLongOrNull() != null && neteaseCookie.isBlank()) ||
                (id.toLongOrNull() == null && ytLoggedIn != com.maxrave.domain.manager.DataStoreManager.TRUE)
        }
    // 混源优先提示(先解决源一致性,登录是下一层)
    val addBlockedReason: Int? =
        when {
            selectionIds.isEmpty() -> null
            mixedSources -> 1
            loggedOutSource -> 2
            else -> null
        }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Confirmed inside the sheet so the nine call sites only wire state and a callback — none of
    // them has to draw its own dialog for the batch removal.
    var showRemoveDownloadDialog by rememberSaveable { mutableStateOf(false) }
    val hideThen: (() -> Unit) -> Unit = { action ->
        coroutineScope.launch {
            sheetState.hide()
            onDismiss()
            action()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        contentColor = Color.Transparent,
        dragHandle = null,
        scrimColor = Color.Black.copy(alpha = .5f),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
            colors = CardDefaults.cardColors().copy(containerColor = colors.container),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(5.dp))
                Card(
                    modifier =
                        Modifier
                            .width(60.dp)
                            .height(4.dp),
                    colors = CardDefaults.cardColors().copy(containerColor = colors.handle),
                    shape = RoundedCornerShape(50),
                ) {}
                Spacer(modifier = Modifier.height(5.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Top,
                ) {
                    Text(
                        text = stringResource(Res.string.n_songs_selected, count),
                        style = typo().bodySmall,
                        color = colors.subtitle,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                    if (onPlayNext != null) {
                        ActionButton(
                            icon = SimpIcons.SkipNext,
                            text = Res.string.play_next,
                        ) { hideThen(onPlayNext) }
                    }
                    if (onAddToQueue != null) {
                        ActionButton(
                            icon = SimpIcons.QueueMusic,
                            text = Res.string.add_to_queue,
                        ) { hideThen(onAddToQueue) }
                    }
                    if (onAddToPlaylist != null) {
                        ActionButton(
                            icon = SimpIcons.PlaylistAdd,
                            text = Res.string.add_to_a_playlist,
                            enable = addBlockedReason == null,
                        ) { hideThen(onAddToPlaylist) }
                        // 第二排提示:首字与按钮文字左对齐(20+48+10=78dp,ActionButton 同款起点),置灰
                        // 混源→含不同音源;涉及源未登录→登录后可用
                        when (addBlockedReason) {
                            1 ->
                                Text(
                                    text = stringResource(Res.string.mixed_source_selection),
                                    style = typo().labelSmall,
                                    color = colors.disabled,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(start = 78.dp, end = 20.dp, top = 0.dp, bottom = 2.dp),
                                )

                            2 -> {
                                // 同源选中(混源已被上一档挡),未登录的源至多一个;带源名提示
                                val loggedOutNetease = selectionIds.any { it.toLongOrNull() != null }
                                Text(
                                    text =
                                        stringResource(
                                            Res.string.login_required_short,
                                            if (loggedOutNetease) stringResource(Res.string.netease) else "YouTube Music",
                                        ),
                                    style = typo().labelSmall,
                                    color = colors.disabled,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(start = 78.dp, end = 20.dp, top = 0.dp, bottom = 2.dp),
                                )
                            }
                        }
                    }
                    if (allDownloaded && onRemoveDownload != null) {
                        // Same accent blue the single-song menu wears once a track is on disk.
                        ActionButton(
                            icon = SimpIcons.DownloadForOffline,
                            text = Res.string.downloaded,
                            iconColor = Color(0xFF00A0CB),
                        ) { showRemoveDownloadDialog = true }
                    } else if (onDownload != null) {
                        ActionButton(
                            icon = SimpIcons.Download,
                            text = Res.string.download,
                        ) { hideThen(onDownload) }
                    }
                    if (onAddToFavorite != null) {
                        // 文案与单曲三点菜单一致("点赞");favorite="收藏"易误读成本地收藏
                        ActionButton(
                            icon = SimpIcons.Favorite,
                            text = Res.string.like,
                        ) { hideThen(onAddToFavorite) }
                    }
                    extraActions.forEach { action ->
                        ActionButton(
                            icon = action.icon,
                            text = null,
                            textString = action.label,
                            textColor = action.tint,
                            iconColor = action.tint ?: Color.Unspecified,
                        ) { hideThen(action.onClick) }
                    }
                    EndOfModalBottomSheet()
                }
            }
        }
    }
    if (showRemoveDownloadDialog) {
        AlertDialog(
            containerColor = colors.container,
            titleContentColor = colors.content,
            textContentColor = colors.content,
            title = { Text(text = stringResource(Res.string.remove_download_title)) },
            text = { Text(text = stringResource(Res.string.remove_download_message)) },
            onDismissRequest = { showRemoveDownloadDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveDownloadDialog = false
                    hideThen(onRemoveDownload ?: {})
                }) {
                    Text(text = stringResource(Res.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDownloadDialog = false }) {
                    Text(text = stringResource(Res.string.cancel))
                }
            },
        )
    }
}
