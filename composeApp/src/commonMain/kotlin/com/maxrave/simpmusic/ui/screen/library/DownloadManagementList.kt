package com.maxrave.simpmusic.ui.screen.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.maxrave.simpmusic.ui.component.AudioPlayingIndicator
import com.maxrave.simpmusic.ui.component.RippleIconButton
import com.maxrave.simpmusic.ui.component.rememberActualPlaying
import com.maxrave.simpmusic.ui.component.rememberHolderPainter
import com.maxrave.simpmusic.ui.icon.Check
import com.maxrave.simpmusic.ui.icon.DownloadForOffline
import com.maxrave.simpmusic.ui.icon.MoreVert
import com.maxrave.simpmusic.ui.icon.Pause
import com.maxrave.simpmusic.ui.icon.PlayArrow
import com.maxrave.simpmusic.ui.icon.PlaylistRemove
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.icon.Update
import com.maxrave.simpmusic.ui.theme.seed
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.DownloadEntryStatus
import com.maxrave.simpmusic.viewModel.DownloadManagementRow
import com.maxrave.simpmusic.viewModel.formatDownloadBytes
import com.maxrave.simpmusic.viewModel.sortRank
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.download_status_failed
import simpmusic.composeapp.generated.resources.download_status_file_missing
import simpmusic.composeapp.generated.resources.download_status_paused
import simpmusic.composeapp.generated.resources.download_status_queued
import simpmusic.composeapp.generated.resources.download_status_saving
import simpmusic.composeapp.generated.resources.downloaded
import simpmusic.composeapp.generated.resources.downloading

/** 行主视角:歌曲 tab 看音频条目,视频 tab 看视频条目(状态行/进度/主操作全切) */
enum class DownloadViewMode { AUDIO, VIDEO }

