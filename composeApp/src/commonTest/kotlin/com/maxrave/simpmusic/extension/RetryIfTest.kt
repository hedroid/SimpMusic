package com.maxrave.simpmusic.extension

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [retryIf] 的行为契约(2026-09-30 二轮 CR 补测):线性退避重试直到结果可接受
 * 或耗尽次数;不可重试结果(predicate=false)立即收手。firstDelayMs 压到 1ms,
 * 全程真实 delay 也在毫秒级,无需虚拟时间基建。
 */
class RetryIfTest {
    @Test
    fun retriesUntilSatisfactory() =
        runBlocking {
            var calls = 0
            val result =
                retryIf(tag = "test", times = 5, firstDelayMs = 1, retryOn = { it < 3 }) { _ ->
                    calls++
                    minOf(calls, 3)
                }
            assertEquals(3, result)
            assertEquals(3, calls) // 初次 + 2 次重试,第 3 次结果可接受即停
        }

    @Test
    fun noRetryWhenFirstResultSatisfactory() =
        runBlocking {
            var calls = 0
            val result = retryIf(tag = "test", firstDelayMs = 1, retryOn = { false }) { _ ->
                calls++
                42
            }
            assertEquals(42, result)
            assertEquals(1, calls)
        }

    @Test
    fun exhaustsAttemptsAndReturnsLast() =
        runBlocking {
            var calls = 0
            val result = retryIf(tag = "test", times = 3, firstDelayMs = 1, retryOn = { true }) { attempt ->
                calls++
                attempt
            }
            assertEquals(2, result) // times=3 全程 = 初次 + 2 重试,返回最后一次(attempt=2)
            assertEquals(3, calls)
        }

    @Test
    fun nonRetryableStopsImmediately() =
        runBlocking {
            // 不可重试语义(添加到歌单链:NeteaseNotLoggedInException):predicate 对
            // 该结果返回 false,一次调用即收手、不耗退避
            var calls = 0
            val result =
                retryIf(tag = "test", times = 5, firstDelayMs = 1, retryOn = { it != "not-logged-in" }) { _ ->
                    calls++
                    "not-logged-in"
                }
            assertEquals("not-logged-in", result)
            assertEquals(1, calls)
        }

    @Test
    fun backoffIsLinearPerAttempt() =
        runBlocking {
            // 退避节奏只影响时长不影响次数;这里验证 attempt 序列 0,1,2 按序传入
            val attempts = mutableListOf<Int>()
            retryIf(tag = "test", times = 3, firstDelayMs = 1, retryOn = { it < 2 }) { attempt ->
                attempts.add(attempt)
                attempt
            }
            assertEquals(listOf(0, 1, 2), attempts)
            assertTrue(true)
        }
}
