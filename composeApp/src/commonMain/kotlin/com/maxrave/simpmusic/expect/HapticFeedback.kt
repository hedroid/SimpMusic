package com.maxrave.simpmusic.expect

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 触感反馈三档强度,存储值(DataStoreManager.Values.HAPTIC_FEEDBACK_LEVEL_*)即枚举名。
 * 放在 expect 层是为了让 commonMain 的共享控件(播放/暂停/切歌/红心)统一调用;
 * Android 之外的平台无振动,actual 为空实现。
 */
enum class HapticFeedbackLevel {
    LIGHT,
    MEDIUM,
    STRONG,
    ;

    companion object {
        /** DataStore 残留值/损坏值回落 MEDIUM,与设置页副标题的归一口径一致 */
        fun parseOr(saved: String?): HapticFeedbackLevel = entries.firstOrNull { it.name == saved } ?: MEDIUM
    }
}

expect object HapticFeedback {
    /** 按当前设置档位振动一次,受总开关(hapticEnabled)门控——常规点击触点用这个 */
    fun tap()

    /**
     * 以指定档位振动一次,不检查总开关:只在设置页语境调用(打开开关的确认震感、
     * 滑动条滑过刻度的预览)——这些动作本身就是"正在开启触感",且打开开关的确认
     * 不能等 DataStore 写入→collect 传播完成,否则会被尚未翻转的开关门自己拦掉。
     */
    fun tap(level: HapticFeedbackLevel)
}

/**
 * 全局点击触感观察器:挂在应用根布局(App.kt 的 Scaffold)上,旁观所有按下/抬起
 * ——不消费事件、不影响任何子组件手势。判定为"点击"(位移 < touchSlop 且按压
 * 时长 < 长按时长)就按当前档位震一次;拖动/滑动(超 slop)与长按(超时)天然
 * 排除——长按有专属即时震感,滑动条刻度有自己的预览。受总开关门控,关闭时零感知。
 *
 * 实现注意(踩过):up 的检测必须用手动循环读 change.pressed,不能用
 * waitForUpOrCancellation——按钮类子组件会把 UP 消费掉,后者把消费当取消,
 * 结果只有空白区震、点按钮不震。Main 通道下父节点照样收到已消费的事件,
 * pressed 标志不受消费影响。
 */
fun Modifier.hapticTapFeedback(): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down =
                awaitFirstDown(
                    requireUnconsumed = false,
                )
            var moved = false
            var up: PointerInputChange? = null
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    up = change
                    break
                }
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    moved = true
                    break
                }
            }
            if (!moved &&
                up != null &&
                up.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
            ) {
                HapticFeedback.tap()
            }
        }
    }

