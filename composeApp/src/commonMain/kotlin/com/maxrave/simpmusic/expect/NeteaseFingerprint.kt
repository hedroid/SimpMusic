package com.maxrave.simpmusic.expect

import coil3.PlatformContext
import com.maxrave.netease.model.NeteaseFingerprint

/**
 * 易盾设备指纹收割(NeriPlayer NeteaseYdDeviceTokenProvider 的跨平台入口):
 * Android 用 headless WebView 加载 music.163.com,等待 createNEFingerprint 就绪后取 token
 * 并收割 CookieManager 会话;其余平台返回 null(扫码走无指纹降级,易被风控)。
 */
expect suspend fun harvestNeteaseFingerprint(context: PlatformContext): NeteaseFingerprint?
