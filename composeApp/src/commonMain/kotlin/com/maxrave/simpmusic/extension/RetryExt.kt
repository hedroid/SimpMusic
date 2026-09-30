package com.maxrave.simpmusic.extension

import com.maxrave.logger.Logger
import kotlinx.coroutines.delay

/**
 * 带线性退避的条件重试:满足 [retryOn](典型=失败/空结果)就再试,最多 [times] 次全程,
 * 次间延迟 firstDelayMs*attempt。返回最后一次结果(不抛)。
 *
 * 动机(用户 2026-09-30 反馈):"添加到歌单"弹窗的云端歌单首拉经常撞上冷网络/
 * 代理抖动一次性失败,弹窗直接空列表;退避重试把瞬时失败就地消化,重试期间调用方
 * 保留旧列表,界面不闪空。
 */
suspend fun <T> retryIf(
    tag: String,
    times: Int = 3,
    firstDelayMs: Long = 700,
    retryOn: (T) -> Boolean,
    block: suspend (attempt: Int) -> T,
): T {
    var result = block(0)
    var attempt = 1
    while (attempt < times && retryOn(result)) {
        Logger.w(tag, "retryIf: attempt $attempt/${times - 1} after unsatisfactory result, backing off ${firstDelayMs * attempt}ms")
        delay(firstDelayMs * attempt)
        result = block(attempt)
        attempt++
    }
    return result
}
