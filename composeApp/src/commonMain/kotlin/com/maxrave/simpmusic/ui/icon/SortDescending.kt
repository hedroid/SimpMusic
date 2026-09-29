package com.maxrave.simpmusic.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 降序排序(下箭头+三条递减横线,网易云官方播客列表排序按钮同款;比 Material 的 Sort
 * 多一个方向箭头,语义更直白)。24dp 网格,fill 风格与 SimpIcons 其余成员一致。
 */
@Suppress("CheckReturnValue")
val SimpIcons.SortDescending: ImageVector
    get() {
        if (_SortDescending != null) {
            return _SortDescending!!
        }
        _SortDescending =
            ImageVector.Builder(
                name = "SortDescending",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    // 箭头杆(左列,垂直向下)
                    moveTo(5.5f, 3.5f)
                    horizontalLineTo(7.5f)
                    verticalLineTo(13f)
                    horizontalLineTo(5.5f)
                    close()
                    // 箭头三角(指向下)
                    moveTo(2.5f, 12.5f)
                    lineTo(6.5f, 16.5f)
                    lineTo(10.5f, 12.5f)
                    lineTo(9.0f, 11.0f)
                    lineTo(6.5f, 13.6f)
                    lineTo(4.0f, 11.0f)
                    close()
                    // 三条横线,自上而下递减(右列)
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
        return _SortDescending!!
    }

private var _SortDescending: ImageVector? = null
