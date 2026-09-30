package com.maxrave.simpmusic.extension

import kotlinx.serialization.json.Json

private val lenientJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * 从网易 cookie 持久值提取 MUSIC_U 作账号身份指纹(2026-09-30 三/四轮 CR):
 * 整串值不适合作身份——__csrf/NMTID 等随任意响应的 Set-Cookie 合并而变,同账号会被
 * 误判成"换号"清掉缓存列表;MUSIC_U 每账号恒定,只在换号/重登时变。
 *
 * **DataStore 的 neteaseCookie 是 JSON**(NeteaseRepositoryImpl.persistCookies 用
 * json.encodeToString(Map<String, String>) 落盘):{"MUSIC_U":"xxx","__csrf":"yyy"}。
 * 首版按 Cookie Header 的 ";"/"=" 切分解析,对 JSON 恒返回 null——看门狗永不识别
 * 换号、回包身份复核恒放行,防线整体失效(四轮 CR 实锤)。现主路径按 JSON Map 解码;
 * Cookie Header 格式("k=v; k2=v2")留作兜底(导入/历史形状防御);都失败=身份未知
 * 返回 null,调用方按"未知放行+后续变化沿再清"处理。
 */
internal fun neteaseAccountIdentity(cookie: String): String? {
    if (cookie.isEmpty()) return null
    runCatching { lenientJson.decodeFromString<Map<String, String>>(cookie) }
        .getOrNull()
        ?.get("MUSIC_U")
        ?.takeIf { it.isNotEmpty() }
        ?.let { return it }
    return cookie
        .split(';')
        .asSequence()
        .mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) {
                null
            } else {
                part.substring(0, idx).trim() to part.substring(idx + 1).trim()
            }
        }.firstOrNull { (k, _) -> k == "MUSIC_U" }
        ?.second
        ?.takeIf { it.isNotEmpty() }
}
