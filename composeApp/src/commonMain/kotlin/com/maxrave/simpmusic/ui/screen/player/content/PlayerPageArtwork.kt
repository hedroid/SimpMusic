package com.maxrave.simpmusic.ui.screen.player.content

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.simpmusic.expect.ui.toImageBitmap
import com.maxrave.simpmusic.ui.component.rememberHolderPainter
import com.maxrave.simpmusic.ui.utils.toHiResArtworkUrl
import kotlinx.coroutines.flow.collectLatest

/**
 * 播放页大图(artwork pager 每页)专用的 URL 派生与渲染。
 *
 * 为什么不直接用 `screenData.thumbnailURL`(mediaItem.artworkUri):那是给通知栏/迷你条/55dp
 * 小头像消费的,构建时被钉在 w544/param=500 的小档;播放页封面槽位在 1080p 设备上约 960
 * 物理像素,直接用会被上采样 ~1.8 倍(“封面很模糊”的根因)。尺寸升档统一走
 * [toHiResArtworkUrl](ui/utils/HiResArtwork.kt,详情页头图同款)。列表/小图路径不受影响。
 */
internal fun Track?.playerArtworkUrl(): String? {
    if (this == null) return null
    val best =
        thumbnails?.maxByOrNull { it.width * it.height }?.url
            // 网易数字 id 不可能命中 i.ytimg,别给它们造假 URL
            ?: videoId?.takeIf { it.isNotEmpty() && it.toLongOrNull() == null }
                ?.let { "https://i.ytimg.com/vi/$it/maxresdefault.jpg" }
    return best.toHiResArtworkUrl()
}

/** 页内自判视频轨:取最大缩略图,宽高非正方形即按 16:9 摆(镜像 handler 的 isSong 判定)。 */
internal fun Track?.playerArtworkIsVideo(): Boolean {
    val best = this?.thumbnails?.maxByOrNull { it.width * it.height } ?: return false
    return best.width != best.height
}

/**
 * 队列数据自带的原始缩略图 URL(网易 param=500y500 / YT w544)——**不做升档**。
 * 这是渐进加载的底层:迷你条/通知栏渲染当前曲用的就是它,磁盘缓存大概率已在;
 * 播放页滑到一个从未加载过 1080 版的页时,先拿它垫底(同图低清),1080 到位后
 * 无缝盖上——封面不再以灰块等网络(2026-10-03 手势滑切"封面闪"六轮根因)。
 */
internal fun Track?.playerArtworkUrlLow(): String? =
    this?.thumbnails?.maxByOrNull { it.width * it.height }?.url

/**
 * artwork pager 的统一封面节点 — “按页数据驱动”的落点。
 *
 * 旧结构是“当前页画 screenData.thumbnailURL / 相邻页画 Track 缩略图”两个分支:滑到/翻到
 * 当前页那一刻分支交换,AsyncImage 整个销毁重建,新请求先落灰占位再 crossfade(550ms),
 * 且旧请求的磁盘 key 带 "BIGGER" 后缀、与相邻页条目互不复用 — 这就是切歌“像重绘一样闪”
 * 的主因。本组件永远画自己那页 Track 的 URL(model 不随 current/adjacent 翻转变化),
 * 翻页只是参数变化;封面在滑动过程中已加载完,切过去零请求零占位。
 *
 * 细节:
 * - holder 占位画在**底层**,低清与高清请求都提前启动；
 * - 显式 [.size] 1080:三主题的页面/磨砂背景请求同尺寸同 key,内存缓存互认；
 * - Pager 运动期间冻结当前可见层。高清图即使此时完成也只留在后台，目标页下次退到
 *   屏外才静默升级；这保证手势和按钮动画期间只有位置变化，没有图片 crossfade；
 * - maxresdefault 404 时退 hqdefault(YT 视频轨老坑,与旧 live 分支同款重试);
 * - 调色板馈送:onArtworkLoaded 每次成功加载都会调(相邻页喂自己的页调色板);
 *   onCurrentArtworkLoaded 额外在**页面翻成当前页时**用已加载的位图补发一次 — 旧结构
 *   靠“翻页后重新发请求 → onSuccess”馈送,本组件翻页不发请求,没有这次补发背景色会
 *   停在上一首。
 */
