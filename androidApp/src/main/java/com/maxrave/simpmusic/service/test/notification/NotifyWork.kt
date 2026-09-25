package com.maxrave.simpmusic.service.test.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.maxrave.domain.data.entities.ArtistEntity
import com.maxrave.domain.data.entities.FollowedArtistSingleAndAlbum
import com.maxrave.domain.data.entities.NotificationEntity
import com.maxrave.domain.data.model.searchResult.albums.AlbumsResult
import com.maxrave.domain.extension.now
import com.maxrave.domain.repository.AlbumRepository
import com.maxrave.domain.repository.ArtistRepository
import com.maxrave.domain.repository.CommonRepository
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.extension.symmetricDifference
import com.maxrave.simpmusic.viewModel.MoreAlbumsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class NotifyWork(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val albumRepository: AlbumRepository by inject()
    private val artistRepository: ArtistRepository by inject()

    private val commonRepository: CommonRepository by inject()

    private val mapOfNotification = arrayListOf<NotificationModel>()

    private companion object {
        /** 网易艺人轮询间隔(风控保护,PITFALLS -462) */
        const val NETEASE_POLL_GAP_MS = 500L
    }

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            Logger.w("NotifyWork", "doWork: ")
            val artistList: List<ArtistEntity> = artistRepository.getFollowedArtists().lastOrNull() ?: listOf()
            val listFollowedArtistSingleAndAlbum =
                albumRepository.getAllFollowedArtistSingleAndAlbums().lastOrNull() ?: listOf()
            Logger.w("NotifyWork", "doWork: $artistList")
            Logger.w("NotifyWork", "doWork: $listFollowedArtistSingleAndAlbum")
            artistList.forEach { art ->
                combine(
                    albumRepository.getAlbumMore("MPAD${art.channelId}", MoreAlbumsViewModel.ALBUM_PARAM),
                    albumRepository.getAlbumMore("MPAD${art.channelId}", MoreAlbumsViewModel.SINGLE_PARAM),
                ) { album, single ->
                    Pair(album, single)
                }.first().let { pair ->
                    val albumItem =
                        pair.first
                            ?.second
                    val singleItem =
                        pair.second
                            ?.second
                    val saved =
                        listFollowedArtistSingleAndAlbum.find { it.channelId == art.channelId }
                    val savedAlbum = saved?.album
                    val savedSingle = saved?.single
                    // 重发去重:该艺人历史通知行里出现过的 browseId 一律不再发——
                    // 快照窗口抖动/接口降级让同一张专辑再次进差集时的最后一道闸
                    // (getAlbumMore 对失败与"成功但空"都回 null,两者都跳过差集比较)
                    val notifiedBrowseIds =
                        commonRepository
                            .getNotificationsByChannelId(art.channelId)
                            .flatMap { row -> (row.single + row.album).mapNotNull { it["browseId"] } }
                            .toHashSet()
                    if (!savedAlbum.isNullOrEmpty() && !albumItem.isNullOrEmpty()) {
                        val differentAlbum =
                            albumItem
                                .filter { ytItem ->
                                    (
                                        albumItem.map { item ->
                                            item.browseId
                                        } symmetricDifference (savedAlbum.map { it["browseId"] })
                                    ).contains(ytItem.browseId) && ytItem.browseId !in notifiedBrowseIds
                                }
                        if (differentAlbum.isNotEmpty()) {
                            mapOfNotification.add(
                                NotificationModel(
                                    name = art.name,
                                    channelId = art.channelId,
                                    single = listOf(),
                                    album = differentAlbum,
                                ),
                            )
                        }
                    }
                    if (!savedSingle.isNullOrEmpty() && !singleItem.isNullOrEmpty()) {
                        val differentSingle =
                            singleItem
                                .filter { ytItem ->
                                    (
                                        singleItem.map { item ->
                                            item.browseId
                                        } symmetricDifference (savedSingle.map { it["browseId"] })
                                    ).contains(ytItem.browseId) && ytItem.browseId !in notifiedBrowseIds
                                }
                        if (differentSingle.isNotEmpty()) {
                            mapOfNotification.add(
                                NotificationModel(
                                    name = art.name,
                                    channelId = art.channelId,
                                    single = differentSingle,
                                    album = listOf(),
                                ),
                            )
                        }
                    }
                    // 快照写入:拉取失败(null)的一侧保留旧值,两侧都失败整行跳过。
                    // 曾经无条件覆盖——失败一次快照清空,下次拉全量时"回来"的专辑全算新,
                    // 整批重复通知(网易风控降级窗口最易触发)。
                    if (albumItem != null || singleItem != null) {
                        albumRepository.insertFollowedArtistSingleAndAlbum(
                            FollowedArtistSingleAndAlbum(
                                channelId = art.channelId,
                                name = art.name,
                                single = singleItem?.toMap() ?: savedSingle ?: emptyList(),
                                album = albumItem?.toMap() ?: savedAlbum ?: emptyList(),
                            ),
                        )
                    }
                }
                // 网易歌手(artistId 纯数字)串行拉取间留间隔防风控;YT 侧无此敏感性,不延迟。
                if (art.channelId.toLongOrNull() != null) delay(NETEASE_POLL_GAP_MS)
            }
            Logger.w("NotifyWork", "doWork: $mapOfNotification")
            NotificationHandler.createNotificationChannel(applicationContext)
            mapOfNotification.forEach { noti ->
                if (noti.album.isNotEmpty() || noti.single.isNotEmpty()) {
                    NotificationHandler.createReminderNotification(
                        applicationContext,
                        noti,
                    )
                    commonRepository.insertNotification(
                        NotificationEntity(
                            channelId = noti.channelId,
                            thumbnail = artistList.find { it.channelId == noti.channelId }?.thumbnails,
                            name = noti.name,
                            single = noti.single.toMap(),
                            album = noti.album.toMap(),
                            time = now(),
                        ),
                    )
                }
            }
            Result.success()
        }
}

private fun List<AlbumsResult>?.toMap(): List<Map<String, String>> =
    this?.map { single ->
        mapOf(
            "browseId" to single.browseId,
            "title" to single.title,
            "thumbnails" to (single.thumbnails.lastOrNull()?.url ?: ""),
        )
    } ?: emptyList()

data class NotificationModel(
    val name: String,
    val channelId: String,
    val single: List<AlbumsResult>,
    val album: List<AlbumsResult>,
)