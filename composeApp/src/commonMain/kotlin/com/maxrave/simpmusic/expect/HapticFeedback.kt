package com.maxrave.simpmusic.expect

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
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

    /**
     * 比当前设置档位高一档的"强调"震动(仍受总开关门控,已是最强档则保持最强):
     * 给需要比普通点击更突出的动作——长按弹音源菜单、迷你条拉断切歌(用户 2026-09-24
     * 定案:"这两种动作更突出")。
     */
    fun tapEmphasized()
}

/**
 * 全局点击触感观察器:挂在应用根布局(App.kt 的 Scaffold)与各独立窗口(sheet)的根布局上,
 * 旁观所有按下/抬起——不消费事件、不影响任何子组件手势。判定为"点击"(位移 < touchSlop
 * 且按压时长 < 长按时长)且**真正命中了交互控件**才按当前档位震一次;拖动/滑动(超 slop)
 * 与长按(超时)天然排除。受总开关门控,关闭时零感知。
 *
 * "命中交互控件"的判定:全程在 **Final 通道**读事件——clickable 类控件会在自己的
 * Main 通道处理里消费 UP,死区(播放页封面、页面背景)没有控件消费;Final 通道
 * 子先父后分发,UP 到达本观察器时消费已定,`up.isConsumed` 即真相。配套约束:
 * **自定义手势里对"点击"路径也要 consume UP**(sourceSwitchGesture 已补),否则该
 * 控件会丢点击震感。
 *
 * 实现注意(踩过两坑):①不能用 awaitEachGesture——它的手势边界同步要求块内首个
 * await 在 Main/Initial 通道,整体挂 Final 会彻底哑掉(实测零触发);这里手写外层
 * while 在 Final 通道跟指针流。②UP 检测必须手动循环读 change.pressed,不能用
 * waitForUpOrCancellation——它把"UP 被子组件消费"当取消。pressed 标志不受消费影响。
 */
fun Modifier.hapticTapFeedback(): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            // DOWN 在 Main 通道取:awaitEachGesture 的手势边界同步要求块内首个 await 在
            // Main/Initial(整体挂 Final 会彻底哑掉,实测零触发)
            val down =
                awaitFirstDown(
                    requireUnconsumed = false,
                )
            val downTime = down.uptimeMillis
            val downPosition = down.position
            var moved = false
            var up: PointerInputChange? = null
            while (true) {
                // UP 在 Final 通道读:Final 子先父后,UP 到达这里时 clickable 类子控件已在
                // Main 通道完成消费,isConsumed 即"命中交互控件"的最终真相
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    up = change
                    break
                }
                if ((change.position - downPosition).getDistance() > viewConfiguration.touchSlop) {
                    moved = true // 不 break:滑动后继续跟到抬起,防止抬起被当新手势
                }
            }
            if (!moved &&
                up != null &&
                up.uptimeMillis - downTime < viewConfiguration.longPressTimeoutMillis
            ) {
                if (up.isConsumed) HapticFeedback.tap()
            }
        }
    }

