package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maxrave.domain.data.entities.AlbumEntity
import com.maxrave.domain.data.entities.PlaylistEntity
import com.maxrave.domain.source.MusicSource
import com.maxrave.simpmusic.ui.icon.NeteaseCloudMusic
import com.maxrave.simpmusic.ui.icon.SimpIcons

/**
 * 混源上下文里标记网易云来源的品牌角标:半透明黑圆底 + 白色网易图标,叠在缩略图右上角。
 * 只在明确传入开关的调用点渲染(收藏/下载网格、最近添加行);纯网易分区和 YT-only
 * 用户永不出现——网易实体只有经网易使用才会进本地库,无角标即读作"非网易"。
 */
@Composable
fun NeteaseSourceBadge(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = SimpIcons.NeteaseCloudMusic,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.64f),
        )
    }
}

/**
 * 判源:playlist 表有独立 source 列(Room v28 已回填);album 表没有,按 ID 形状判
 * (纯数字=网易,项目惯用法)。其余类型(YT 远端形状/本地歌单/榜单等)恒 false。
 */
fun isNeteaseContent(data: Any?): Boolean =
    when (data) {
        is PlaylistEntity -> data.source == MusicSource.NETEASE.name
        is AlbumEntity -> data.browseId.toLongOrNull() != null
        else -> false
    }
