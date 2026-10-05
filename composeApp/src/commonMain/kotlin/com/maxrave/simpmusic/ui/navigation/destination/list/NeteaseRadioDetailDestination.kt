package com.maxrave.simpmusic.ui.navigation.destination.list

import kotlinx.serialization.Serializable

/** 网易云播客电台详情页(播客 chip 页电台卡/订阅列表进入) */
@Serializable
data class NeteaseRadioDetailDestination(
    val radioId: Long,
    val radioName: String = "",
)
