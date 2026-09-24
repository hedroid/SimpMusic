package com.maxrave.simpmusic.ui.navigation.destination.list

import kotlinx.serialization.Serializable

/** 网易艺人"全部歌曲"分页页(人气区"更多"):order 由页内 chips 切换,不进路由 */
@Serializable
data class MoreSongsDestination(
    val artistId: String,
    val artistName: String = "",
)
