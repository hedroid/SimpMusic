package com.maxrave.simpmusic.ui.screen.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.maxrave.simpmusic.extension.angledGradientBackground
import com.maxrave.simpmusic.ui.component.rememberSurfaceDarkColors
import com.maxrave.simpmusic.ui.icon.Add
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.seed
import com.maxrave.simpmusic.ui.theme.typo
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.cancel
import simpmusic.composeapp.generated.resources.create
import simpmusic.composeapp.generated.resources.create_new_playlist
import simpmusic.composeapp.generated.resources.delete
import simpmusic.composeapp.generated.resources.playlist_name

/**
 * 库页两个云端 tab("您的网易云"/"您的 YouTube Music")"创建的歌单"分区的固定入口:
 * 视觉对齐本地歌单网格的既有新建 tile(种子色渐变+白加号),与
 * [com.maxrave.simpmusic.ui.component.HomeItemContentPlaylist](fillWidth=true) 同足迹——
 * 方形占位块 + 下方标题行;点击弹 [CreatePlaylistDialog]。
 */
@Composable
internal fun CreatePlaylistTile(onClick: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClick),
    ) {
        Box(
            modifier =
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
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = SimpIcons.Add,
                contentDescription = stringResource(Res.string.create_new_playlist),
                tint = Color.White,
                modifier = Modifier.size(84.dp),
            )
        }
        Text(
            text = stringResource(Res.string.create_new_playlist),
            style = typo().titleSmall,
            // 与本地歌单新建 tile 同款:文字落在页面背景上,跟主题色而非硬编码白
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
        )
    }
}

/**
 * 新建云端歌单弹窗(库页入口,无初始曲目):点"创建"即乐观关闭,成败 toast 由 VM 提示
 * (成功后 VM 静默刷新,新歌单几秒内出现在分区里)。
 */
@Composable
internal fun CreatePlaylistDialog(
    onCreate: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        containerColor = rememberSurfaceDarkColors().container,
        titleContentColor = rememberSurfaceDarkColors().content,
        textContentColor = rememberSurfaceDarkColors().content,
        title = { Text(text = stringResource(Res.string.create_new_playlist)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(text = stringResource(Res.string.playlist_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotEmpty()) {
                        onDismiss()
                        onCreate(trimmed)
                    }
                },
            ) {
                Text(text = stringResource(Res.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.cancel))
            }
        },
    )
}

/**
 * 库页移除类操作的确认弹窗(取消收藏歌单/专辑、删除歌单共用,原 NeteaseUnsubscribeDialog
 * 提升共用——YT tab 对齐网易逻辑后两 tab 同款)。取消收藏不是删除,按钮文案用
 * [confirmLabel] 传"确认";真删除(自建歌单)走默认"删除"。
 */
@Composable
internal fun LibraryRemoveConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String? = null,
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
                Text(text = confirmLabel ?: stringResource(Res.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.cancel))
            }
        },
    )
}
