package com.maxrave.simpmusic.ui.utils

private val YT_SIZE_PARAM = Regex("=w(\\d+)-h(\\d+)")
private val NETEASE_SIZE_PARAM = Regex("param=\\d+y\\d+")

/**
 * 全屏级大图头图/封面专用:请求侧把封面 URL 升到 1080 ——
 * 网易 `?param=NNNyNNN` → `?param=1080y1080`(恒方形);
 * YT googleusercontent **只升"方形小图"**(`=w544-h544`/`=w617-h617` → `=w1080-h1080`),
 * 宽横幅(如歌手页 banner `=w2880-h1200`)本就够大且改方形会毁构图,一律不动。
 *
 * 1080p 设备上这些头图槽位约 960-1080 物理像素,而数据侧 URL 普遍被钉在 544/500/617
 * (YT 建队列钉 w544、网易 toThumbnails 500、YT 歌手头像源 w617),
 * 直接用会被上采样 1.8-2 倍 —— "封面很模糊"的根因。
 *
 * **只在请求处调用,不要改数据源/模型**:通知栏/迷你条/列表 tile/55dp 小头像继续用
 * 原始 544/500 正合适,升档纯浪费流量。注意 diskCacheKey/memoryCacheKey 要跟着用改写后的
 * URL,否则同图两套缓存条目。
 */
fun String?.toHiResArtworkUrl(): String? {
    if (this == null) return this
    val ytRewritten =
        YT_SIZE_PARAM.replace(this) { m ->
            val w = m.groupValues[1].toIntOrNull()
            val h = m.groupValues[2].toIntOrNull()
            if (w != null && h != null && w == h) "=w1080-h1080" else m.value
        }
    return NETEASE_SIZE_PARAM.replace(ytRewritten, "param=1080y1080")
}
