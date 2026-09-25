package com.maxrave.simpmusic.expect

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import com.maxrave.domain.manager.DataStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 触感反馈的 Android 实现:Vibrator 幅值控制(支持幅值的马达上三档=不同幅值+时长,
 * 不支持的老马达退化为三档时长差)。总开关尊重系统"触摸振动"设置——用户在系统层
 * 关掉触感时 app 内静默,与 View.performHapticFeedback 的默认语义一致。
 *
 * 必须[initialize]后才生效(SimpMusicApplication.onCreate 里 Koin 起来之后调用);
 * 未初始化的调用(如测试)安全空跑。
 */
actual object HapticFeedback {
    /** 总开关(默认关),关闭时强度设置保留但不生效 */
    @Volatile private var enabled: Boolean = false

    @Volatile private var level: HapticFeedbackLevel = HapticFeedbackLevel.MEDIUM

    @Volatile private var vibrator: Vibrator? = null

    @Volatile private var hasAmplitudeControl: Boolean = false

    @Volatile private var appContext: Context? = null

    fun initialize(context: Context, dataStoreManager: DataStoreManager, scope: CoroutineScope) {
        appContext = context.applicationContext
        vibrator = context.getSystemService(Vibrator::class.java)
        hasAmplitudeControl = vibrator?.hasAmplitudeControl() == true
        scope.launch {
            dataStoreManager.hapticEnabled.collect { enabled = it == DataStoreManager.TRUE }
        }
        scope.launch {
            dataStoreManager.hapticFeedbackLevel.collect { level = HapticFeedbackLevel.parseOr(it) }
        }
    }

    actual fun tap() {
        if (!enabled) return
        performVibration(level)
    }

    // 不检查 enabled:显式档位的调用点全在设置页(开关确认/滑动条预览),
    // 开关确认若走 enabled 门会被"写入→collect 传播"的毫秒级竞态拦掉
    actual fun tap(level: HapticFeedbackLevel) = performVibration(level)

    actual fun tapEmphasized() {
        if (!enabled) return
        performVibration(
            when (level) {
                HapticFeedbackLevel.LIGHT -> HapticFeedbackLevel.MEDIUM
                HapticFeedbackLevel.MEDIUM -> HapticFeedbackLevel.STRONG
                HapticFeedbackLevel.STRONG -> HapticFeedbackLevel.STRONG
            },
        )
    }

    private fun performVibration(level: HapticFeedbackLevel) {
        val context = appContext ?: return
        val vib = vibrator ?: return
        // 系统关闭触感反馈时静默(Settings 读取是 Provider 缓存快路径,点击频率下无感)
        val systemEnabled =
            runCatching {
                Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
            }.getOrDefault(true)
        if (!systemEnabled || !vib.hasVibrator()) return
        val effect =
            if (hasAmplitudeControl) {
                VibrationEffect.createOneShot(durationMs(level), amplitude(level))
            } else {
                VibrationEffect.createOneShot(durationMs(level), VibrationEffect.DEFAULT_AMPLITUDE)
            }
        runCatching { vib.vibrate(effect) }
    }

    // LRA 马达上幅值是主要感知维度,时长微增保证"档位差"在偏硬的马达上也能读出来
    private fun durationMs(level: HapticFeedbackLevel): Long =
        when (level) {
            HapticFeedbackLevel.LIGHT -> 12L
            HapticFeedbackLevel.MEDIUM -> 20L
            HapticFeedbackLevel.STRONG -> 32L
        }

    private fun amplitude(level: HapticFeedbackLevel): Int =
        when (level) {
            HapticFeedbackLevel.LIGHT -> 64
            HapticFeedbackLevel.MEDIUM -> 144
            HapticFeedbackLevel.STRONG -> 255
        }
}
