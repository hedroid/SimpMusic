package com.maxrave.simpmusic.expect

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
    /** 按当前设置档位振动一次 */
    fun tap()

    /** 以指定档位振动一次(设置弹窗里选中即预览用) */
    fun tap(level: HapticFeedbackLevel)
}
