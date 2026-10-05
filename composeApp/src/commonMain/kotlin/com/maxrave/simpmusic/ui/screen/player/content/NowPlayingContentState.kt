package com.maxrave.simpmusic.ui.screen.player.content

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maxrave.domain.data.entities.NewFormatEntity
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.streams.TimeLine
import com.maxrave.domain.data.player.GenericCastState
import com.maxrave.domain.mediaservice.handler.ControlState
import com.maxrave.domain.mediaservice.handler.RepeatState
import com.maxrave.simpmusic.extension.GradientOffset
import com.maxrave.simpmusic.viewModel.LyricsProvider
import com.maxrave.simpmusic.viewModel.NowPlayingScreenData
import com.maxrave.simpmusic.viewModel.RemoteSongLikeState
import com.maxrave.simpmusic.viewModel.UIEvent
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

/**
 * Whether the lyrics currently on screen can be rated.
 *
 * SimpMusic Lyrics is the only provider with a vote endpoint, and the vote is cast against the
 * SimpMusic record itself — so the provider tag alone is NOT the condition: `simpMusicLyrics`
 * must actually be there. Either half qualifying is enough, because the dialog rates whichever
 * of the two came from SimpMusic.
 *
 * Lives on the contract the three styles share. The Apple Music style shipped its floating vote
 * button ungated — offering a rating on YouTube, LRCLIB and Spotify lyrics alike — precisely
 * because this rule existed only as an expression copy-pasted inside the other two styles, where
 * a new style had no reason to go looking for it.
 */
internal fun NowPlayingScreenData.LyricsData?.canVote(): Boolean {
    val data = this ?: return false
    val votableLyrics =
        data.lyricsProvider == LyricsProvider.SIMPMUSIC && data.lyrics.simpMusicLyrics != null
    val votableTranslation =
        data.translatedLyrics?.second == LyricsProvider.SIMPMUSIC &&
            data.translatedLyrics?.first?.simpMusicLyrics != null
    return votableLyrics || votableTranslation
}

// Backdrop behind the player. A dark surface rather than pure black: #000000 reads as a hole
// next to the artwork-tinted gradient and cards, which is why Spotify sits its player on a
// near-black surface instead. Used for the gradient's end colour, the fade-to target and the
// area below the gradient so all three match exactly and leave no seam.
internal val PlayerBackdropColor = Color(0xFF121212)

/**
 * Drag the Pager by exactly the consumed finger delta, capped at one neighbouring page. Pager's
 * fling is disabled: release sends the player skip once and animates only the remaining distance
 * with [ArtworkPagerSnapAnimation]. No velocity decay or spring can overshoot and pull the cover
 * back. The shell keeps the visual track on the old page until both player and Pager agree.
 */
@Composable
internal fun Modifier.artworkDragPager(
    state: NowPlayingContentState,
    actions: NowPlayingContentActions,
): Modifier {
    val pager = state.artworkPagerState
    val onUIEvent by rememberUpdatedState(actions.onUIEvent)
    val onDragChanged by rememberUpdatedState(actions.onArtworkDragChanged)
    val onSkipRequested by rememberUpdatedState(actions.onArtworkSkipRequested)
    val enabled =
        state.artworkQueue.isNotEmpty() &&
            state.controllerState.repeatState !is RepeatState.One &&
            (!state.artworkMotionInProgress || state.artworkDragInProgress)
    val canNext = state.controllerState.isNextAvailable
    val canPrevious = state.controllerState.isPreviousAvailable
    val thresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
    var startPage by remember(pager) { mutableIntStateOf(0) }
    var dragPx by remember(pager) { mutableFloatStateOf(0f) }
    val dragState = rememberDraggableState { delta ->
        val pageWidth = (pager.layoutInfo.pageSize + pager.layoutInfo.pageSpacing).toFloat()
        if (pageWidth > 0f) {
            val next = (dragPx + delta).coerceIn(
                if (canNext) -pageWidth else 0f,
                if (canPrevious) pageWidth else 0f,
            )
            val consumed = next - dragPx
            dragPx = next
            pager.dispatchRawDelta(-consumed)
        }
    }
    return draggable(
        state = dragState,
        orientation = Orientation.Horizontal,
        enabled = enabled,
        onDragStarted = {
            startPage = pager.settledPage
            dragPx = 0f
            onDragChanged(true)
        },
        onDragStopped = {
            val target =
                when {
                    dragPx <= -thresholdPx && canNext -> startPage + 1
                    dragPx >= thresholdPx && canPrevious -> startPage - 1
                    else -> startPage
                }.coerceIn(0, state.artworkQueue.lastIndex)
            try {
                if (target != startPage) {
                    // Previous would restart the current song after a few seconds; a right drag
                    // always means the previous track, as it did before this gesture rewrite.
                    onSkipRequested(target)
                    onUIEvent(if (target > startPage) UIEvent.Next else UIEvent.SkipToPrevious)
                }
                pager.animateScrollToPage(target, animationSpec = ArtworkPagerSnapAnimation)
            } finally {
                onDragChanged(false)
            }
        },
    )
}

