package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maxrave.domain.data.entities.AlbumEntity
import com.maxrave.domain.data.entities.ArtistEntity
import com.maxrave.domain.data.entities.PlaylistEntity
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.data.model.searchResult.artists.ArtistsResult
import com.maxrave.domain.source.MusicSource
import com.maxrave.simpmusic.ui.icon.NeteaseCloudMusic
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.icon.PlayArrow

/**
 * 混源上下文里的**双品牌来源角标**（网易=音符标 / YT=播放标），半透明黑圆底 + 白色品牌
 * 图标叠缩略图右上角。只在混源上下文 opt-in 渲染（您的库最近添加、收藏/下载网格等）；
 * 单源页面（您的网易云、YouTube 歌单、排行榜、播客）与本地自建歌单封面不传。
 * 判源：playlist/song 表有独立 source 列（Room v28 已回填）；album/artist 表没有，
 * 按 ID 形状判（纯数字=网易，项目惯用法）；本地实体（LocalPlaylistEntity 等）无角标。
 */
@Composable
fun SourceBadge(
    source: MusicSource,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    // 网易=黑底+白色音符(实心,小尺寸可读);YT=YouTube红底+白色实心播放三角。
    // (曾用 YT Music 圆环标:细圆环在 16dp 角标里线宽不足 2px,视觉上"没有角标"。)
    val netease = source == MusicSource.NETEASE
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    if (netease) Color.Black.copy(alpha = 0.55f) else Color(0xE6FF0000),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector =
                if (netease) {
                    SimpIcons.NeteaseCloudMusic
                } else {
                    SimpIcons.PlayArrow
                },
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * (if (netease) 0.64f else 0.52f)),
        )
    }
}

/** 条目 → 音源;null = 不加角标(本地实体/榜单/月度回顾等无音源语义的类型) */
fun contentSource(data: Any?): MusicSource? =
    when (data) {
        is PlaylistEntity ->
            when (data.source) {
                MusicSource.NETEASE.name -> MusicSource.NETEASE
                else -> MusicSource.YOUTUBE_MUSIC
            }

        is SongEntity ->
            when (data.source) {
                MusicSource.NETEASE.name -> MusicSource.NETEASE
                else -> MusicSource.YOUTUBE_MUSIC
            }

        is AlbumEntity ->
            if (data.browseId.toLongOrNull() != null) MusicSource.NETEASE else MusicSource.YOUTUBE_MUSIC

        is ArtistEntity ->
            if (data.channelId.toLongOrNull() != null) MusicSource.NETEASE else MusicSource.YOUTUBE_MUSIC

        is ArtistsResult ->
            if (data.browseId.toLongOrNull() != null) MusicSource.NETEASE else MusicSource.YOUTUBE_MUSIC

        else -> null
    }
