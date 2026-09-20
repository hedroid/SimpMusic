package com.maxrave.simpmusic.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp

/**
 * 库页各 tab 歌单网格的统一口径(2026-09-20 定):与主页歌单行一致——
 * 页面水平边距 15dp、卡片间距 4dp(纵向 8dp)、tile 最小宽 160dp(Adaptive,槽内铺满)。
 *
 * 此前各 tab 用 GridCells.FixedSize(132.dp)+Arrangement.SpaceEvenly:页边随列数与
 * 屏宽浮动("左右间距不一致"),与主页固定 15dp 不同口径;统一走这里,别再各自手写。
 */
object LibraryGridDefaults {
    val minTileSize = 160.dp
    val horizontalPadding = 15.dp
    val horizontalSpacing = 4.dp
    val verticalSpacing = 8.dp
    val horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(horizontalSpacing)
    val verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(verticalSpacing)
}
