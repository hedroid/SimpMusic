package com.maxrave.simpmusic.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * "从下载列表移除"专用删除图标(2026-10 用户反馈:垃圾桶太丑,减号又太素)——
 * Material 的 playlist_remove 形状:三条横线 + 右下角一段减号,语义"从列表里拿掉",
 * 不带垃圾桶的"销毁"暗示。
 */
@Suppress("CheckReturnValue")
val SimpIcons.PlaylistRemove: ImageVector
  get() {
    if (_PlaylistRemove != null) {
      return _PlaylistRemove!!
    }
    _PlaylistRemove =
      ImageVector.Builder(
          name = "PlaylistRemove",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        )
        .apply {
          // 圆头横线(半径 1,与 Remove.kt 同款圆角比例)
          fun ImageVector.Builder.line(
            x1: Float,
            x2: Float,
            cy: Float,
          ) = path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            pathFillType = PathFillType.Companion.NonZero,
          ) {
            moveTo(x1 + 1f, cy + 1f)
            quadTo(x1 + 0.42f, cy + 1f, x1 + 0.29f, cy + 0.71f)
            quadTo(x1, cy + 0.43f, x1, cy)
            quadTo(x1, cy - 0.42f, x1 + 0.29f, cy - 0.71f)
            quadTo(x1 + 0.42f, cy - 1f, x1 + 1f, cy - 1f)
            horizontalLineTo(x2 - 1f)
            quadTo(x2 - 0.42f, cy - 1f, x2 - 0.29f, cy - 0.71f)
            quadTo(x2, cy - 0.43f, x2, cy)
            quadTo(x2, cy + 0.42f, x2 - 0.29f, cy + 0.71f)
            quadTo(x2 - 0.42f, cy + 1f, x2 - 1f, cy + 1f)
            close()
          }
          line(2f, 14f, 7f)
          line(2f, 14f, 12f)
          line(2f, 9f, 17f)
          line(15f, 22f, 17f)
        }
        .build()
    return _PlaylistRemove!!
  }

private var _PlaylistRemove: ImageVector? = null
