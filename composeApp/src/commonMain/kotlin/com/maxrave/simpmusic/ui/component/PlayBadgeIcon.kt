package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** 按钮内播放徽标:细描边圆圈包播放/暂停图标(单图标太轻不清,播客详情页/混合页 FM hero 共用) */
@Composable
fun PlayBadgeIcon(icon: ImageVector) {
    Box(
        modifier = Modifier.size(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(20.dp)
                    .border(1.dp, LocalContentColor.current, CircleShape),
        )
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
        )
    }
}
