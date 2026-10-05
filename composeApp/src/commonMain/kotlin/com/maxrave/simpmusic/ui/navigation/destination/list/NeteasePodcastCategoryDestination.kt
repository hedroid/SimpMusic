package com.maxrave.simpmusic.ui.navigation.destination.list

import kotlinx.serialization.Serializable

/** 网易云播客分类电台列表页(播客主页分类 chips 进入,/djradio/hot 分页) */
@Serializable
data class NeteasePodcastCategoryDestination(
    val categoryId: Long,
    val categoryName: String = "",
)
