package com.maxrave.simpmusic.ui.screen.player.content

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maxrave.domain.data.entities.NewFormatEntity
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.streams.TimeLine
import com.maxrave.domain.data.player.GenericCastState
import com.maxrave.domain.mediaservice.handler.ControlState
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
 * 封面 pager 的落位弹簧(用户 2026-09-24 要求"橡皮筋效果更强"):中低刚度+可见回弹,
 * 松手后页面冲过头再弹回来 —— "拉断橡皮筋"的弹性落位感。默认 spring(无回弹)读作
 * 平移到位,没有弹的感觉。三主题的 pager 落位共用它(shell 的 snapAnimationSpec +
 * 各 HorizontalPager 的 flingBehavior);播放器跟歌的 scrollToPage 是直跳,不经此弹簧。
 */
internal val ArtworkSnapSpring: AnimationSpec<Float> =
    spring(
        dampingRatio = 0.38f, // MediumBouncy(0.5)弹幅约 12px 偏含蓄;0.38 弹感明显(用户点名"更强")
        stiffness = 700f, // 落位 ~250ms:弹得干脆,不拖沓
    )

/**
 * 封面 pager 的统一 fling 行为。`PagerSnapDistance.atMost(1)` 是这里的主角:
 * 默认 fling 按 velocity 惯性选目标页,一次快甩会飞过 2-3 页再被落位弹簧拽回来——
 * 途中扫过的页面若封面尚未加载,一帧 holder 灰渐变就"啪"地划过屏幕(手势滑切
 * "封面闪一下"的真凶,2026-10-03 录帧+探针实证:Loading 灰帧恰在越页窗口出现);
 * 即便都已加载,封面飞过头再弹回也读作"跳"。夹到 1 页 = 一次手势一页,
 * Spotify/YTM 同款手感。按上一首/下一首不走 fling,天然不受影响。
 */
@Composable
internal fun rememberArtworkPagerFlingBehavior(state: PagerState): TargetedFlingBehavior =
    PagerDefaults.flingBehavior(
        state = state,
        pagerSnapDistance = PagerSnapDistance.atMost(1),
        snapAnimationSpec = ArtworkSnapSpring,
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
    val artworkPagerState: PagerState,
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
)

/**
 * Everything a Now Playing content layer can do. All callbacks land in the shell, which owns
 * the ViewModel, the navController and the sheet/dialog visibility flags.
 */
@Stable
class NowPlayingContentActions(
    val onUIEvent: (UIEvent) -> Unit,
    val onSeekToQueueIndex: (Int) -> Unit,
    val onArtworkBitmap: (ImageBitmap) -> Unit,
    /**
     * (fix 滑切背景闪)Spotify 主题:邻页翻成当前页的瞬间,把壳层 startColor 先 SNAP 到
     * 该页自己的按页渐变色(用户此刻正看着的颜色)。否则邻页的 per-page backdrop 层随
     * "current 跳过 Layer 0"的设计关掉,露出还停在**上一首歌**颜色的壳层渐变,调色板
     * 重新生成后才从旧色动画到新色——滑切瞬间背景"旧色闪一下再变过去"的根源。
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
