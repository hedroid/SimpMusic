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
val SimpIcons.Visibility: ImageVector
  get() {
    if (_Visibility != null) {
      return _Visibility!!
    }
    _Visibility =
      ImageVector.Builder(
          name = "Visibility",
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
            pathFillType = PathFillType.Companion.EvenOdd,
          ) {
            // Eye outline ring (outer lens minus inner lens), then the pupil.
            moveTo(1f, 12f)
            curveTo(2.73f, 7.61f, 7f, 4.5f, 12f, 4.5f)
            curveTo(17f, 4.5f, 21.27f, 7.61f, 23f, 12f)
            curveTo(21.27f, 16.39f, 17f, 19.5f, 12f, 19.5f)
            curveTo(7f, 19.5f, 2.73f, 16.39f, 1f, 12f)
            close()
            moveTo(3.1f, 12f)
            curveTo(4.7f, 8.45f, 8.1f, 6.5f, 12f, 6.5f)
            curveTo(15.9f, 6.5f, 19.3f, 8.45f, 20.9f, 12f)
            curveTo(19.3f, 15.55f, 15.9f, 17.5f, 12f, 17.5f)
            curveTo(8.1f, 17.5f, 4.7f, 15.55f, 3.1f, 12f)
            close()
            moveTo(9f, 12f)
            arcTo(3f, 3f, 0f, true, false, 15f, 12f)
            arcTo(3f, 3f, 0f, true, false, 9f, 12f)
            close()
          }
        }
        .build()
    return _Visibility!!
  }

private var _Visibility: ImageVector? = null
