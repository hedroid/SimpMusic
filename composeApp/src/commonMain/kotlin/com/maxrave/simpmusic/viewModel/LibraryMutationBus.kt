package com.maxrave.simpmusic.viewModel

import com.maxrave.domain.data.entities.PlaylistEntity
import com.maxrave.domain.data.model.searchResult.albums.AlbumsResult
import com.maxrave.domain.data.model.searchResult.artists.ArtistsResult
import com.maxrave.domain.data.model.searchResult.playlists.PlaylistsResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 库页本地回写事件总线(用户 2026-09-22 定案):子页(艺人/歌单/播放页)的写操作成功后
 * 通知库页**原地更新**对应分区——移除条目、增减计数、插入新建/新收藏条目;库页 VM 存活
 * 期间不再依赖返回时的网络刷新(拉全量刷新体验差,已撤)。VM 已销毁(冷启动后首次进库页)
 * 时事件丢失无妨——那时走首拉网络,数据本来就是新的。
 *
 * 2026-09-23 起所有"添加"方向事件(Created/Favorited/Followed)一律携带**完整行**
 * (详情页上下文或建单体回读的权威数据),库页插入的行与下次网络拉回的行同构,
 * 消灭"占位行→网络行"的样式跳变;回读失败时由发送方退占位行。
 */
sealed interface LibraryMutation {
    /** 网易红心歌单曲目数变化(点赞 +1 / 取消点赞 -1,含红心歌单内移除歌曲) */
    data class NeteaseHeartCountChanged(val delta: Int) : LibraryMutation

    /** 歌单已从云端账号移除(网易取消收藏/删除歌单;YT 取消收藏/删除歌单) */
    data class PlaylistRemoved(val playlistId: String) : LibraryMutation

    /** 云端歌单收藏成功(YT like/like 或网易 subscribe t=1),payload=详情页完整实体 */
    data class PlaylistFavorited(val playlist: PlaylistEntity) : LibraryMutation

    /** YT 新建歌单成功,payload=权威行(建单体回读;回读失败退占位行) */
    data class YouTubePlaylistCreated(
        val playlist: PlaylistsResult,
    ) : LibraryMutation

    /** 网易新建歌单成功,payload=权威行(建单体回读;回读失败退占位行) */
    data class NeteasePlaylistCreated(
        val playlist: PlaylistEntity,
    ) : LibraryMutation

    /** 云端专辑收藏成功(YT like album 或网易 album/sub t=1);browseId 数字形状=网易 */
    data class AlbumFavorited(val album: AlbumsResult) : LibraryMutation

    /** 艺人已取消关注(本地关注表已同步写,库页关注分区原地移除) */
    data class ArtistUnfollowed(val artistId: String) : LibraryMutation

    /** 艺人关注成功;browseId 数字形状=网易 */
    data class ArtistFollowed(val artist: ArtistsResult) : LibraryMutation

    /** 网易收藏专辑已移除 */
    data class AlbumUnsubscribed(val albumId: String) : LibraryMutation
}

class LibraryMutationBus {
    private val _mutations = MutableSharedFlow<LibraryMutation>(extraBufferCapacity = 32)
    val mutations: SharedFlow<LibraryMutation> = _mutations.asSharedFlow()

    /**
     * 库页 VM 不在(冷启动后没进过库 tab,从其它入口进了歌单/艺人页操作)时的事件暂存:
     * VM 创建并在首拉完成后 drainPending 补放。VM 在线(subscriptionCount>0)则只走 flow,
     * 不暂存——避免同一次变更被 flow 与 pending 双发。
     */
    private val pending = java.util.concurrent.ConcurrentLinkedQueue<LibraryMutation>()

    fun send(mutation: LibraryMutation) {
        if (_mutations.subscriptionCount.value == 0) {
            pending.add(mutation)
        }
        _mutations.tryEmit(mutation)
    }

    fun drainPending(): List<LibraryMutation> {
        val drained = mutableListOf<LibraryMutation>()
        while (true) {
            drained += pending.poll() ?: break
        }
        return drained
    }
}
