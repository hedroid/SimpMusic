package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.maxrave.simpmusic.ui.theme.typo
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.batch_download_confirm_message
import simpmusic.composeapp.generated.resources.batch_download_confirm_title
import simpmusic.composeapp.generated.resources.batch_download_overwrite
import simpmusic.composeapp.generated.resources.batch_download_skip
import simpmusic.composeapp.generated.resources.cancel

/**
 * 批量下载入口(歌单/专辑/多选)的"跳过/覆盖/取消"三选弹窗(2026-09-30 定稿):
 * 入口含文件式已下载歌曲时弹出——跳过=只下载未下载的;覆盖=删旧文件后全部重下;取消=不动。
 * 取消与跳过并排放在 dismiss 槽(M3 AlertDialog 只有两槽),覆盖为 confirm。
 */
@Composable
fun BatchDownloadConfirmDialog(
    downloadedCount: Int,
    onSkip: () -> Unit,
    onOverwrite: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        containerColor = rememberSurfaceDarkColors().container,
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onOverwrite) {
                Text(text = stringResource(Res.string.batch_download_overwrite), style = typo().labelSmall)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(Res.string.cancel), style = typo().labelSmall)
                }
                TextButton(onClick = onSkip) {
                    Text(text = stringResource(Res.string.batch_download_skip), style = typo().labelSmall)
                }
            }
        },
        title = {
            Text(
                text = stringResource(Res.string.batch_download_confirm_title, downloadedCount),
                style = typo().labelSmall,
            )
        },
        text = {
            Text(text = stringResource(Res.string.batch_download_confirm_message), style = typo().bodyMedium)
        },
    )
}
