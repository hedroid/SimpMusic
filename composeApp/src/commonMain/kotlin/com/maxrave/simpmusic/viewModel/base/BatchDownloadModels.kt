package com.maxrave.simpmusic.viewModel.base

/**
 * 批量下载入口(歌单/专辑/多选)的分类结果与确认弹窗载荷。
 *
 * 交互定稿(2026-09-30):入口含已下载歌曲时弹窗三选——跳过已下载/覆盖已下载/取消;
 * 全部未下载直接入队不弹;在途(排队/下载中)的一律跳过不参与弹窗计数。
 * "已下载"按文件式判定(Room 音频路径+文件存在),旧缓存代(state=3 无路径)算未下载。
 */
data class BatchDownloadRequest(
    /** 未下载(含旧缓存代/文件丢失),确认后直接入队 */
    val notDownloaded: List<BatchDownloadSong>,
    /** 文件式已下载,弹窗决定跳过或覆盖(覆盖=删旧文件后重新入队) */
    val downloaded: List<BatchDownloadSong>,
)

data class BatchDownloadSong(
    val videoId: String,
    val title: String,
    val thumbnail: String,
)
