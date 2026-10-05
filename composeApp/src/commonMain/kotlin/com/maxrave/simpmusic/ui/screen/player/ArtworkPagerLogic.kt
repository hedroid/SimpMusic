package com.maxrave.simpmusic.ui.screen.player

import com.maxrave.domain.data.model.browse.album.Track

/**
 * Pure helper for the artwork pager on [NowPlayingScreen].
 *
 * Extracted to keep queue/player index reconciliation testable without Compose runtime.
 */

/**
 * Maps the currently playing track back to its position in the queue (`listTracks`).
 *
 * - [playerOrderIndex] is `MediaPlayerHandler#currentOrderIndex()`, i.e. the PLAYER's own
 *   position, and it is preferred because it is the only answer that survives a queue holding
 *   the same `videoId` twice. Nothing dedupes the queue — an endless/radio tail, or "Add to
 *   queue" on a track already in it, is enough — and searching by id then returns the LAST copy
 *   while the player sits on an earlier one. Everything downstream inherits that: the Apple
 *   Music queue cuts its "up next" list at `index + 1` and swallows every track in between, a
 *   artwork navigation starts from the wrong slot, and a row's ⋯ opens on a different song.
 * - It is cross-checked against [nowPlayingVideoId] rather than trusted outright: while the
 *   queue is being rebuilt, `listTracks` and the player timeline are briefly out of step, and an
 *   index that points at some other track is worse than the id search. Failing that check falls
 *   back to the search below, so this is never worse than what it replaced.
 * - The search uses `videoId` (already prefix-stripped by
 *   `MediaServiceHandlerImpl#getDataOfNowPlayingState`). Do **not** pass
 *   `nowPlayingState.mediaItem.mediaId` — for video items it carries the
 *   `MERGING_DATA_TYPE.VIDEO` ("Video") prefix and the lookup will silently fail.
 * - Uses `indexOfLast`, mirroring what `MediaServiceHandlerImpl#currentOrderIndex` itself does
 *   in the one case it has no better answer either (shuffle on, where the timeline index belongs
 *   to a different order than `listTracks`).
 * - Coerces to `0` when the track isn't found, so the pager stays on the first slot
 *   instead of throwing during a transient queue/now-playing mismatch.
 */
internal fun deriveOrderIndex(
    queue: List<Track>,
    nowPlayingVideoId: String?,
    playerOrderIndex: Int,
): Int {
    if (queue.isEmpty()) return 0
    if (playerOrderIndex in queue.indices &&
        (nowPlayingVideoId.isNullOrEmpty() || queue[playerOrderIndex].videoId == nowPlayingVideoId)
    ) {
        return playerOrderIndex
    }
    if (nowPlayingVideoId.isNullOrEmpty()) return 0
    return queue
        .indexOfLast { it.videoId == nowPlayingVideoId }
        .coerceAtLeast(0)
}
