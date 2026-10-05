package com.maxrave.simpmusic.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
val SimpIcons.Comment: ImageVector
  get() {
    if (_Comment != null) {
      return _Comment!!
    }
    _Comment =
      ImageVector.Builder(
          name = "Comment",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        )
        .apply {
          path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.Companion.NonZero,
          ) {
            // Material Symbols Rounded "forum" 24px:双气泡明确表达公开评论/回复串,
            // 与旧单气泡在播放器的 24dp 尺寸下也有可辨识差异。源 SVG 使用 960 网格,
            // 这里按 1:40 精确换算到 ImageVector 的 24x24 viewport;末尾零面积轮廓丢弃。
            moveTo(7f, 18f)
            quadToRelative(-0.425f, 0f, -0.7125f, -0.2875f)
            reflectiveQuadTo(6f, 17f)
            verticalLineTo(15f)
            horizontalLineTo(19f)
            verticalLineTo(6f)
            horizontalLineTo(21f)
            quadToRelative(0.425f, 0f, 0.7125f, 0.2875f)
            reflectiveQuadTo(22f, 7f)
            verticalLineTo(19.575f)
            quadToRelative(0f, 0.675f, -0.6125f, 0.9375f)
            reflectiveQuadTo(20.3f, 20.3f)
            lineTo(18f, 18f)
            close()
            moveTo(6f, 13f)
            lineTo(3.7f, 15.3f)
            quadToRelative(-0.475f, 0.475f, -1.0875f, 0.2125f)
            reflectiveQuadTo(2f, 14.575f)
            verticalLineTo(3f)
            quadToRelative(0f, -0.425f, 0.2875f, -0.7125f)
            reflectiveQuadTo(3f, 2f)
            horizontalLineTo(16f)
            quadToRelative(0.425f, 0f, 0.7125f, 0.2875f)
            reflectiveQuadTo(17f, 3f)
            verticalLineTo(12f)
            quadToRelative(0f, 0.425f, -0.2875f, 0.7125f)
            reflectiveQuadTo(16f, 13f)
            close()
            moveTo(15f, 11f)
            verticalLineTo(4f)
            horizontalLineTo(4f)
            verticalLineTo(11f)
            close()
          }
        }
        .build()
    return _Comment!!
  }

private var _Comment: ImageVector? = null