/** Monotonic page transition shared by transport buttons and artwork swipes. */
internal val ArtworkPagerSnapAnimation: AnimationSpec<Float> =
    tween(
        durationMillis = 240,
        easing = LinearOutSlowInEasing,
    )

/** 迷你播放条原有的拉断回弹；与全屏封面 Pager 的单向吸附刻意分离。 */
internal val ArtworkSnapSpring: AnimationSpec<Float> =
    spring(
        dampingRatio = 0.38f,
        stiffness = 700f,
    )

private val RICH_SYNC_TIMESTAMP_REGEX = Regex("""<\d{2}:\d{2}\.\d{2,3}>\s*""")
private val WHITESPACE_REGEX = Regex("""\s+""")

// Word-by-word lyrics carry a timestamp per word; replace each with a space
// (not ""), then collapse — otherwise the words run together.
// Shared by every Now Playing content style (Spotify + M3 Expressive).
internal fun String.stripRichSyncTimestamps(): String =
    replace(RICH_SYNC_TIMESTAMP_REGEX, " ")
        .replace(WHITESPACE_REGEX, " ")
        .trim()

/**
 * Codec label for the Apple Music style's progress-bar badge. Derived from the stream's
 * mimeType (e.g. `audio/webm; codecs="opus"`, `audio/mp4; codecs="mp4a.40.2"`) rather than the
 * itag, which the two YouTube audio families always encode as one of these two codecs. Returns
 * null for anything else so the badge hides instead of showing a guess.
 */
internal fun String?.toAudioCodecLabel(): String? {
    // Fed NewFormatEntity.codecs — "opus", or "mp4a.40.2" for AAC. The regex that fills that
    // column falls back to the WHOLE mimeType when it fails to match, so both shapes have to be
    // recognised here; "aac" covers the Piped path, which reports the codec by name.
    // 网易流按 mimeType 归一成 "flac"/"mp3" 存进同一列(见 StreamRepositoryImpl 网易分支)。
    val codec = this ?: return null
    return when {
        codec.contains("opus", ignoreCase = true) -> "OPUS"
        codec.contains("mp4a", ignoreCase = true) || codec.contains("aac", ignoreCase = true) -> "AAC"
        codec.contains("flac", ignoreCase = true) -> "FLAC"
        codec.contains("mp3", ignoreCase = true) || codec.contains("mpeg", ignoreCase = true) -> "MP3"
        else -> null
    }
}

/**
 * "154 kbps · 48 kHz" — the Apple Music style's quality line, read off the format YouTube served
 * for the track now playing: its `bitrate` (the figure the Info sheet prints in bps) and its
 * `sampleRate`. A figure the format does not carry is dropped rather than guessed, and null comes
 * back when neither is known, so the line hides instead of showing a placeholder.
 */
