@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.maxrave.simpmusic.ui.screen.player.content.applemusic

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maxrave.common.NETEASE_FM_PLAYLIST_ID
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.mediaservice.handler.RepeatState
import com.maxrave.simpmusic.expect.ui.DeviceVolumeController
import com.maxrave.simpmusic.ui.component.DraggableItem
import com.maxrave.simpmusic.ui.component.QueueItemBottomSheet
import com.maxrave.simpmusic.ui.component.SongFullWidthItems
import com.maxrave.simpmusic.ui.component.rememberDragDropState
import com.maxrave.simpmusic.ui.icon.Info
import com.maxrave.simpmusic.ui.icon.MyLocation
import com.maxrave.simpmusic.ui.icon.PlaylistAdd
import com.maxrave.simpmusic.ui.icon.Repeat
import com.maxrave.simpmusic.ui.icon.RepeatOne
import com.maxrave.simpmusic.ui.icon.Shuffle
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.screen.player.content.NowPlayingContentActions
import com.maxrave.simpmusic.ui.screen.player.content.NowPlayingContentState
import com.maxrave.simpmusic.viewModel.UIEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import multiplatform.network.cmptoast.ToastGravity
import multiplatform.network.cmptoast.showToast
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.continue_playing
import simpmusic.composeapp.generated.resources.endless_queue
import simpmusic.composeapp.generated.resources.endless_queue_fm_locked
import simpmusic.composeapp.generated.resources.now_playing

/**
 * The QUEUE body: compact header, [Info][PlaylistAdd][Shuffle][Repeat] pills, a "Continue
 * Playing" section (with the endless-queue switch) and the upcoming tracks — the OLD queue
 * sheet's own [SongFullWidthItems] rows with its long-press-drag reorder and its per-item ⋯
 * sheet ([QueueItemBottomSheet]: move up/down/delete), matching
 * [com.maxrave.simpmusic.ui.component.QueueBottomSheet] exactly.
 * Ends in the same fixed bottom cluster as MAIN/LYRICS.
 */
