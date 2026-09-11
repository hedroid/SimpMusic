package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.maxrave.domain.source.MusicSource
import kotlin.math.cos
import kotlin.math.sin

/**
 * 数据源扇形切换菜单(feat/netease-source)。
 *
 * 以搜索按钮圆心 [center] 为轴心,向左上展开 90° 扇形,YouTube Music / 网易云两个图标
 * 分别落在 118° / 162°(数学角,逆时针,向上为 90°)。悬停高亮由 [hover] 驱动;绘制发生在
 * 父容器 bounds 之外(向上),依赖 Compose 默认不裁剪;指针手势由搜索按钮的
 * pointerInput 全程接管(按下后的移动事件持续派发给初始节点,不受 bounds 限制)。
 */
private const val FAN_RADIUS_DP = 132f
private const val ICON_RADIUS_DP = 96f
private const val ANGLE_YT = 118.0
private const val ANGLE_NE = 162.0

@Composable
fun SourceFanOverlay(
    center: Offset,
    selected: MusicSource,
    neteaseLoggedIn: Boolean,
    hover: MusicSource?,
    modifier: Modifier = Modifier,
) {
    val sectorColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f)
    val highlightColor = MaterialTheme.colorScheme.primary

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .semantics { contentDescription = "data source switcher" },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            fun polar(
                angleDeg: Double,
                radiusPx: Float,
            ): Offset =
                Offset(
                    x = center.x + (radiusPx * cos(Math.toRadians(angleDeg))).toFloat(),
                    y = center.y - (radiusPx * sin(Math.toRadians(angleDeg))).toFloat(),
                )

            val radiusPx = FAN_RADIUS_DP.dp.toPx()
            // 数学角 90°~180°(左上象限)= canvas arc 180°~270°
            val sector =
                Path().apply {
                    moveTo(center.x, center.y)
                    arcTo(
                        rect = Rect(center = center, radius = radiusPx),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false,
                    )
                    close()
                }
            drawPath(sector, color = sectorColor)
            drawPath(sector, color = highlightColor.copy(alpha = 0.25f), style = Stroke(width = 1.5.dp.toPx()))

            // ------------------------------------------------ YouTube Music:红底 + 白色播放三角
            val ytCenter = polar(ANGLE_YT, ICON_RADIUS_DP.dp.toPx())
            drawSourceCircle(
                center = ytCenter,
                hovered = hover == MusicSource.YOUTUBE_MUSIC,
                selected = selected == MusicSource.YOUTUBE_MUSIC,
                enabled = true,
                highlightColor = highlightColor,
            )
            val t = 11.dp.toPx() // 三角半径
            drawPath(
                path =
                    Path().apply {
                        moveTo(ytCenter.x - t * 0.55f, ytCenter.y - t)
                        lineTo(ytCenter.x + t, ytCenter.y)
                        lineTo(ytCenter.x - t * 0.55f, ytCenter.y + t)
                        close()
                    },
                color = Color.White,
                style = Fill,
            )

            // ------------------------------------------------ 网易云:红底 + 白色音符
            val neCenter = polar(ANGLE_NE, ICON_RADIUS_DP.dp.toPx())
            drawSourceCircle(
                center = neCenter,
                hovered = hover == MusicSource.NETEASE,
                selected = selected == MusicSource.NETEASE,
                enabled = neteaseLoggedIn,
                highlightColor = highlightColor,
            )
            val noteHead = 7.dp.toPx()
            val stemTop = neCenter.y - 13.dp.toPx()
            drawCircle(color = Color.White, radius = noteHead, center = Offset(neCenter.x + 4.dp.toPx(), neCenter.y + 6.dp.toPx()))
            drawPath(
                path =
                    Path().apply {
                        moveTo(neCenter.x + 4.dp.toPx() + noteHead, neCenter.y + 6.dp.toPx())
                        lineTo(neCenter.x + 4.dp.toPx() + noteHead, stemTop)
                        lineTo(neCenter.x + 4.dp.toPx() + noteHead + 8.dp.toPx(), stemTop + 4.dp.toPx())
                    },
                color = Color.White,
                style = Stroke(width = 2.5.dp.toPx()),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSourceCircle(
    center: Offset,
    hovered: Boolean,
    selected: Boolean,
    enabled: Boolean,
    highlightColor: Color,
) {
    val r = 30.dp.toPx()
    drawCircle(
        color =
            if (hovered && enabled) {
                highlightColor
            } else {
                Color(0xFFE53935).copy(alpha = if (enabled) 1f else 0.45f)
            },
        radius = r,
        center = center,
    )
    if (selected) {
        drawCircle(
            color = highlightColor,
            radius = r + 4.dp.toPx(),
            center = center,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}
