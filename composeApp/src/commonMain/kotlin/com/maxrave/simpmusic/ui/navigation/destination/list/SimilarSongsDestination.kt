package com.maxrave.simpmusic.ui.navigation.destination.list

import kotlinx.serialization.Serializable

/** 网易单曲"相似歌曲"分页页(三点菜单入口):simiSong 无排序概念,不进路由 */
@Serializable
data class SimilarSongsDestination(
    val songId: String,
    val songTitle: String = "",
)
