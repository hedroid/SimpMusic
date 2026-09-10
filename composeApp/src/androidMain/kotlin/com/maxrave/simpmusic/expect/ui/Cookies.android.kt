package com.maxrave.simpmusic.expect.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

actual fun createWebViewCookieManager(): WebViewCookieManager =
    object : WebViewCookieManager {
        override fun getCookie(url: String): String {
            val cookie = CookieManager.getInstance()
            return if (cookie.hasCookies()) {
                // The platform can hand back null for a URL with no cookies even though
                // other domains have some; callers treat this as a plain String.
                cookie.getCookie(url) ?: ""
            } else {
                ""
            }
        }

        override fun removeAllCookies() {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }

@Composable
actual fun PlatformWebView(
    state: MutableState<WebViewState>,
    initUrl: String,
    aboveContent: @Composable (BoxScope.() -> Unit),
    onPageFinished: (String) -> Unit,
) {
    Box {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams =
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )

                    // The login pages' Google/Facebook SSO buttons open their flows as popups
                    // (window.open / target=_blank). Without multi-window support the tap does
                    // nothing at all; with it, the popup URL is captured in onCreateWindow and
                    // loaded into this WebView so the OAuth flow stays in-app.
                    settings.setSupportMultipleWindows(true)

                    fun routeExternal(url: String) {
                        when {
                            url.startsWith("about:") -> Unit
                            else ->
                                runCatching {
                                    val intent =
                                        if (url.startsWith("intent:")) {
                                            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                                        } else {
                                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        }
                                    intent.addCategory(Intent.CATEGORY_BROWSABLE)
                                    context.startActivity(intent)
                                }
                        }
                    }

                    fun overrideForUrl(
                        url: String,
                        isForMainFrame: Boolean,
                    ): Boolean =
                        if (!isForMainFrame || url.startsWith("http")) {
                            false
                        } else {
                            // Deep links (fb://, intent:// to Google services, mailto:, ...) must
                            // never fall through to WebView's default handling: it calls
                            // startActivity itself and crashes the app with
                            // ActivityNotFoundException when nothing can handle the intent.
                            routeExternal(url)
                            true
                        }

                    webViewClient =
                        object : WebViewClient() {
                            override fun onPageFinished(
                                view: WebView?,
                                url: String?,
                            ) {
                                url?.let {
                                    onPageFinished(it)
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean = overrideForUrl(request.url.toString(), request.isForMainFrame)

                            @Deprecated("Deprecated in Java")
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                url: String,
                            ): Boolean = overrideForUrl(url, true)
                        }

                    webChromeClient =
                        object : WebChromeClient() {
                            override fun onCreateWindow(
                                view: WebView,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message,
                            ): Boolean {
                                val main = view
                                // Offscreen WebView whose only job is to hand us the popup's
                                // first navigation; every URL it sees is diverted into the
                                // main WebView (or an external app for non-http schemes).
                                val diverted = booleanArrayOf(false)
                                val divert =
                                    fun(v: WebView, url: String) {
                                        // Popups can start life on about:blank; wait for the
                                        // real navigation instead of consuming the one shot.
                                        if (diverted[0] || url.startsWith("about:")) return
                                        diverted[0] = true
                                        if (url.startsWith("http")) {
                                            main.loadUrl(url)
                                        } else {
                                            routeExternal(url)
                                        }
                                        v.post { v.destroy() }
                                    }
                                val popup =
                                    WebView(view.context).apply {
                                        webViewClient =
                                            object : WebViewClient() {
                                                override fun shouldOverrideUrlLoading(
                                                    v: WebView,
                                                    request: WebResourceRequest,
                                                ): Boolean {
                                                    if (request.isForMainFrame) divert(v, request.url.toString())
                                                    return true
                                                }

                                                @Deprecated("Deprecated in Java")
                                                override fun shouldOverrideUrlLoading(
                                                    v: WebView,
                                                    url: String,
                                                ): Boolean {
                                                    divert(v, url)
                                                    return true
                                                }

                                                override fun onPageStarted(
                                                    v: WebView,
                                                    url: String,
                                                    favicon: Bitmap?,
                                                ) {
                                                    // Fallback for popups whose initial navigation
                                                    // never consults shouldOverrideUrlLoading.
                                                    divert(v, url)
                                                }
                                            }
                                    }
                                (resultMsg.obj as WebView.WebViewTransport).webView = popup
                                resultMsg.sendToTarget()
                                return true
                            }
                        }

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true

                    loadUrl(initUrl)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        aboveContent()
    }
}

@Composable
actual fun DiscordWebView(
    state: MutableState<WebViewState>,
    aboveContent: @Composable (BoxScope.() -> Unit),
    onLoginDone: (String) -> Unit
) {
    val url = "https://discord.com/login"
    Box {
        AndroidView(factory = {
            WebView(it).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
                webViewClient = object : WebViewClient() {

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(
                        webView: WebView,
                        url: String,
                    ): Boolean {
                        stopLoading()
                        if (url.endsWith("/app")) {
                            loadUrl(JS_SNIPPET)
                        }
                        return false
                    }
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true

                if (android.os.Build.MANUFACTURER.equals(MOTOROLA, ignoreCase = true)) {
                    settings.userAgentString = SAMSUNG_USER_AGENT
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onJsAlert(
                        view: WebView,
                        url: String,
                        message: String,
                        result: JsResult,
                    ): Boolean {
                        onLoginDone(message)
                        return true
                    }
                }
                loadUrl(url)
            }
        })
        aboveContent()
    }
}

const val JS_SNIPPET =
    "javascript:(function()%7Bvar%20i%3Ddocument.createElement('iframe')%3Bdocument.body.appendChild(i)%3Balert(i.contentWindow.localStorage.token.slice(1,-1))%7D)()"
private const val MOTOROLA = "motorola"
private const val SAMSUNG_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; SM-S921U; Build/UP1A.231005.007) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.363"