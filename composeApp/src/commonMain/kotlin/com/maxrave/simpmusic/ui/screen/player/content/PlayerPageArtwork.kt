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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.simpmusic.expect.ui.toImageBitmap
import com.maxrave.simpmusic.ui.component.rememberHolderPainter
import kotlinx.coroutines.flow.collectLatest

/**
 * 播放页大图(artwork pager 每页)专用的 URL 派生与渲染。
 *
 * 为什么不直接用 `screenData.thumbnailURL`(mediaItem.artworkUri):那是给通知栏/迷你条/55dp
 * 小头像消费的,构建时被钉在 w544/param=500 的小档;播放页封面槽位在 1080p 设备上约 960
 * 物理像素,直接用会被上采样 ~1.8 倍(“封面很模糊”的根因)。这里在请求侧把两源统一升到
 * 1080:YT googleusercontent `=wNNN-hNNN` → `=w1080-h1080`,网易 `?param=NNNyNNN` →
 * `?param=1080y1080`。列表/小图路径不受影响。
 */
private val YT_SIZE_PARAM = Regex("=w\\d+-h\\d+")
private val NETEASE_SIZE_PARAM = Regex("param=\\d+y\\d+")

internal fun Track?.playerArtworkUrl(): String? {
    if (this == null) return null
    val best =
        thumbnails?.maxByOrNull { it.width * it.height }?.url
            // 网易数字 id 不可能命中 i.ytimg,别给它们造假 URL
            ?: videoId?.takeIf { it.isNotEmpty() && it.toLongOrNull() == null }
                ?.let { "https://i.ytimg.com/vi/$it/maxresdefault.jpg" }
    return best
        ?.replace(YT_SIZE_PARAM, "=w1080-h1080")
        ?.replace(NETEASE_SIZE_PARAM, "param=1080y1080")
}

/** 页内自判视频轨:取最大缩略图,宽高非正方形即按 16:9 摆(镜像 handler 的 isSong 判定)。 */
internal fun Track?.playerArtworkIsVideo(): Boolean {
    val best = this?.thumbnails?.maxByOrNull { it.width * it.height } ?: return false
    return best.width != best.height
}

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
 * - holder 占位画在**底层**,封面加载后直接盖上去 — 加载期间不再是“灰图顶掉封面”;
 * - 显式 [.size] 1080:三主题的页面/磨砂背景请求同尺寸同 key,内存缓存互认,翻页/换页
 *   直接命中;
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
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    onArtworkLoaded: ((ImageBitmap) -> Unit)? = null,
    onCurrentArtworkLoaded: ((ImageBitmap) -> Unit)? = null,
) {
    val baseUrl = remember(pageTrack?.videoId) { pageTrack.playerArtworkUrl() }
    var artworkUrl by remember(baseUrl) { mutableStateOf(baseUrl) }

    val painter =
        rememberAsyncImagePainter(
            ImageRequest
                .Builder(LocalPlatformContext.current)
                .data(artworkUrl)
                .diskCachePolicy(CachePolicy.ENABLED)
                .diskCacheKey(artworkUrl)
                .size(1080)
                .crossfade(250)
                .build(),
        )

    // 每次成功加载:喂页调色板;maxres 404 退 hqdefault 一次。
    // painter.state 在 coil3 是 StateFlow(StateFlow<State>),直接 collect — 订阅即重发当前值。
    LaunchedEffect(painter) {
        painter.state.collectLatest { state ->
            when (state) {
                is AsyncImagePainter.State.Success ->
                    onArtworkLoaded?.invoke(state.result.image.toImageBitmap())
                is AsyncImagePainter.State.Error -> {
                    val fallback =
                        artworkUrl?.replace("maxresdefault", "hqdefault")
                    if (fallback != null && fallback != artworkUrl) artworkUrl = fallback
                }
                else -> Unit
            }
        }
    }

    // 翻成当前页:用已加载位图立即补发一次(见类注释),不等下一次网络事件。
    LaunchedEffect(painter, isCurrentPage) {
        if (!isCurrentPage) return@LaunchedEffect
        painter.state.collectLatest { state ->
            if (state is AsyncImagePainter.State.Success) {
                onCurrentArtworkLoaded?.invoke(state.result.image.toImageBitmap())
            }
        }
    }

    Box(modifier = modifier) {
        val holder: Painter = rememberHolderPainter()
        Image(
            painter = holder,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painter,
            contentDescription = pageTrack?.title,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