@Composable
internal fun PlayerPageArtwork(
    pageTrack: Track?,
    isCurrentPage: Boolean,
    isSettledPage: Boolean,
    isPagerMoving: Boolean,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    onArtworkLoaded: ((ImageBitmap) -> Unit)? = null,
    onCurrentArtworkLoaded: ((ImageBitmap) -> Unit)? = null,
) {
    val baseUrl = remember(pageTrack?.videoId) { pageTrack.playerArtworkUrl() }
    var artworkUrl by remember(baseUrl) { mutableStateOf(baseUrl) }
    val lowUrl = remember(pageTrack?.videoId) { pageTrack.playerArtworkUrlLow() }
    var retryNonce by remember(baseUrl) { mutableStateOf(0) }

    // 页面一旦开始移动，本次访问期间就冻结清晰度层。即使 1080 图在拖动/回弹中完成，
    // 也不能盖到正在运动的 500 图上；目标页停稳后仍保持同一张图，等它下次退到屏外时
    // 再静默升级。此前的“低清垫底 + 1080 crossfade”会在手势中途改变像素，正是最后
    // 一层看得见的闪动。按钮切歌和手势切歌都读取同一个 Pager 状态，因此规则一致。
    var qualityLockedForVisit by remember(baseUrl) { mutableStateOf(false) }
    var showHighResolution by remember(baseUrl) { mutableStateOf(false) }
    var retriedThisVisit by remember(baseUrl) { mutableStateOf(false) }
    var lowBitmap by remember(baseUrl) { mutableStateOf<ImageBitmap?>(null) }
    var highBitmap by remember(baseUrl) { mutableStateOf<ImageBitmap?>(null) }

    // The request MUST be remembered: coil3's model equality is reference-based for ImageRequest,
    // so a freshly built instance on every recomposition (e.g. when isCurrentPage flips at the
    // swipe-settle) reads as a model change and RESTARTS the painter — a frame of the holder
    // placeholder plus a crossfade over an already-decoded cover, which is the "cover flashes on
    // swipe-settle" users kept reporting. Same URL in → same instance out → no restart.
    val platformContext = LocalPlatformContext.current
    val request =
        remember(artworkUrl, retryNonce, platformContext) {
            ImageRequest
                .Builder(platformContext)
                .data(artworkUrl)
                .diskCachePolicy(CachePolicy.ENABLED)
                .diskCacheKey(artworkUrl)
                .size(1080)
                .build()
        }

    val highPainter = rememberAsyncImagePainter(request)
    val lowRequest =
        remember(lowUrl, platformContext) {
            ImageRequest
                .Builder(platformContext)
                .data(lowUrl)
                .diskCachePolicy(CachePolicy.ENABLED)
                .diskCacheKey(lowUrl)
                .size(1080)
                .build()
        }
    val lowPainter = rememberAsyncImagePainter(lowRequest)

    LaunchedEffect(lowPainter, showHighResolution) {
        lowPainter.state.collectLatest { state ->
            if (state is AsyncImagePainter.State.Success) {
                val bitmap = state.result.image.toImageBitmap()
                lowBitmap = bitmap
                if (!showHighResolution) onArtworkLoaded?.invoke(bitmap)
            }
        }
    }

    // 高清请求始终在后台跑，但只有页面静止且本次访问没有被运动锁住时才可见。
    // maxres 404 仍回退 hqdefault；真正的失败在下一次进入当前页时只重试一次。
    LaunchedEffect(
        highPainter,
        isPagerMoving,
        qualityLockedForVisit,
        isCurrentPage,
        retriedThisVisit,
    ) {
        highPainter.state.collectLatest { state ->
            when (state) {
                is AsyncImagePainter.State.Success -> {
                    val bitmap = state.result.image.toImageBitmap()
                    highBitmap = bitmap
                    if (!isPagerMoving && !qualityLockedForVisit) {
                        showHighResolution = true
                        onArtworkLoaded?.invoke(bitmap)
                    }
                }
                is AsyncImagePainter.State.Error -> {
                    val fallback =
                        artworkUrl?.replace("maxresdefault", "hqdefault")
                    if (fallback != null && fallback != artworkUrl) {
                        artworkUrl = fallback
                    } else if (isCurrentPage && !retriedThisVisit) {
                        retriedThisVisit = true
                        retryNonce += 1
                    }
                }
                else -> Unit
            }
        }
    }

    LaunchedEffect(isPagerMoving, isSettledPage, highBitmap) {
        if (isPagerMoving) {
            qualityLockedForVisit = true
        } else if (!isSettledPage) {
            // 本页已经完全退到屏外：解除访问锁，并在不可见处切到已经解码好的高清图。
            qualityLockedForVisit = false
            retriedThisVisit = false
            if (highBitmap != null) showHighResolution = true
        } else if (!qualityLockedForVisit && highBitmap != null) {
            // 初次打开播放页、尚未发生任何切页时，允许当前页正常升到高清。
            showHighResolution = true
        }
    }

    // 当前页只向背景/调色板馈送“屏幕上实际画的那张图”，避免封面仍锁在低清层时
    // 背景先按隐藏的高清图跳色。Error 重试使用显式 nonce；原来的 null→原值在同一
    // snapshot 内会被合并，实际上不保证重启请求。
    LaunchedEffect(isCurrentPage, showHighResolution, highBitmap, lowBitmap) {
        if (!isCurrentPage) return@LaunchedEffect
        val visibleBitmap = if (showHighResolution) highBitmap else lowBitmap
        visibleBitmap?.let { onCurrentArtworkLoaded?.invoke(it) }
    }

    Box(modifier = modifier) {
        val holder: Painter = rememberHolderPainter()
        Image(
            painter = holder,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = lowPainter,
            contentDescription = null,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
        )
        // alpha=0 的 Image 仍保持 painter 处于 remembered/预取状态；只有在屏外或尚未
        // 开始切页的静止阶段才翻成 1。没有 crossfade，也没有运动中换像素。
        Image(
            painter = highPainter,
            contentDescription = pageTrack?.title,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize().alpha(if (showHighResolution) 1f else 0f),
        )
    }
}