@Composable
internal fun AppleMusicQueueView(
    state: NowPlayingContentState,
    actions: NowPlayingContentActions,
    typography: AppleMusicTypography,
    viewState: AppleMusicView,
    onSelectView: (AppleMusicView) -> Unit,
    activePillContainer: Color,
    activePillContent: Color,
    deviceVolumeController: DeviceVolumeController?,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    dataStoreManager: DataStoreManager = koinInject(),
    musicServiceHandler: MediaPlayerHandler = koinInject(),
) {
    val localDensity = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // The FULL queue, QueueBottomSheet semantics: played tracks stay ABOVE the current one, so
    // the locate button can land ON the current song and you can still scroll up past it. This
    // view used to drop the played prefix (Apple Music's own "Playing Next" shape), but then the
    // current track was row 0 with nothing above it — locating pinned the list at its top edge
    // with no way to scroll up, which reads as stuck.
    //
    // Rows live in the SAME index space as musicServiceHandler.swap(from, to) and
    // removeMediaItem(index): artworkQueue == queueData.data.listTracks, and both operations
    // read/write that list (and the player timeline) at the SAME position — confirmed by reading
    // MediaServiceHandlerImpl.removeMediaItem/swap and ExoPlayerAdapter.moveMediaItem/
    // removeMediaItem/getUnshuffledIndex, which all treat their index argument as "current
    // shuffle/display order", i.e. exactly artworkQueue's own order. With the whole list shown,
    // local index == absolute index — no offset arithmetic to get wrong anywhere.

    Column(modifier = modifier.fillMaxSize()) {
        // statusBars + 20dp, not a bare status-bar offset: the grabber that
        // NowPlayingContentAppleMusic draws above the view Crossfade floats over this column, and
        // without the extra room the header's title slides underneath it.
        Spacer(
            modifier =
                Modifier.height(
                    with(localDensity) { WindowInsets.statusBars.getTop(localDensity).toDp() } +
                        if (isCompact) 8.dp else 20.dp,
                ),
        )
        AppleMusicCompactHeader(state = state, actions = actions, typography = typography, compact = isCompact)
        AppleMusicQueuePillsRow(
            state = state,
            actions = actions,
            activePillContainer = activePillContainer,
            activePillContent = activePillContent,
            modifier = Modifier.padding(top = 4.dp, bottom = if (isCompact) 8.dp else 20.dp),
        )
        val queueDataState by musicServiceHandler.queueData.collectAsStateWithLifecycle()
        // 到尾触发协程的"重武装"键:开关翻转(尤其关→开)要重启边沿判定,否则弹窗打开时
        // (开关还是关的)近尾边沿已白白消费一次,之后布尔恒 true 不再有新边沿(同 QueueBottomSheet)
        val endlessQueueEnabledForRearm by remember(dataStoreManager) {
            dataStoreManager.endlessQueue.map { it == DataStoreManager.TRUE }
        }.collectAsStateWithLifecycle(initialValue = false)
        // Compact (landscape side panel) drops the "Continue Playing" section header: between the
        // compact header, the pills and the transport-only cluster there is no room for it, and
        // its endless toggle stays reachable in portrait and in the queue bottom sheet.
        if (!isCompact) {
            AppleMusicContinuePlayingHeader(
                state = state,
                dataStoreManager = dataStoreManager,
                typography = typography,
                activePillContainer = activePillContainer,
                activePillContent = activePillContent,
                // 网易私人FM队列：语义即无限电台（loadMore 凭哨兵放行，与开关无关），开关锁定为开。
                isFmQueue = queueDataState?.data?.playlistId == NETEASE_FM_PLAYLIST_ID,
                onEndlessDisabled = { musicServiceHandler.restoreOriginalQueueAfterEndless() },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        // Start already anchored on the current track (no top-of-list flash on entry), then
        // follow track changes: the scroll offset does not move on its own, so a queue the user
        // had scrolled through stays parked mid-list with the current track off-screen.
        val lazyListState =
            rememberLazyListState(initialFirstVisibleItemIndex = state.currentOrderIndex.coerceAtLeast(0))
        val dragDropState =
            rememberDragDropState(lazyListState) { from, to ->
                actions.onMoveQueueItem(from, to)
            }

        LaunchedEffect(state.currentOrderIndex) {
            if (state.currentOrderIndex >= 0) {
                lazyListState.animateScrollToItem(state.currentOrderIndex)
            }
        }

        // Endless/radio queues page in as you scroll — same trigger QueueBottomSheet uses.
        val loadMoreState by remember {
            derivedStateOf { musicServiceHandler.queueData.value?.queueState ?: QueueData.StateSource.STATE_CREATED }
        }
        val shouldLoadMore by remember {
            derivedStateOf {
                val layoutInfo = lazyListState.layoutInfo
                // 布局未就绪/滚动中的瞬时空列表不算"到底"(曾返回 true,快速甩动会伪触发
                // loadMore 把中段歌单提前转成无尽电台)
                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
                lastVisibleItem.index >= layoutInfo.totalItemsCount - 3 && layoutInfo.totalItemsCount > 0
            }
        }
        // 一次到尾只拉一批:布尔边沿触发,INITIALIZING 窗口里的边沿等就绪补射一次
        // (pending);不按落批链式追加(曾一次连灌几十首、指示器连转多圈)。
        // 手势兜底:批次太小(≤2 首)时布尔无边沿可用,手势停止后复核一次;
        // drop(1) 跳过初始未滚动发射,两路共享 2s 抑制窗防同手势双批。同 QueueBottomSheet。
        var lastLoadMoreAt by remember { mutableStateOf(0L) }
        LaunchedEffect(endlessQueueEnabledForRearm) {
            var prevMore = false
            var pending = false
            snapshotFlow { shouldLoadMore to loadMoreState }
                .collect { (more, state) ->
                    when {
                        more && !prevMore -> {
                            if (state == QueueData.StateSource.STATE_INITIALIZED) {
                                lastLoadMoreAt = System.currentTimeMillis()
                                musicServiceHandler.loadMore()
                            } else {
                                pending = true
                            }
                        }

                        more && pending && state == QueueData.StateSource.STATE_INITIALIZED -> {
                            pending = false
                            lastLoadMoreAt = System.currentTimeMillis()
                            musicServiceHandler.loadMore()
                        }
                    }
                    if (!more) pending = false
                    prevMore = more
                }
        }
        LaunchedEffect(Unit) {
            snapshotFlow { lazyListState.isScrollInProgress }
                .distinctUntilChanged()
                .drop(1)
                .collectLatest { scrolling ->
                    if (!scrolling) {
                        delay(250)
                        if (!lazyListState.isScrollInProgress &&
                            shouldLoadMore &&
                            loadMoreState == QueueData.StateSource.STATE_INITIALIZED &&
                            System.currentTimeMillis() - lastLoadMoreAt > 2_000
                        ) {
                            lastLoadMoreAt = System.currentTimeMillis()
                            musicServiceHandler.loadMore()
                        }
                    }
                }
        }
        var overscrollJob by remember { mutableStateOf<Job?>(null) }

        // Same per-item sheet the old queue sheet opens from a row's ⋯ (move up/down/delete).
        var queueItemSheetIndex by remember { mutableStateOf(-1) }
        if (queueItemSheetIndex >= 0) {
            QueueItemBottomSheet(
                onDismiss = { queueItemSheetIndex = -1 },
                index = queueItemSheetIndex,
            )
        }

        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .appleMusicVerticalFadeEdges(topFade = QUEUE_TOP_FADE, bottomFade = QUEUE_BOTTOM_FADE),
        ) {
            LazyColumn(
                state = lazyListState,
                // Space at BOTH ends equal to the fade at that end, so each fade lands on blank
                // space instead of dissolving a real row. Only the bottom had it, which is why the
                // first upcoming track — the one you most want to read — came up half faded out.
                contentPadding = PaddingValues(top = QUEUE_TOP_FADE, bottom = QUEUE_BOTTOM_FADE),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDrag = { change, dragOffset ->
                                    change.consume()
                                    dragDropState.onDrag(offset = dragOffset)
                                    if (overscrollJob?.isActive == true) {
                                        return@detectDragGesturesAfterLongPress
                                    }
                                    dragDropState
                                        .checkForOverScroll()
                                        .takeIf { it != 0f }
                                        ?.let { delta ->
                                            overscrollJob =
                                                coroutineScope.launch {
                                                    dragDropState.state.animateScrollBy(
                                                        delta * 1.3f,
                                                        tween(easing = FastOutLinearInEasing),
                                                    )
                                                }
                                        } ?: run { overscrollJob?.cancel() }
                                },
                                onDragStart = { dragStartOffset -> dragDropState.onDragStart(dragStartOffset) },
                                onDragEnd = {
                                    dragDropState.onDragInterrupted(true)
                                    overscrollJob?.cancel()
                                },
                                onDragCancel = {
                                    dragDropState.onDragInterrupted()
                                    overscrollJob?.cancel()
                                },
                            )
                        },
            ) {
                itemsIndexed(
                    state.artworkQueue,
                    // Same key shape QueueBottomSheet uses over the full list.
                    key = { i, t -> i.toString() + t.videoId },
                ) { index, track ->
                    // Local index == absolute queue index (whole list is shown), so everything the
                    // PLAYER is told — click seeks, ⋯ sheet, drag reorder — takes this directly.
                    val queueIndex = index
                    DraggableItem(
                        dragDropState = dragDropState,
                        index = index,
                        modifier = Modifier,
                    ) { _ ->
                        // Owner's call: the OLD queue sheet's row component, verbatim — no bespoke
                        // row. Long-press-drag reorders (list-level gesture above); ⋯ opens the
                        // same per-item sheet the queue sheet uses; the current track gets the
                        // equalizer highlight.
                        SongFullWidthItems(
                            track = track,
                            isPlaying = queueIndex == state.currentOrderIndex,
                            modifier = Modifier.fillMaxWidth(),
                            onClickListener = { videoId ->
                                if (videoId == track.videoId) actions.onSeekToQueueIndex(queueIndex)
                            },
                            onMoreClickListener = { queueItemSheetIndex = queueIndex },
                        )
                    }
                }
            }
            if (state.artworkQueue.isNotEmpty()) {
                // Over the last song row (the list's bottom fade is blank space, so the button
                // centres on the last visible row). Scrolls the CURRENT track's row — equalizer
                // and all — to the top of the list; played tracks above it stay reachable.
                AppleMusicFloatingCircleButton(
                    icon = SimpIcons.MyLocation,
                    onClick = {
                        val target = state.currentOrderIndex
                        if (target >= 0 && target < lazyListState.layoutInfo.totalItemsCount) {
                            coroutineScope.launch { lazyListState.animateScrollToItem(target) }
                        }
                    },
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = QUEUE_BOTTOM_FADE + 16.dp),
                    contentDescription = stringResource(Res.string.now_playing),
                )
            }
        }

        AppleMusicBottomCluster(
            state = state,
            actions = actions,
            typography = typography,
            viewState = viewState,
            onSelectView = onSelectView,
            activePillContainer = activePillContainer,
            activePillContent = activePillContent,
            deviceVolumeController = deviceVolumeController,
            compact = isCompact,
            // Compact queue keeps transport + dock only: this list has no tap-to-toggle surface
            // the way the lyrics page does, so the cluster is always in-flow — the full block
            // (slider/times/volume) left the list under ~90dp in the side panel.
            transportOnly = isCompact,
        )
    }
}

