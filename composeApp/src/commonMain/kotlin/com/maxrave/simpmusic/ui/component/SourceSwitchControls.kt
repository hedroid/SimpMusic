/*
 * 音源切换的共享交互件:长按手势 + 标准下拉菜单。
 * 扁平导航栏和液态玻璃导航栏的搜索按钮共用同一套逻辑,避免三处复制。
 */
package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.maxrave.domain.source.MusicSource
import com.maxrave.simpmusic.expect.HapticFeedback
import com.maxrave.simpmusic.ui.icon.Check
import com.maxrave.simpmusic.ui.icon.NeteaseCloudMusic
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.icon.YouTubeMusic
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.netease

/**
 * 通用长按:按住超过系统长按时长 → 震动 + [onLongPress];短按 → [onTap]。
 * 超时基于绝对 deadline —— 按住不动(无事件流)也能按时触发;
 * 位移容差 touchSlop*3,超过视为滑动,两个回调都不发。
 */
@Composable
fun Modifier.sourceSwitchGesture(
    onLongPress: () -> Unit,
    onTap: () -> Unit,
): Modifier {
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val start = down.position
            var longPressed = false
            var moved = false
            var lastEventUptime = down.uptimeMillis
            val longPressTimeout = viewConfiguration.longPressTimeoutMillis
            val moveTolerance = viewConfiguration.touchSlop * 3
            while (true) {
                val remaining =
                    if (longPressed) {
                        60_000L
                    } else {
                        (longPressTimeout - (lastEventUptime - down.uptimeMillis)).coerceAtLeast(16L)
                    }
                val event = withTimeoutOrNull(remaining) { awaitPointerEvent() }
                if (event == null) {
                    if (!longPressed && !moved) {
                        longPressed = true
                        // 长按专属即时震感(全局点击观察器按时长排除了长按,这里不受双重振动影响;
                        // 取代原 LocalHapticFeedback.LongPress,受设置"触感反馈"与系统触摸振动开关共同管控)。
                        // 比当前档位高一档的强调震动:长按弹菜单是低频关键动作,要更突出(用户 2026-09-24)
                        HapticFeedback.tapEmphasized()
                        onLongPress()
                    }
                    continue
                }
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                lastEventUptime = change.uptimeMillis
                if (!change.pressed) {
                    if (!longPressed && !moved) {
                        // 点击路径必须消费 UP:全局触感观察器按"UP 被消费=命中交互控件"判震,
                        // 不消费的话这个按钮的点击震感会被当死区丢掉
                        change.consume()
                        onTap()
                    }
                    break
                }
                if (!longPressed && (change.position - start).getDistance() > moveTolerance) {
                    moved = true
                }
            }
        }
    }
}

/** 音源菜单:标准 Material DropdownMenu,锚定在调用方(搜索按钮)上,自动翻到按钮上方弹出 */
@Composable
fun SourceSwitchMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    selectedSource: MusicSource,
    neteaseLoggedIn: Boolean,
    onSourceSelected: (MusicSource) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        // 网易在前(用户 2026-09-20 定序,与库页 chip 顺序一致)
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.netease)) },
            leadingIcon = {
                Icon(
                    SimpIcons.NeteaseCloudMusic,
                    null,
                    tint = com.maxrave.simpmusic.ui.component.NeteaseBrandRed,
                    modifier = Modifier.size(24.dp),
                )
            },
            trailingIcon = {
                if (selectedSource == MusicSource.NETEASE) Icon(SimpIcons.Check, null)
            },
            enabled = neteaseLoggedIn,
            onClick = {
                onSourceSelected(MusicSource.NETEASE)
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text("YouTube Music") },
            leadingIcon = {
                Icon(
                    SimpIcons.YouTubeMusic,
                    null,
                    tint = com.maxrave.simpmusic.ui.component.YouTubeBrandRed,
                    modifier = Modifier.size(24.dp),
                )
            },
            trailingIcon = {
                if (selectedSource == MusicSource.YOUTUBE_MUSIC) Icon(SimpIcons.Check, null)
            },
            onClick = {
                onSourceSelected(MusicSource.YOUTUBE_MUSIC)
                onDismiss()
            },
        )
    }
}
