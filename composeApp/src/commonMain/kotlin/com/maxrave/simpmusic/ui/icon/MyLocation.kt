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
val SimpIcons.MyLocation: ImageVector
  get() {
    if (_MyLocation != null) {
      return _MyLocation!!
    }
    _MyLocation =
        ImageVector.Builder(
            name = "MyLocation",
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
                  moveTo(12f, 8f)
                  curveToRelative(-2.21f, 0f, -4f, 1.79f, -4f, 4f)
                  reflectiveCurveToRelative(1.79f, 4f, 4f, 4f)
                  reflectiveCurveToRelative(4f, -1.79f, 4f, -4f)
                  reflectiveCurveToRelative(-1.79f, -4f, -4f, -4f)
                  close()
                  moveToRelative(8.94f, 3f)
                  curveToRelative(-0.46f, -4.17f, -3.77f, -7.48f, -7.94f, -7.94f)
                  verticalLineTo(1f)
                  horizontalLineToRelative(-2f)
                  verticalLineToRelative(2.06f)
                  curveTo(6.83f, 3.52f, 3.52f, 6.83f, 3.06f, 11f)
                  horizontalLineTo(1f)
                  verticalLineToRelative(2f)
                  horizontalLineToRelative(2.06f)
                  curveToRelative(0.46f, 4.17f, 3.77f, 7.48f, 7.94f, 7.94f)
                  verticalLineTo(23f)
                  horizontalLineToRelative(2f)
                  verticalLineToRelative(-2.06f)
                  curveToRelative(4.17f, -0.46f, 7.48f, -3.77f, 7.94f, -7.94f)
                  horizontalLineTo(23f)
                  verticalLineToRelative(-2f)
                  horizontalLineToRelative(-2.06f)
                  close()
                  moveTo(12f, 19f)
                  curveToRelative(-3.87f, 0f, -7f, -3.13f, -7f, -7f)
                  reflectiveCurveToRelative(3.13f, -7f, 7f, -7f)
                  reflectiveCurveToRelative(7f, 3.13f, 7f, 7f)
                  reflectiveCurveToRelative(-3.13f, 7f, -7f, 7f)
                  close()
              }
          }
          .build()
    return _MyLocation!!
  }

private var _MyLocation: ImageVector? = null
