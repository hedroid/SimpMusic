package com.maxrave.simpmusic.extension

/**
 * 从网易 cookie 串提取 MUSIC_U 值作账号身份指纹(2026-09-30 三轮 CR):
 * 整串 cookie 不适合作身份——__csrf/NMTID 等随任意响应的 Set-Cookie 合并而变,
 * 同账号会被误判成"换号"清掉缓存列表;MUSIC_U 每账号恒定,只在换号/重登时变。
 * 返回 null=cookie 无 MUSIC_U(未登录/导入残缺),调用方按"身份未知"处理。
 */
internal fun neteaseAccountIdentity(cookie: String): String? =
    cookie
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
