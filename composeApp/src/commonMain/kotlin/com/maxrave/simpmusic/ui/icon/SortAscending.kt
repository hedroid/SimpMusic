package com.maxrave.simpmusic.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 升序排序(上箭头+三条横线)——[SimpIcons.SortDescending] 的方向镜像,两颗一起构成
 * 节目列表的 升序/降序 切换对(网易云官方播客列表同款风格)。
 */
@Suppress("CheckReturnValue")
val SimpIcons.SortAscending: ImageVector
    get() {
        if (_SortAscending != null) {
            return _SortAscending!!
        }
        _SortAscending =
            ImageVector.Builder(
                name = "SortAscending",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    // 箭头杆(左列,垂直向上)
                    moveTo(5.5f, 16.5f)
                    horizontalLineTo(7.5f)
                    verticalLineTo(7f)
                    horizontalLineTo(5.5f)
                    close()
                    // 箭头三角(指向上)
                    moveTo(2.5f, 7.5f)
                    lineTo(6.5f, 3.5f)
                    lineTo(10.5f, 7.5f)
                    lineTo(9.0f, 9.0f)
                    lineTo(6.5f, 6.4f)
                    lineTo(4.0f, 9.0f)
                    close()
                    // 三条横线(右列,与降序版同形)
                    moveTo(12f, 4.5f)
                    horizontalLineTo(21.5f)
                    verticalLineTo(6.5f)
                    horizontalLineTo(12f)
                    close()
                    moveTo(12f, 9.5f)
                    horizontalLineTo(19.5f)
                    verticalLineTo(11.5f)
                    horizontalLineTo(12f)
                    close()
                    moveTo(12f, 14.5f)
                    horizontalLineTo(17.5f)
                    verticalLineTo(16.5f)
                    horizontalLineTo(12f)
                    close()
                }
            }.build()
        return _SortAscending!!
    }

private var _SortAscending: ImageVector? = null
