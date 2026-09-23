package com.maxrave.simpmusic.expect

// 桌面端无振动马达,空实现
actual object HapticFeedback {
    actual fun tap() {}

    actual fun tap(level: HapticFeedbackLevel) {}
}
