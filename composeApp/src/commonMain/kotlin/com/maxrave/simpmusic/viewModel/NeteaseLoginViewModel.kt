package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.ViewModel
import com.maxrave.logger.Logger
import androidx.lifecycle.viewModelScope
import com.maxrave.data.repository.NeteaseRepositoryImpl
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.netease.NeteaseQrLoginSession
import com.maxrave.netease.model.NeteaseQrStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * 网易云登录:四种方式(扫码/手机号/网页/Cookie 粘贴)共享一个成功出口 —— 拿到含 MUSIC_U
 * 的 cookie 集合后走 [NeteaseRepositoryImpl.saveLoginCookies] 校验落盘。
 */
class NeteaseLoginViewModel(
    private val neteaseRepository: NeteaseRepositoryImpl,
    private val dataStoreManager: DataStoreManager,
) : ViewModel() {
    enum class Method { QR, WEB }

    enum class QrUi { IDLE, LOADING, WAITING_SCAN, SCANNED, EXPIRED, RISK, LOGGED_IN }

    private val client get() = neteaseRepository.client

    /** 扫码专用会话(NeriPlayer 架构):独立 client + 内存 cookie,803 内部完成验证 */
    private val qrSession = NeteaseQrLoginSession()

    private val _method = MutableStateFlow(Method.QR)
    val method: StateFlow<Method> = _method

    fun setMethod(method: Method) {
        if (method != Method.QR) {
            pollJob?.cancel() // 离开扫码页即停轮询
        } else if (_method.value != Method.QR) {
            // 切回扫码页:二维码还在屏幕上但轮询已被停掉 —— 恢复它,否则"扫了没反应"
            val key = currentQrKey
            if (key != null && _qrUi.value in listOf(QrUi.WAITING_SCAN, QrUi.SCANNED)) {
                Logger.d(TAG, "re-enter QR tab, resume polling")
                poll(key)
            }
        }
        _method.value = method
    }

    private val _qrContent = MutableStateFlow<String?>(null)
    val qrContent: StateFlow<String?> = _qrContent

    private val _qrUi = MutableStateFlow(QrUi.IDLE)
    val qrUi: StateFlow<QrUi> = _qrUi

    private val _phoneMessage = MutableSharedFlow<String>()
    val phoneMessage: SharedFlow<String> = _phoneMessage



    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    /** 登录成功信号,Screen 侧 toast + 返回 */
    private val _loginSuccess = MutableSharedFlow<String?>(extraBufferCapacity = 1)
    val loginSuccess: SharedFlow<String?> = _loginSuccess

    private var pollJob: Job? = null
    private var qrAutoRefreshCount = 0
    private var currentQrKey: String? = null
    private var lastFingerprint: com.maxrave.netease.model.NeteaseFingerprint? = null

    // ---------------------------------------------------------------- QR

    /** 收割指纹期间由 Screen 调用:卡片先转圈,避免数秒空白被当成"二维码出不来" */
    fun beginQrLoading() {
        _loading.value = true
        _qrUi.value = QrUi.LOADING
    }

    fun startQrLogin(fingerprint: com.maxrave.netease.model.NeteaseFingerprint? = null) {
        val fp = fingerprint ?: lastFingerprint
        pollJob?.cancel()
        qrSession.reset()
        qrSession.setFingerprint(fp)
        if (fingerprint != null) lastFingerprint = fingerprint
        _qrUi.value = QrUi.LOADING
        viewModelScope.launch {
            qrSession.createSession()
                .onSuccess { session ->
                    _qrContent.value = session.qrContent
                    _qrUi.value = QrUi.WAITING_SCAN
                    _loading.value = false
                    currentQrKey = session.key
                    poll(session.key)
                }.onFailure {
                    _qrUi.value = QrUi.EXPIRED
                    _loading.value = false
                    _phoneMessage.emit(it.message ?: "QR session failed")
                }
        }
    }

    private fun poll(key: String) {
        pollJob =
            viewModelScope.launch {
                while (true) {
                    delay(3000)
                    qrSession.checkLogin(key)
                        .onSuccess { status ->
                            when (status) {
                                is NeteaseQrStatus.WaitingForScan -> _qrUi.value = QrUi.WAITING_SCAN
                                is NeteaseQrStatus.ScannedWaitingForConfirm -> {
                                    qrAutoRefreshCount = 0 // 用户已扫码,会话有效
                                    _qrUi.value = QrUi.SCANNED
                                }
                                is NeteaseQrStatus.RiskControl -> {
                                    // -462:换码无效,明确引导网页登录,不再自动刷新
                                    _qrUi.value = QrUi.RISK
                                    return@launch
                                }
                                is NeteaseQrStatus.Expired -> {
                                    _qrUi.value = QrUi.EXPIRED
                                    // 800:自动换码最多 3 次
                                    if (qrAutoRefreshCount < 3) {
                                        qrAutoRefreshCount++
                                        Logger.d(TAG, "QR expired, auto refresh #$qrAutoRefreshCount")
                                        startQrLogin(lastFingerprint)
                                    }
                                    return@launch
                                }
                                is NeteaseQrStatus.Confirmed -> {
                                    val finalCookies = status.cookies
                                    // 独立协程执行收尾:轮询 job 的任何取消/异常都不再牵连登录流程
                                    viewModelScope.launch { finishLogin(finalCookies) }
                                    return@launch
                                }
                            }
                        }
                }
            }
    }

    // ---------------------------------------------------------------- phone

    /**
     * 手机号登录的 cookie 同时出现在响应体 cookie 字段与 Set-Cookie 头;client 已合并头部,
     * 这里再兜底合并 body 里的,然后统一校验。
     */
    private suspend fun finishLoginFromBody(body: kotlinx.serialization.json.JsonObject) {
        val bodyCookie = (body["cookie"] as? JsonPrimitive)?.content ?: ""
        val extra =
            bodyCookie.split(";")
                .mapNotNull { part ->
                    val name = part.substringBefore('=', "").trim()
                    val value = part.substringAfter('=', "").trim()
                    if (name.isEmpty()) null else name to value
                }.toMap()
        finishLogin(client.currentCookies() + extra)
    }

    // ---------------------------------------------------------------- cookie paste / web

    fun loginByCookie(raw: String) {
        if (raw.isBlank()) return
        _loading.value = true
        viewModelScope.launch {
            finishLogin(client.parseRawCookies(raw))
            _loading.value = false
        }
    }

    /** 网页登录 onPageFinished 回调:命中 MUSIC_U 即视为登录完成 */
    fun onWebCookies(rawCookie: String) {
        if (!rawCookie.contains("MUSIC_U")) return
        if (_loading.value) return
        _loading.value = true
        viewModelScope.launch {
            val cookies =
                rawCookie.split(";")
                    .mapNotNull { part ->
                        val name = part.substringBefore('=', "").trim()
                        val value = part.substringAfter('=', "").trim()
                        if (name.isEmpty()) null else name to value
                    }.toMap()
            finishLogin(cookies)
            _loading.value = false
        }
    }

    // ---------------------------------------------------------------- shared exit

    private suspend fun finishLogin(cookies: Map<String, String>) {
        pollJob?.cancel()
        Logger.d(TAG, "finishLogin: cookie keys=${cookies.keys}")
        neteaseRepository.saveLoginCookies(cookies)
            .onSuccess { account ->
                Logger.d(TAG, "finishLogin: success account=${account?.nickname}")
                _qrUi.value = QrUi.LOGGED_IN
                dataStoreManager.setSelectedSource(com.maxrave.domain.source.MusicSource.NETEASE.name)
                _loginSuccess.emit(account?.nickname)
            }.onFailure {
                Logger.e(TAG, "finishLogin failed", it)
                _phoneMessage.emit(it.message ?: "登录校验失败")
            }
    }

    private companion object {
        const val TAG = "NeteaseLogin"
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }
}
