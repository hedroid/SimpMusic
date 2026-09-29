package com.maxrave.simpmusic.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 小锁(付费未购语义,播客节目角标用;24dp 网格填 18 居中) */
@Suppress("CheckReturnValue")
val SimpIcons.LockSmall: ImageVector
    get() {
        if (_LockSmall != null) {
            return _LockSmall!!
        }
        _LockSmall =
            ImageVector.Builder(
                name = "LockSmall",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    // 锁体
                    moveTo(6f, 10f)
                    horizontalLineTo(18f)
                    verticalLineTo(20f)
                    horizontalLineTo(6f)
                    close()
                    // 锁梁
                    moveTo(8f, 10f)
                    verticalLineTo(7f)
                    arcTo(4f, 4f, 0f, false, true, 16f, 7f)
                    verticalLineTo(10f)
                    horizontalLineTo(14f)
                    verticalLineTo(7f)
                    arcTo(2f, 2f, 0f, false, false, 10f, 7f)
                    verticalLineTo(10f)
                    close()
                }
            }.build()
        return _LockSmall!!
    }

private var _LockSmall: ImageVector? = null