/** [SimpIcons.Info] [SimpIcons.PlaylistAdd] [SimpIcons.Shuffle] [SimpIcons.Repeat] — exactly, per the corrected spec. */
@Composable
private fun AppleMusicQueuePillsRow(
    state: NowPlayingContentState,
    actions: NowPlayingContentActions,
    activePillContainer: Color,
    activePillContent: Color,
    modifier: Modifier = Modifier,
) {
    val repeatState = state.controllerState.repeatState
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppleMusicQueuePill(
            icon = SimpIcons.Info,
            active = false,
            activeContainer = activePillContainer,
            activeContent = activePillContent,
            onClick = { actions.onShowInfo() },
            modifier = Modifier.weight(1f),
        )
        AppleMusicQueuePill(
            icon = SimpIcons.PlaylistAdd,
            active = false,
            activeContainer = activePillContainer,
            activeContent = activePillContent,
            onClick = { actions.onShowAddToPlaylist() },
            modifier = Modifier.weight(1f),
        )
        AppleMusicQueuePill(
            icon = SimpIcons.Shuffle,
            active = state.controllerState.isShuffle,
            activeContainer = activePillContainer,
            activeContent = activePillContent,
            onClick = { actions.onUIEvent(UIEvent.Shuffle) },
            modifier = Modifier.weight(1f),
        )
        AppleMusicQueuePill(
            icon = if (repeatState is RepeatState.One) SimpIcons.RepeatOne else SimpIcons.Repeat,
            active = repeatState !is RepeatState.None,
            activeContainer = activePillContainer,
            activeContent = activePillContent,
            onClick = { actions.onUIEvent(UIEvent.Repeat) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AppleMusicQueuePill(
    icon: ImageVector,
    active: Boolean,
    activeContainer: Color,
    activeContent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                // Gentler than the default 1.35×: a pill is 83dp wide with only a 10dp gap, so
                // the full inflate would visibly overlap its neighbour on every tap.
                .appleMusicPressInflate(pressedScale = 1.08f)
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (active) activeContainer else AppleMusicPillInactive)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "",
            tint = if (active) activeContent else Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun AppleMusicContinuePlayingHeader(
    state: NowPlayingContentState,
    dataStoreManager: DataStoreManager,
    typography: AppleMusicTypography,
    activePillContainer: Color,
    activePillContent: Color,
    isFmQueue: Boolean,
    onEndlessDisabled: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    // remember the mapped flow: a bare .map in composition builds a new Flow every recomposition
    // (FlowOperatorInvokedInComposition, promoted to an error here).
    val endlessQueueFlow =
        remember(dataStoreManager) {
            dataStoreManager.endlessQueue.map { it == DataStoreManager.TRUE }
        }
    val endlessQueueEnabled by endlessQueueFlow.collectAsStateWithLifecycle(initialValue = false)
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Two stacked lines instead of one "Continue Playing": the small label says WHAT this
            // section is, the line under it says WHERE the queue came from. A queue with no source
            // name (a bare radio, a restored session) simply drops the second line rather than
            // printing an empty one.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    // "Now playing 12/34" — the count rides the subtitle line because this row's
                    // right half is already taken by the endless-queue switch, and the playlist
                    // name below marquee-fills the whole width. Same queueSectionSubtitle the
                    // endless label uses, mirroring the queue sheet's 队列 xx/YY.
                    text =
                        if (state.currentOrderIndex >= 0 && state.artworkQueue.isNotEmpty()) {
                            "${stringResource(Res.string.now_playing)} ${state.currentOrderIndex + 1}/${state.artworkQueue.size}"
                        } else {
                            stringResource(Res.string.now_playing)
                        },
                    style = typography.queueSectionSubtitle,
                )
                val source = state.screenData.playlistName
                if (source.isNotBlank()) {
                    Text(
                        text = source,
                        style = typography.queueSectionHeader,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // The switch needs its own label, exactly like the queue sheet's — unlabelled it
            // reads as a mystery toggle.
            Text(
                text = stringResource(Res.string.endless_queue),
                style = typography.queueSectionSubtitle,
                modifier = Modifier.padding(end = 8.dp),
            )
            Switch(
                checked = isFmQueue || endlessQueueEnabled,
                onCheckedChange = { checked ->
                    if (isFmQueue) {
                        showToast(
                            runBlocking { getString(Res.string.endless_queue_fm_locked) },
                            ToastGravity.Bottom,
                        )
                    } else {
                        // 关开关=裁掉电台追加的歌、恢复原队列(对齐 YTM autoplay)
                        if (!checked) onEndlessDisabled()
                        coroutineScope.launch { dataStoreManager.setEndlessQueue(checked) }
                    }
                },
                colors =
                    SwitchDefaults.colors(
                        // On state takes the artwork-derived pair this style already uses for its
                        // active pills, instead of the theme's green — on a page painted from the
                        // cover art, a fixed accent is the one element that does not belong to the
                        // record playing.
                        checkedTrackColor = activePillContainer,
                        checkedThumbColor = activePillContent,
                        checkedBorderColor = Color.Transparent,
                        // Transparent when off, so the control reads as an outline sitting on the
                        // page rather than a grey slab: this row has no surface of its own, and
                        // Material's default unchecked track paints one.
                        uncheckedTrackColor = Color.Transparent,
                        uncheckedBorderColor = Color.White.copy(alpha = 0.45f),
                        uncheckedThumbColor = Color.White.copy(alpha = 0.75f),
                    ),
                modifier = Modifier.appleMusicPressInflate(pressedScale = 1.08f),
            )
        }
    }
}

// The fade at each edge of the queue list, and the content padding that matches it — declared
// once so the two can never drift apart.
private val QUEUE_TOP_FADE = 24.dp
private val QUEUE_BOTTOM_FADE = 48.dp
