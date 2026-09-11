package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maxrave.data.repository.NeteaseRepositoryImpl
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.logger.Logger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 网易云登录(YTM 登录页同款形态:全屏 WebView)。
 * 网页自带扫码/手机号/滑块验证,这里只负责:Cookie 弹框粘贴 / 网页 Cookie 捕获,
 * 两者汇入同一个校验出口 [NeteaseRepositoryImpl.saveLoginCookies]。
 */
class NeteaseLoginViewModel(
    private val neteaseRepository: NeteaseRepositoryImpl,
    private val dataStoreManager: DataStoreManager,
) : ViewModel() {
    private val client get() = neteaseRepository.client

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    /** 登录成功信号,Screen 侧 toast + 返回 */
    private val _loginSuccess = MutableSharedFlow<String?>(extraBufferCapacity = 1)
    val loginSuccess: SharedFlow<String?> = _loginSuccess

    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message

    /** Cookie 弹框粘贴:支持 MUSIC_U 裸值或完整 cookie 串 */
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
                        if (name.isEmpty() || value.isEmpty()) null else name to value
                    }.toMap()
            finishLogin(cookies)
            _loading.value = false
        }
    }

    private suspend fun finishLogin(cookies: Map<String, String>) {
        Logger.d(TAG, "finishLogin: cookie keys=${cookies.keys}")
        neteaseRepository.saveLoginCookies(cookies)
            .onSuccess { account ->
                Logger.d(TAG, "finishLogin: success account=${account?.nickname}")
                dataStoreManager.setSelectedSource(com.maxrave.domain.source.MusicSource.NETEASE.name)
                _loginSuccess.emit(account?.nickname)
            }.onFailure {
                Logger.e(TAG, "finishLogin failed", it)
                _message.emit(it.message ?: "登录校验失败")
            }
    }

    private companion object {
        const val TAG = "NeteaseLogin"
    }
}
