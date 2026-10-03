package com.maxrave.simpmusic.ui.utils

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 兜底"滚动收尾动画吞掉第一下点击"(2026-10-03 播客页反馈:我的订阅/猜你喜欢点封面
 * 有时第一次没反应,第二次才进得去)。
 *
 * 根因在 Compose scrollable 的设计(foundation 1.12 字节码实证):滚动动画进行中
 * (isScrollInProgress=true——横滑货架 snap 吸附的收尾弹簧、外层列表 fling 的尾巴
 * 都算)收到新按下时,scrollable 在 Initial pass(父先于子)立即 consume 掉 down;
 * 而 clickable 的 tap 检测要求"未消费的 down",整支手势从此不再响应——这一下点击
 * 只起到停住动画的作用。snap 弹簧"视觉上已停但动画未完"的窗口有几百毫秒,用户
 * 看到的就是"点了没反应,再点一下才行"。
 *
 * 本修饰符只接管那条被吞路径:down 到达时已被消费、且后续是干净 tap——手指一旦
 * 移动,scrollable 会消费位移,waitForUpOrCancellation 随即返回 null 放弃,tap/拖动
 * 天然区分——此时补发 onClick。正常 tap(down 未被消费)原样交给配套的 clickable,
 * 不会双发。被兜底的这一次没有涟漪/按压态(按下时 clickable 根本没认领该手势),
 * 换来点击必定生效。
 *
 * 用法:挂在 clickable 之后,两者传同一个 onClick。
 */
fun Modifier.scrollSettleTapRescue(onClick: () -> Unit): Modifier =
    pointerInput(onClick) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!down.isConsumed) return@awaitEachGesture
            if (waitForUpOrCancellation() != null) onClick()
        }
    }
