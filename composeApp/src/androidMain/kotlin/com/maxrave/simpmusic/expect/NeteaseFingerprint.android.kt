package com.maxrave.simpmusic.expect

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import coil3.PlatformContext
import com.maxrave.netease.model.NeteaseFingerprint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/*
 * NeriPlayer NeteaseYdDeviceTokenProvider 的移植(GPL-3.0):
 * headless WebView → music.163.com(其页面注入 createNEFingerprint 指纹 SDK)
 * → 就绪轮询 → instance.getToken() 经 JS bridge 回传 → 连同 CookieManager 一起交出。
 */
private const val YD_URL = "https://music.163.com/"
private const val YD_APP_ID = "9d0ef7e0905d422cba1ecf7e73d77e67"
private const val YD_BRIDGE = "__SIMP_NETEASE_YD_BRIDGE__"
private const val YD_UA =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36 Edg/129.0.0.0"

actual suspend fun harvestNeteaseFingerprint(context: PlatformContext): NeteaseFingerprint? {
    val appContext = context as? Context ?: return null
    return runCatching {
        val pageLoaded = CompletableDeferred<Unit>()
        val tokenResult = CompletableDeferred<NeteaseFingerprint?>()

        withContext(Dispatchers.Main) {
            val webView =
                WebView(appContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = YD_UA
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webChromeClient = WebChromeClient()
                    webViewClient =
                        object : WebViewClient() {
                            override fun onPageFinished(
                                view: WebView?,
                                url: String?,
                            ) {
                                pageLoaded.complete(Unit)
                            }
                        }
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onToken(raw: String?) {
                                runCatching {
                                    val json = JSONObject(raw.orEmpty())
                                    val token = json.optString("token").orEmpty()
                                    val cookieMap = readCookieMap()
                                    tokenResult.complete(
                                        NeteaseFingerprint(
                                            ydToken = token,
                                            sDeviceId = cookieMap["sDeviceId"].orEmpty(),
                                            cookies = cookieMap,
                                        ),
                                    )
                                }.onFailure {
                                    if (!tokenResult.isCompleted) {
                                        tokenResult.complete(NeteaseFingerprint(cookies = readCookieMap()))
                                    }
                                }
                            }
                        },
                        YD_BRIDGE,
                    )
                    loadUrl(YD_URL)
                }

            try {
                withTimeoutOrNull(15_000) { pageLoaded.await() }
                // 轮询等待指纹 SDK 注入完成
                val ready =
                    withTimeoutOrNull(12_000) {
                        while (true) {
                            val raw =
                                webView.evaluateJavascriptSuspend(
                                    """
                                    (function() {
                                      try {
                                        return JSON.stringify({ready: typeof createNEFingerprint === 'function'});
                                      } catch (e) { return JSON.stringify({ready: false}); }
                                    })();
                                    """.trimIndent(),
                                )
                                val ok = runCatching { JSONObject(raw?.removeSurrounding("\"")?.replace("\\\"", "\"") ?: "").optBoolean("ready") }.getOrDefault(false)
                                if (ok) break
                                delay(400)
                        }
                        true
                    } == true

                if (ready) {
                    webView.evaluateJavascriptSuspend(
                        """
                        (function() {
                          try {
                            if (typeof createNEFingerprint !== 'function') {
                              window.$YD_BRIDGE.onToken(JSON.stringify({error: 'missing'}));
                              return;
                            }
                            createNEFingerprint({appId: '$YD_APP_ID', timeout: 6000}).getToken().then(function(r) {
                              window.$YD_BRIDGE.onToken(JSON.stringify({token: (r && r.token) || '', error: ''}));
                            }).catch(function(e) {
                              window.$YD_BRIDGE.onToken(JSON.stringify({error: String(e)}));
                            });
                          } catch (e) {
                            window.$YD_BRIDGE.onToken(JSON.stringify({error: String(e)}));
                          }
                        })();
                        """.trimIndent(),
                    )
                    withTimeoutOrNull(12_000) { tokenResult.await() } ?: NeteaseFingerprint(cookies = readCookieMap())
                } else {
                    NeteaseFingerprint(cookies = readCookieMap())
                }
            } finally {
                runCatching {
                    webView.removeJavascriptInterface(YD_BRIDGE)
                    webView.stopLoading()
                    webView.loadUrl("about:blank")
                    webView.destroy()
                }
            }
        }
    }.getOrNull()
}

private fun readCookieMap(): Map<String, String> {
    val raw = CookieManager.getInstance().getCookie(YD_URL).orEmpty()
    if (raw.isBlank()) return emptyMap()
    return raw.split(";")
        .map(String::trim)
        .filter { it.contains('=') }
        .associate {
            val idx = it.indexOf('=')
            it.substring(0, idx) to it.substring(idx + 1)
        }
}

private suspend fun WebView.evaluateJavascriptSuspend(script: String): String? =
    withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<String?>()
        evaluateJavascript(script) { value -> deferred.complete(value) }
        deferred.await()
    }
