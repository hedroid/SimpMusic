package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maxrave.domain.data.entities.AlbumEntity
import com.maxrave.domain.data.entities.ArtistEntity
import com.maxrave.domain.data.entities.PlaylistEntity
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.data.model.searchResult.artists.ArtistsResult
import com.maxrave.domain.data.type.ChartItem
import com.maxrave.domain.source.MusicSource
import com.maxrave.simpmusic.ui.icon.NeteaseCloudMusic
import com.maxrave.simpmusic.ui.icon.SimpIcons

/** 网易云音乐品牌红(官方视觉主色;音源切换菜单用,角标已改灰白系) */
val NeteaseBrandRed = Color(0xFFC20C0C)

/** YouTube 品牌红(音源切换菜单用,角标已改灰白系) */
val YouTubeBrandRed = Color(0xFFFF0000)

/** 角标圆底:半透明白(灰白观感,叠任何封面都不抢色);0.6——更低在浅色封面上糊掉 */
private val BadgeScrimColor = Color.White.copy(alpha = 0.6f)

/** 角标图标:近纯白浅调——比纯白柔,又比圆底实,保证在磨砂圆上读得清 */
private val BadgeGlyphColor = Color.White.copy(alpha = 0.95f)

/**
 * 混源上下文里的**双品牌来源角标**（网易=音符标 / YT=圆环套播放三角=官方标形状）。
 * 圆底与图标统一灰白系(2026-09-24 用户定案:品牌红太跳)——半透明白圆底 +
 * 高不透明浅白图标,叠任何封面都不抢;只靠图形(音符 vs 环+三角)区分品牌,
 * 颜色不再承担品牌语义(音源切换菜单仍用品牌红,那是点击目标需要辨识度)。
 * 只在 opt-in 上下文渲染(您的库最近添加、收藏/下载网格、排行榜/搜索分类卡/混合页
 * 顶栏/播放页封面);本地实体(LocalPlaylistEntity 等)无角标。
 * 判源:playlist/song 表有独立 source 列(Room v28 已回填);album/artist 表没有,
 * 按 ID 形状判(纯数字=网易,项目惯用法)。
 */
@Composable
fun SourceBadge(
    source: MusicSource,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    val netease = source == MusicSource.NETEASE
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(BadgeScrimColor),
        contentAlignment = Alignment.Center,
    ) {
        if (netease) {
            Icon(
                imageVector = SimpIcons.NeteaseCloudMusic,
                contentDescription = null,
                tint = BadgeGlyphColor,
                modifier = Modifier.size(size * 0.62f),
            )
        } else {
            // YTM 官方标:白圈环 + 环内播放三角(裸三角不成立,用户点名要环)
            Canvas(modifier = Modifier.size(size * 0.68f)) {
                val w = this.size.width
                val stroke = w * 0.15f
                drawCircle(
                    color = BadgeGlyphColor,
                    // 描边骑在半径上,外沿贴 Canvas 边要扣掉半描边
                    radius = (w - stroke) / 2f,
                    style = Stroke(width = stroke),
                )
                val triangle =
                    Path().apply {
                        moveTo(w * 0.39f, w * 0.28f)
                        lineTo(w * 0.74f, w * 0.5f)
                        lineTo(w * 0.39f, w * 0.72f)
                        close()
                    }
                drawPath(triangle, color = BadgeGlyphColor)
            }
        }
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

        // 排行榜 tile 恒为 YT 歌单(ChartItem.ytPlaylistId)
        is ChartItem -> MusicSource.YOUTUBE_MUSIC

        else -> null
    }

/** 歌曲 ID → 音源(纯数字=网易,项目惯用判形);null=无从判断 */
fun trackSource(videoId: String?): MusicSource? =
    when {
        videoId == null -> null
        videoId.toLongOrNull() != null -> MusicSource.NETEASE
        else -> MusicSource.YOUTUBE_MUSIC
    }

/**
 * 播放页封面角标取源:按页曲 ID 形状判(相邻页各自标对源);当前页无页曲时回退
 * [NowPlayingContentState.isNeteaseSong],非当前页无页曲则不标。
 */
fun artworkBadgeSource(
    pageTrackVideoId: String?,
    isCurrentPage: Boolean,
    isNeteaseSong: Boolean,
): MusicSource? =
    trackSource(pageTrackVideoId)
        ?: if (isCurrentPage) {
            if (isNeteaseSong) MusicSource.NETEASE else MusicSource.YOUTUBE_MUSIC
        } else {
            null
        }