internal fun NewFormatEntity?.toAudioQualityLabel(): String? {
    val format = this ?: return null
    val parts =
        buildList {
            format.bitrate?.takeIf { it > 0 }?.let { add("${(it / 1000.0).roundToInt()} kbps") }
            format.sampleRate?.takeIf { it > 0 }?.let { add(it.toKhzLabel()) }
        }
    return parts.joinToString(" · ").takeIf { it.isNotEmpty() }
}

// 48000 → "48 kHz", 44100 → "44.1 kHz": one decimal, and none when it would be ".0".
private fun Int.toKhzLabel(): String {
    val tenths = (this + 50) / 100
    return if (tenths % 10 == 0) "${tenths / 10} kHz" else "${tenths / 10}.${tenths % 10} kHz"
}

/**
 * Everything a Now Playing content layer reads. The shell ([com.maxrave.simpmusic.ui.screen.player.NowPlayingScreenContent])
 * owns the ViewModel collection, palette animation, sheets/dialogs and gesture state machines;
 * a content composable only renders from this snapshot.
 */
@Stable
class NowPlayingContentState(
    val screenData: NowPlayingScreenData,
    val controllerState: ControlState,
    // (perf) The 50 ms position snapshot does NOT live here on purpose: a TimeLine field made this
    // whole object new twenty times a second, re-executing every style's tree on each tick. The
    // flow is the only timeline channel — the widgets that actually render position (the three
    // styles' playback controls, the Apple lyric strip's sweep) collect it inside themselves.
    val timelineFlow: StateFlow<TimeLine>,
    val castState: GenericCastState,
    val shouldShowVideo: Boolean,
    /** 当前歌曲是否来自网易；用于元数据加载前也能立即应用源特有 UI 规则。 */
    val isNeteaseSong: Boolean,
    /** 播客节目(队列指纹判定):歌曲红心对节目无效(524),三主题红心隐藏 */
    val isPodcastSong: Boolean = false,
    /** 红心=云端账号状态;该源未登录时置灰(onLoginRequired 提示) */
    val likeEnabled: Boolean = true,
    val remoteLikeState: RemoteSongLikeState,
    val isUserLoggedIn: Boolean,
    val artworkQueue: List<Track>,
    /**
     * 每页的稳定 key(videoId + 同 id 出现序号)。不能直接用 videoId(队列可含重复歌,
     * Compose key 必须唯一),更不能掺 index(重排后 key 变=页面销毁重建=封面闪一下
     * 别的歌)。出现序号在重排后可能互换,但同 id 两首内容相同,视觉无差。
     */
    val artworkPageKeys: List<String> = emptyList(),
    val currentOrderIndex: Int,
    /** Pager 已经落稳、用户此刻实际看到的页面。文字和颜色只跟它走。 */
    val visualOrderIndex: Int,
    val artworkPagerState: PagerState,
    val artworkDragInProgress: Boolean,
    /** 播放器触发的程序化翻页期间为 true；封面据此锁住可见位图。 */
    val artworkMotionInProgress: Boolean,
    val startColor: Animatable<Color, AnimationVector4D>,
    val endColor: Animatable<Color, AnimationVector4D>,
    val spotShadowColor: Color,
    val gradientOffset: GradientOffset,
    val sliderTrackColor: Color,
    val currentLyricLineIndex: Int,
    val showControlLayout: Boolean,
    val controlLayoutAlpha: Float,
    val showHideMiddleLayout: Boolean,
    val shouldShowToolbar: Boolean,
    val isInPipMode: Boolean,
    val mainScrollState: ScrollState,
    val isExpanded: Boolean,
    val dismissIcon: ImageVector,
    /** "154 kbps · 48 kHz" for the stream now playing, or null while unknown — see [toAudioQualityLabel]. */
    val audioQualityLabel: String? = null,
    /** AM 进度条时间行旁的编解码徽章(OPUS/AAC/FLAC/MP3);见 toAudioCodecLabel。 */
    val audioCodecLabel: String? = null,
    /**
     * The user's lyrics delay, applied at read time. [currentLyricLineIndex] already has it baked in;
     * this is for anything that times WITHIN a line (the Apple Music lyric strip's word sweep).
     */
    val lyricsOffsetMs: Long = 0L,
    /**
     * Width / height of the video now playing, 16:9 until the player knows it. Every style sizes
     * its video frame from this one value, so a frame and the spacer that measures it cannot drift.
     */
    val videoAspectRatio: Float = 16f / 9,
) {
    val visualTrack: Track?
        get() = artworkQueue.getOrNull(visualOrderIndex)

    val displayTitle: String
        get() = visualTrack?.title ?: screenData.nowPlayingTitle

    val displayArtistName: String
        get() =
            visualTrack
                ?.artists
                ?.joinToString(", ") { it.name }
                ?.takeIf { it.isNotBlank() }
                ?: screenData.artistName

    val displayIsExplicit: Boolean
        get() = visualTrack?.isExplicit ?: screenData.isExplicit
}

