package com.maxrave.simpmusic.extension

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [neteaseAccountIdentity] 的格式契约(2026-09-30 四轮 CR 补测):DataStore 的
 * neteaseCookie 是 JSON Map(NeteaseRepositoryImpl.persistCookies 落盘形状),
 * 不是 Cookie Header——首版按 ";"/"=" 切分恒返回 null,换号检测整体失效。
 */
class NeteaseAccountIdentityTest {
    @Test
    fun jsonFormatReturnsMusicU() {
        val cookie = """{"MUSIC_U":"abc123","__csrf":"tok","NMTID":"n1"}"""
        assertEquals("abc123", neteaseAccountIdentity(cookie))
    }

    @Test
    fun emptyStringReturnsNull() {
        assertNull(neteaseAccountIdentity(""))
    }

    @Test
    fun missingKeyReturnsNull() {
        assertNull(neteaseAccountIdentity("""{"__csrf":"tok","NMTID":"n1"}"""))
        assertNull(neteaseAccountIdentity("""{}"""))
    }

    @Test
    fun accountSwitchProducesDifferentIdentity() {
        val a = neteaseAccountIdentity("""{"MUSIC_U":"aaa","__csrf":"same"}""")
        val b = neteaseAccountIdentity("""{"MUSIC_U":"bbb","__csrf":"same"}""")
        // __csrf 等杂质不变、仅 MUSIC_U 变 = 换号,身份必须跟着变
        assertNotEquals(a, b)
        assertEquals("aaa", a)
        assertEquals("bbb", b)
    }

    @Test
    fun setCookieMergeDoesNotChangeIdentity() {
        // 同账号:运行期 Set-Cookie 合并改了杂质键,身份必须稳定(整串比对会误判换号)
        val before = neteaseAccountIdentity("""{"MUSIC_U":"aaa","__csrf":"old"}""")
        val after = neteaseAccountIdentity("""{"MUSIC_U":"aaa","__csrf":"new","NMTID":"x"}""")
        assertEquals(before, after)
    }

    @Test
    fun malformedReturnsNull() {
        assertNull(neteaseAccountIdentity("not a cookie at all"))
        // 非字符串值:isLenient 把它强制成字符串形态——对身份语义无害(按账号稳定即可,
        // persistCookies 落盘恒为字符串值,此形状实际不会出现)
        assertEquals("123", neteaseAccountIdentity("""{"MUSIC_U":123}"""))
    }

    @Test
    fun headerFormatFallbackStillWorks() {
        // 兜底路径:导入/历史的 Cookie Header 形状
        assertEquals("hdr", neteaseAccountIdentity("MUSIC_U=hdr; __csrf=tok"))
        assertTrue(neteaseAccountIdentity("__csrf=tok; NMTID=x") == null)
    }
}
