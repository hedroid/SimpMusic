package com.maxrave.simpmusic.viewModel

import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.mediaservice.handler.DownloadHandler
import java.io.File

/**
 * 下载管理页(2026-10 二期)的行模型:Room 的下载活动行 × DownloadHandler.downloads 实时
 * 状态在 VM 合并而来。音频/视频是两个独立条目,各自解析成 [DownloadEntryStatus]。
 */
enum class DownloadEntryStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    FAILED,
    EXPORTING,
    DONE,
    FILE_MISSING,
}

data class DownloadManagementRow(
    val song: SongEntity,
    val audioStatus: DownloadEntryStatus,
    /** 音频条目的实时 Download(下载中带进度;无条目/终态为 null) */
    val audioLive: DownloadHandler.Download?,
    val videoStatus: DownloadEntryStatus?,
    val videoLive: DownloadHandler.Download?,
    val audioFileBytes: Long?,
    val videoFileBytes: Long?,
)

/** 列表排序:在途最上(下载中>转存中>排队>暂停>失败),丢失次之,完成沉底;同档按时间倒序 */
fun DownloadEntryStatus.sortRank(): Int =
    when (this) {
        DownloadEntryStatus.DOWNLOADING -> 0
        DownloadEntryStatus.EXPORTING -> 1
        DownloadEntryStatus.QUEUED -> 2
        DownloadEntryStatus.PAUSED -> 3
        DownloadEntryStatus.FAILED -> 4
        DownloadEntryStatus.FILE_MISSING -> 5
        DownloadEntryStatus.DONE -> 6
    }

/**
 * 单条目(音频或视频)状态解析。优先级:DownloadManager 实时态 > 文件落地事实 > Room 旧态。
 *
 * @param live DownloadManager 侧条目(可能 null:从未入队/条目已被清/旧代已完成)
 * @param filePath Room 里的文件路径列(音频/视频对应列;旧缓存代为 null)
 * @param roomState song.downloadState(collect 写的 0-3)
 */
fun resolveDownloadEntryStatus(
    live: DownloadHandler.Download?,
    filePath: String?,
    roomState: Int,
): DownloadEntryStatus {
    val fileExists = filePath != null && File(filePath).exists()
    val S = DownloadHandler.State
    if (live != null) {
        return when (live.state) {
            S.STATE_QUEUED -> DownloadEntryStatus.QUEUED
            S.STATE_DOWNLOADING, S.STATE_RESTARTING -> DownloadEntryStatus.DOWNLOADING
            S.STATE_STOPPED ->
                if (live.stopReason != 0) {
                    DownloadEntryStatus.PAUSED
                } else {
                    // 移除过渡态:以文件/Room 事实为准
                    if (fileExists) DownloadEntryStatus.DONE else fallbackWithoutEntry(filePath, roomState)
                }
            S.STATE_FAILED -> DownloadEntryStatus.FAILED
            S.STATE_COMPLETED ->
                // 文件式:COMPLETED≠文件就绪,转存中;旧代(state=3 无路径)或文件已落地=完成
                if (fileExists || roomState == 3) DownloadEntryStatus.DONE else DownloadEntryStatus.EXPORTING
            else -> if (fileExists) DownloadEntryStatus.DONE else fallbackWithoutEntry(filePath, roomState)
        }
    }
    return fallbackWithoutEntry(filePath, roomState)
}

private fun fallbackWithoutEntry(
    filePath: String?,
    roomState: Int,
): DownloadEntryStatus =
    when {
        filePath != null -> DownloadEntryStatus.FILE_MISSING // 路径在文件丢(外部删/转存中断)
        roomState == 3 -> DownloadEntryStatus.DONE // 旧缓存代(SimpleCache,无文件路径)
        else -> DownloadEntryStatus.FILE_MISSING // 在途条目消失(取消/异常)——展示为可重下
    }

/** 文件大小展示格式:1.2 KB / 27.4 MB / 1.5 GB */
fun formatDownloadBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    return String.format("%.1f GB", mb / 1024.0)
}
