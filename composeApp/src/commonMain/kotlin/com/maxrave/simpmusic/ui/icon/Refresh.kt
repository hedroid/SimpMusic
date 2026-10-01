package com.maxrave.simpmusic.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 双箭头环形刷新(Material Symbols refresh)——下载管理节头"重试失败项"图标版(2026-10-01) */
@Suppress("CheckReturnValue")
val SimpIcons.Refresh: ImageVector
  get() {
    if (_Refresh != null) {
      return _Refresh!!
    }
    _Refresh =
      ImageVector.Builder(
          name = "Refresh",
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
            moveTo(12f, 5f)
            verticalLineToRelative(-3f)
            lineTo(8f, 6f)
            lineToRelative(4f, 4f)
            verticalLineToRelative(-3f)
            curveToRelative(3.31f, 0f, 6f, 2.69f, 6f, 6f)
            curveToRelative(0f, 1.01f, -0.25f, 1.97f, -0.7f, 2.8f)
            lineToRelative(1.46f, 1.46f)
            curveToRelative(0.78f, -1.23f, 1.24f, -2.69f, 1.24f, -4.26f)
            curveToRelative(0f, -4.42f, -3.58f, -8f, -8f, -8f)
            close()
            moveTo(12f, 19f)
            curveToRelative(-3.31f, 0f, -6f, -2.69f, -6f, -6f)
            curveToRelative(0f, -1.01f, 0.25f, -1.97f, 0.7f, -2.8f)
            lineTo(5.24f, 8.74f)
            curveToRelative(-0.78f, 1.23f, -1.24f, 2.69f, -1.24f, 4.26f)
            curveToRelative(0f, 4.42f, 3.58f, 8f, 8f, 8f)
            verticalLineToRelative(3f)
            lineToRelative(4f, -4f)
            lineToRelative(-4f, -4f)
            verticalLineToRelative(3f)
            close()
          }
        }
        .build()
    return _Refresh!!
  }

private var _Refresh: ImageVector? = null
