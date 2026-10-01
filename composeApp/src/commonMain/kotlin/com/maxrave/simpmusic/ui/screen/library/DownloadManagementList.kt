package com.maxrave.simpmusic.ui.screen.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.maxrave.simpmusic.ui.component.RippleIconButton
import com.maxrave.simpmusic.ui.icon.Delete
import com.maxrave.simpmusic.ui.icon.DownloadForOffline
import com.maxrave.simpmusic.ui.icon.MoreVert
import com.maxrave.simpmusic.ui.icon.Pause
import com.maxrave.simpmusic.ui.icon.PlayArrow
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.icon.Update
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.DownloadEntryStatus
import com.maxrave.simpmusic.viewModel.DownloadManagementRow
import com.maxrave.simpmusic.viewModel.formatDownloadBytes
import com.maxrave.simpmusic.viewModel.sortRank
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.delete
import simpmusic.composeapp.generated.resources.download_action_cancel_download
import simpmusic.composeapp.generated.resources.download_action_redownload
import simpmusic.composeapp.generated.resources.download_action_resume_all
import simpmusic.composeapp.generated.resources.download_status_failed
import simpmusic.composeapp.generated.resources.download_status_file_missing
import simpmusic.composeapp.generated.resources.download_status_paused
import simpmusic.composeapp.generated.resources.download_status_queued
import simpmusic.composeapp.generated.resources.download_status_saving
import simpmusic.composeapp.generated.resources.download_video_label
import simpmusic.composeapp.generated.resources.downloaded
import simpmusic.composeapp.generated.resources.downloading
import simpmusic.composeapp.generated.resources.more

/**
 * 下载管理页的歌曲条目行(2026-10 二期):封面+标题+状态行(进度/大小/失败/丢失)+主操作按钮
 * (暂停/继续/重试/删除)+溢出菜单(取消/重下/删除)。音频为主体,视频条目存在时以
 * "视频 xxx" 追加在状态行;下载中渲染细进度条。
 */
@Composable
fun DownloadManagementItem(
    row: DownloadManagementRow,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onRedownload: () -> Unit,
    onDelete: () -> Unit,
    onSongMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryRank = minOf(row.audioStatus.sortRank(), row.videoStatus?.sortRank() ?: Int.MAX_VALUE)
    val contentColor = MaterialTheme.colorScheme.onSurface
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant
    val failedColor = MaterialTheme.colorScheme.error
    var menuOpen by remember { mutableStateOf(false) }

    val audioPlaying = row.audioStatus == DownloadEntryStatus.DONE || row.audioStatus == DownloadEntryStatus.FILE_MISSING
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(enabled = audioPlaying) { onPlay() }
                .padding(horizontal = 15.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)),
        ) {
            AsyncImage(
                model = row.song.thumbnails,
                contentDescription = null,
                modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)),
            )
            if (isPlaying) {
                Spacer(
                    modifier =
                        Modifier
                            .size(50.dp)
                            .background(Color.Black.copy(alpha = 0.3f)),
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.song.title,
                style = typo().labelMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entryStatusText(row.audioStatus, row.audioLive, row.audioFileBytes),
                style = typo().bodySmall,
                color =
                    when (primaryRank) {
                        4 -> failedColor
                        else -> subtitleColor
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            row.videoStatus?.let { videoStatus ->
                Text(
                    text =
                        stringResource(Res.string.download_video_label) + " · " +
                            entryStatusText(videoStatus, row.videoLive, row.videoFileBytes),
                    style = typo().bodySmall,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            progressOf(row)?.let { progress ->
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
        Spacer(modifier = Modifier.width(4.dp))
        // 主操作:在途→暂停;暂停→继续;失败→重试;丢失→重下;完成→删除(确认在调用方)
        when (primaryRank) {
            0, 2 -> RippleIconButton(imageVector = SimpIcons.Pause, fillMaxSize = false, tint = contentColor) { onPause() }
            3 -> RippleIconButton(imageVector = SimpIcons.PlayArrow, fillMaxSize = false, tint = contentColor) { onResume() }
            4 -> RippleIconButton(imageVector = SimpIcons.Update, fillMaxSize = false, tint = contentColor) { onRetry() }
            5 -> RippleIconButton(imageVector = SimpIcons.DownloadForOffline, fillMaxSize = false, tint = contentColor) { onRedownload() }
            6 -> RippleIconButton(imageVector = SimpIcons.Delete, fillMaxSize = false, tint = contentColor) { onDelete() }
            else -> Unit // EXPORTING(1):转存中不可操作,只留菜单
        }
        Box {
            RippleIconButton(imageVector = SimpIcons.MoreVert, fillMaxSize = false, tint = contentColor) { menuOpen = true }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.more)) },
                    onClick = {
                        menuOpen = false
                        onSongMenu()
                    },
                )
                if (primaryRank <= 3) {
                    if (primaryRank == 3) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.download_action_resume_all)) },
                            onClick = {
                                menuOpen = false
                                onResume()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.download_action_cancel_download)) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.download_action_redownload)) },
                        onClick = {
                            menuOpen = false
                            onRedownload()
                        },
                    )
                    if (primaryRank == 6) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.delete)) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 下载中的整体进度(0..1):音频优先,音频无百分比再看视频 */
private fun progressOf(row: DownloadManagementRow): Float? {
    (row.audioLive.takeIf { row.audioStatus == DownloadEntryStatus.DOWNLOADING })?.let { live ->
        if (live.percentDownloaded >= 0) return live.percentDownloaded / 100f
    }
    (row.videoLive.takeIf { row.videoStatus == DownloadEntryStatus.DOWNLOADING })?.let { live ->
        if (live.percentDownloaded >= 0) return live.percentDownloaded / 100f
    }
    return null
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
