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
    enum class Method { QR, PHONE, WEB }

    enum class QrUi { IDLE, LOADING, WAITING_SCAN, SCANNED, EXPIRED, LOGGED_IN }

    private val client get() = neteaseRepository.client

    /** 扫码专用会话(NeriPlayer 架构):独立 client + 内存 cookie,803 内部完成验证 */
    private val qrSession = NeteaseQrLoginSession()

    private val _method = MutableStateFlow(Method.QR)
    val method: StateFlow<Method> = _method

    fun setMethod(method: Method) {
        if (method != Method.QR) pollJob?.cancel() // 离开扫码页即停轮询
        _method.value = method
    }

    private val _qrContent = MutableStateFlow<String?>(null)
    val qrContent: StateFlow<String?> = _qrContent

    private val _qrUi = MutableStateFlow(QrUi.IDLE)
    val qrUi: StateFlow<QrUi> = _qrUi

    private val _phoneMessage = MutableSharedFlow<String>()
    val phoneMessage: SharedFlow<String> = _phoneMessage

    private val _captchaSent = MutableStateFlow(false)
    val captchaSent: StateFlow<Boolean> = _captchaSent

    /** -462 风控的滑块验证页;非空时手机号页显示 WebView,验证完成自动重试登录 */
    private val _verifyUrl = MutableStateFlow<String?>(null)
    val verifyUrl: StateFlow<String?> = _verifyUrl
    private var pendingCaptchaLogin: Triple<String, String, String>? = null
    private var verifyRetried = false

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    /** 登录成功信号,Screen 侧 toast + 返回 */
    private val _loginSuccess = MutableSharedFlow<String?>(extraBufferCapacity = 1)
    val loginSuccess: SharedFlow<String?> = _loginSuccess

    private var pollJob: Job? = null

    // ---------------------------------------------------------------- QR

    fun startQrLogin() {
        pollJob?.cancel()
        qrSession.reset()
        _qrUi.value = QrUi.LOADING
        viewModelScope.launch {
            qrSession.createSession()
                .onSuccess { session ->
                    _qrContent.value = session.qrContent
                    _qrUi.value = QrUi.WAITING_SCAN
                    poll(session.key)
                }.onFailure {
                    _qrUi.value = QrUi.EXPIRED
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
                                is NeteaseQrStatus.ScannedWaitingForConfirm -> _qrUi.value = QrUi.SCANNED
                                is NeteaseQrStatus.Expired -> {
                                    _qrUi.value = QrUi.EXPIRED
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

    fun sendCaptcha(
        phone: String,
        countryCode: String,
    ) {
        if (phone.isBlank()) return
        _loading.value = true
        viewModelScope.launch {
            client.sendCaptcha(phone, countryCode)
                .onSuccess {
                    _captchaSent.value = true
                    _phoneMessage.emit("验证码已发送")
                }.onFailure { _phoneMessage.emit(it.message ?: "captcha failed") }
            _loading.value = false
        }
    }

    fun loginByCaptcha(
        phone: String,
        captcha: String,
        countryCode: String,
    ) {
        if (phone.isBlank() || captcha.isBlank()) return
        pendingCaptchaLogin = Triple(phone, captcha, countryCode)
        verifyRetried = false
        _loading.value = true
        viewModelScope.launch {
            client.loginByCaptcha(phone, captcha, countryCode)
                .onSuccess { body ->
                    val code = (body["code"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    val verifyUrl =
                        (
                            (body["data"] as? kotlinx.serialization.json.JsonObject)
                                ?.get("verifyUrl") as? kotlinx.serialization.json.JsonPrimitive
                        )?.content.orEmpty()
                    if (code == "-462" && verifyUrl.isNotBlank()) {
                        Logger.d(TAG, "risk control -462, showing verify page")
                        _verifyUrl.value = verifyUrl
                        _phoneMessage.emit("请完成安全验证后自动继续")
                    } else {
                        finishLoginFromBody(body)
                    }
                }.onFailure { _phoneMessage.emit(it.message ?: "login failed") }
            _loading.value = false
        }
    }

    /** 滑块验证页回调:收割 WebView 会话 cookie 喂给主 client,自动重试登录 */
    fun onVerifyPageFinished(rawCookie: String) {
        if (verifyRetried) return
        val pending = pendingCaptchaLogin ?: return
        val parsed =
            rawCookie.split(";")
                .mapNotNull { part ->
                    val name = part.substringBefore('=', "").trim()
                    val value = part.substringAfter('=', "").trim()
                    if (name.isEmpty() || value.isEmpty()) null else name to value
                }.toMap()
        if (parsed.isEmpty()) return
        verifyRetried = true
        viewModelScope.launch {
            client.seedCookies(client.currentCookies() + parsed)
            _verifyUrl.value = null
            _loading.value = true
            client.loginByCaptcha(pending.first, pending.second, pending.third)
                .onSuccess { finishLoginFromBody(it) }
                .onFailure { _phoneMessage.emit(it.message ?: "login failed") }
            _loading.value = false
        }
    }

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
