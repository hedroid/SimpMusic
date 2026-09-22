package com.maxrave.simpmusic.viewModel

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 库页本地回写事件总线(用户 2026-09-22 定案):子页(艺人/歌单/播放页)的写操作成功后
 * 通知库页**原地更新**对应分区——移除条目、增减计数、插入新建歌单;库页 VM 存活期间
 * 不再依赖返回时的网络刷新(拉全量刷新体验差,已撤)。VM 已销毁(冷启动后首次进库页)
 * 时事件丢失无妨——那时走首拉网络,数据本来就是新的。
 */
sealed interface LibraryMutation {
    /** 网易红心歌单曲目数变化(点赞 +1 / 取消点赞 -1,含红心歌单内移除歌曲) */
    data class NeteaseHeartCountChanged(val delta: Int) : LibraryMutation

    /** 歌单已从云端账号移除(网易取消收藏/删除歌单;YT 取消收藏/删除歌单) */
    data class PlaylistRemoved(val playlistId: String) : LibraryMutation

    /** YT 新建歌单成功,插入"创建的歌单"分区 */
    data class YouTubePlaylistCreated(
        val playlistId: String,
        val title: String,
    ) : LibraryMutation

    /** 网易新建歌单成功,插入"创建的歌单"分区 */
    data class NeteasePlaylistCreated(
        val playlistId: String,
        val title: String,
    ) : LibraryMutation

    /** 艺人已取消关注(本地关注表已同步写,库页关注分区原地移除) */
    data class ArtistUnfollowed(val artistId: String) : LibraryMutation

    /** 网易收藏专辑已移除 */
    data class AlbumUnsubscribed(val albumId: String) : LibraryMutation
}

class LibraryMutationBus {
    private val _mutations = MutableSharedFlow<LibraryMutation>(extraBufferCapacity = 32)
    val mutations: SharedFlow<LibraryMutation> = _mutations.asSharedFlow()

    fun send(mutation: LibraryMutation) {
        _mutations.tryEmit(mutation)
    }
}
