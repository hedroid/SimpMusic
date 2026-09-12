package com.maxrave.simpmusic.ui.navigation.destination.home

import kotlinx.serialization.Serializable

/** 网易标签分类页(网格歌单页),tag 为分类名(华语/摇滚/学习…) */
@Serializable
data class NeteaseTagDestination(
    val tag: String,
)