/**
 * Everything a Now Playing content layer can do. All callbacks land in the shell, which owns
 * the ViewModel, the navController and the sheet/dialog visibility flags.
 */
@Stable
class NowPlayingContentActions(
    val onUIEvent: (UIEvent) -> Unit,
    val onArtworkDragChanged: (Boolean) -> Unit = {},
    val onArtworkSkipRequested: (Int) -> Unit = {},
    val onSeekToQueueIndex: (Int) -> Unit,
    val onArtworkBitmap: (ImageBitmap) -> Unit,
    /**
     * Spotify 主题:邻页翻成当前页后,让壳层 startColor 平滑收敛到该页自己的按页渐变色。
     * 否则邻页的 per-page backdrop 层随
     * "current 跳过 Layer 0"的设计关掉,露出还停在**上一首歌**颜色的壳层渐变,调色板
     * 重新生成后才从旧色动画到新色——滑切瞬间背景会出现旧色平台。
     */
    val onSnapPaletteColor: (Color) -> Unit = {},
    val onToggleControls: () -> Unit,
    val onNavigateToArtist: () -> Unit,
    val onOpenListenTogether: () -> Unit,
    /** 网易歌:打开评论列表弹窗(详情卡评论数点击) */
    val onShowNeteaseComments: () -> Unit = {},

    val onShowMoreSheet: () -> Unit,
    val onShowQueue: () -> Unit,
    val onShowInfo: () -> Unit,
    val onShowAddToPlaylist: () -> Unit,
    val onSetRemoteLiked: (Boolean) -> Unit,
    /** 未登录源上的红心点击:给"需要登录"提示而不是打注定失败的请求 */
    val onLoginRequired: () -> Unit = {},
    val onShowFullscreenLyrics: () -> Unit,
    val onShowVoteDialog: () -> Unit,
    val onEnterFullscreenVideo: () -> Unit,
    val onDismiss: () -> Unit,
    val onToolbarVisibilityChange: (Boolean) -> Unit,
    /** Reorders the queue. `from`/`to` are absolute indices into [NowPlayingContentState.artworkQueue]. */
    val onMoveQueueItem: (from: Int, to: Int) -> Unit,
    /** Removes one queue entry. `index` is an absolute index into [NowPlayingContentState.artworkQueue]. */
    val onRemoveQueueItem: (index: Int) -> Unit,
)

/**
 * (perf) 只取 [TimeLine.total] 的低频读法(每首歌才变一次):歌词卡时间戳点击这类只在
 * 点击时需要时长的位置用,避免为它收整条 50ms 的 position 流、把所在面板拖进逐帧重组。
 */
@Composable
internal fun StateFlow<TimeLine>.collectTotalMs(): State<Long> =
    map { it.total }.distinctUntilChanged().collectAsStateWithLifecycle(0L)