/**
 * 下载管理页的条目行(2026-10 二期+用户反馈改版):封面+标题+状态行(进度/大小/失败/
 * 丢失)+主操作按钮(暂停/继续/重试/重下/删除[playlist_remove])+三点直接弹歌曲操作
 * sheet。多选模式:长按进入,行首 Checkbox,点击切换选择。视频 tab 传 [DownloadViewMode.VIDEO]
 * (播放走 mp4 兜底链路,删除走引擎按列清理)。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadManagementItem(
    row: DownloadManagementRow,
    isPlaying: Boolean,
    viewMode: DownloadViewMode = DownloadViewMode.AUDIO,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: () -> Unit = {},
    onSelectToggle: () -> Unit = {},
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onRedownload: () -> Unit,
    onDelete: () -> Unit,
    onSongMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isVideoView = viewMode == DownloadViewMode.VIDEO
    val primaryRank =
        if (isVideoView) {
            row.videoStatus?.sortRank() ?: Int.MAX_VALUE
        } else {
            minOf(row.audioStatus.sortRank(), row.videoStatus?.sortRank() ?: Int.MAX_VALUE)
        }
    val primaryStatus = if (isVideoView) row.videoStatus else row.audioStatus
    val primaryLive = if (isVideoView) row.videoLive else row.audioLive
    val primaryBytes = if (isVideoView) row.videoFileBytes else row.audioFileBytes
    val contentColor = MaterialTheme.colorScheme.onSurface
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant
    val failedColor = MaterialTheme.colorScheme.error
    val playable =
        if (isVideoView) {
            row.videoStatus == DownloadEntryStatus.DONE || row.videoStatus == DownloadEntryStatus.FILE_MISSING
        } else {
            row.audioStatus == DownloadEntryStatus.DONE || row.audioStatus == DownloadEntryStatus.FILE_MISSING
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(
                    // enabled 门不能放这里:它会同时禁掉长按,在途/失败行就进不了多选
                    // (批量删除在途行=取消任务,语义健全)——不可播行的限制只作用于播放
                    onClick = {
                        if (selectionMode) onSelectToggle() else if (playable) onPlay()
                    },
                    onLongClick = onLongClick,
                )
                .background(if (isSelected) seed.copy(alpha = 0.18f) else Color.Transparent)
                .padding(horizontal = 15.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(8.dp))
        AnimatedVisibility(
            visible = selectionMode,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) seed else Color.Transparent)
                            .border(
                                width = 1.5.dp,
                                color = if (isSelected) seed else contentColor.copy(alpha = 0.6f),
                                shape = CircleShape,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = SimpIcons.Check,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
        }
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            // 与 SongFullWidthItems 同款两态:播放中=均衡器(暂停时冻结),否则封面
            val actuallyPlaying = rememberActualPlaying()
            Crossfade(isPlaying) {
                if (it) {
                    Crossfade(actuallyPlaying, label = "dlRowPlayingAnim") { playingNow ->
                        if (playingNow) {
                            AudioPlayingIndicator(modifier = Modifier.fillMaxSize())
                        } else {
                            AudioPlayingIndicator(
                                modifier = Modifier.fillMaxSize(),
                                paused = true,
                            )
                        }
                    }
                } else {
                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(LocalPlatformContext.current)
                                .data(row.song.thumbnails)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .diskCacheKey(row.song.thumbnails)
                                .crossfade(true)
                                .build(),
                        placeholder = rememberHolderPainter(),
                        error = rememberHolderPainter(),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(4.dp)),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
        ) {
            Text(
                text = row.song.title,
                style = typo().titleSmall,
                color = contentColor,
                maxLines = 1,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            animationMode = MarqueeAnimationMode.Immediately,
                        ).focusable(),
            )
            Text(
                text = entryStatusText(primaryStatus ?: DownloadEntryStatus.FILE_MISSING, primaryLive, primaryBytes),
                style = typo().bodySmall,
                color = if (primaryRank == 4) failedColor else subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            progressOf(row, viewMode)?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(50)),
                )
            }
        }
        if (!selectionMode) {
            Spacer(modifier = Modifier.width(4.dp))
            // 主操作:在途→暂停;暂停→继续;失败→重试;丢失→重下;完成→删除(playlist_remove)
            when (primaryRank) {
                0, 2 -> RippleIconButton(imageVector = SimpIcons.Pause, fillMaxSize = false, tint = contentColor) { onPause() }
                3 -> RippleIconButton(imageVector = SimpIcons.PlayArrow, fillMaxSize = false, tint = contentColor) { onResume() }
                4 -> RippleIconButton(imageVector = SimpIcons.Update, fillMaxSize = false, tint = contentColor) { onRetry() }
                5 -> RippleIconButton(imageVector = SimpIcons.DownloadForOffline, fillMaxSize = false, tint = contentColor) { onRedownload() }
                6 -> RippleIconButton(imageVector = SimpIcons.PlaylistRemove, fillMaxSize = false, tint = contentColor) { onDelete() }
                else -> Unit // EXPORTING(1):转存中不可操作
            }
            // 三点直接弹歌曲操作 sheet(用户反馈:不要下拉菜单,弹框里下载/删除都有)
            RippleIconButton(imageVector = SimpIcons.MoreVert, fillMaxSize = false, tint = contentColor) { onSongMenu() }
        }
    }
}

/** 下载中的进度(0..1):音频视角看音频条目,视频视角只看视频;音频视角下音频无百分比再看视频 */
private fun progressOf(
    row: DownloadManagementRow,
    viewMode: DownloadViewMode,
): Float? {
    val pick: (com.maxrave.domain.mediaservice.handler.DownloadHandler.Download?, DownloadEntryStatus?) -> Float? = { live, status ->
        if (status == DownloadEntryStatus.DOWNLOADING && live != null && live.percentDownloaded >= 0) live.percentDownloaded / 100f else null
    }
    if (viewMode == DownloadViewMode.VIDEO) return pick(row.videoLive, row.videoStatus)
    return pick(row.audioLive, row.audioStatus) ?: pick(row.videoLive, row.videoStatus)
}

@Composable
private fun entryStatusText(
    status: DownloadEntryStatus,
    live: com.maxrave.domain.mediaservice.handler.DownloadHandler.Download?,
    fileBytes: Long?,
): String =
    when (status) {
        DownloadEntryStatus.QUEUED -> stringResource(Res.string.download_status_queued)
        DownloadEntryStatus.DOWNLOADING ->
            when {
                live != null && live.percentDownloaded >= 0 -> "${live.percentDownloaded}%"
                live != null && live.bytesDownloaded > 0 -> stringResource(Res.string.downloading) + " · " + formatDownloadBytes(live.bytesDownloaded)
                else -> stringResource(Res.string.downloading)
            }
        DownloadEntryStatus.PAUSED -> stringResource(Res.string.download_status_paused)
        DownloadEntryStatus.FAILED -> stringResource(Res.string.download_status_failed)
        DownloadEntryStatus.EXPORTING -> stringResource(Res.string.download_status_saving)
        DownloadEntryStatus.DONE -> fileBytes?.let { formatDownloadBytes(it) } ?: stringResource(Res.string.downloaded)
        DownloadEntryStatus.FILE_MISSING -> stringResource(Res.string.download_status_file_missing)
    }
