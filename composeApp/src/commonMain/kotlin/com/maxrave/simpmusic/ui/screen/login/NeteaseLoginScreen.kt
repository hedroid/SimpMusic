package com.maxrave.simpmusic.ui.screen.login

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import com.maxrave.simpmusic.ui.component.DevLogInBottomSheet
import com.maxrave.simpmusic.ui.component.DevLogInType
import com.maxrave.simpmusic.ui.component.RippleIconButton
import multiplatform.network.cmptoast.ToastGravity
import multiplatform.network.cmptoast.showToast
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.maxrave.netease.NeteaseQrEncoder
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import simpmusic.composeapp.generated.resources.netease_qr_title
import simpmusic.composeapp.generated.resources.netease_qr_subtitle
import simpmusic.composeapp.generated.resources.netease_qr_refresh
import simpmusic.composeapp.generated.resources.netease_qr_use_web
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import com.maxrave.simpmusic.expect.ui.PlatformWebView
import com.maxrave.simpmusic.expect.ui.createWebViewCookieManager
import com.maxrave.simpmusic.expect.ui.rememberWebViewState
import com.maxrave.simpmusic.ui.icon.ArrowBackIosNew
import com.maxrave.simpmusic.ui.icon.LogoDev
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.NeteaseLoginViewModel
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.login_success
import simpmusic.composeapp.generated.resources.netease_cookie_hint
import simpmusic.composeapp.generated.resources.netease_login_by_captcha
import simpmusic.composeapp.generated.resources.netease_login_by_password
import simpmusic.composeapp.generated.resources.netease_login_cookie
import simpmusic.composeapp.generated.resources.netease_login_phone
import simpmusic.composeapp.generated.resources.netease_login_qr
import simpmusic.composeapp.generated.resources.netease_login_to_netease
import simpmusic.composeapp.generated.resources.netease_login_web
import simpmusic.composeapp.generated.resources.netease_password
import simpmusic.composeapp.generated.resources.netease_phone_number
import simpmusic.composeapp.generated.resources.netease_qr_expired
import simpmusic.composeapp.generated.resources.netease_qr_scanned
import simpmusic.composeapp.generated.resources.netease_qr_waiting_scan
import simpmusic.composeapp.generated.resources.netease_captcha
import simpmusic.composeapp.generated.resources.netease_send_captcha

private const val NETEASE_WEB_LOGIN_URL = "https://music.163.com/#/login"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeteaseLoginScreen(
    innerPadding: PaddingValues,
    navController: NavController,
    viewModel: NeteaseLoginViewModel = koinViewModel(),
    hideBottomNavigation: () -> Unit,
    showBottomNavigation: () -> Unit,
) {
    val method by viewModel.method.collectAsStateWithLifecycle()
    val qrContent by viewModel.qrContent.collectAsStateWithLifecycle()
    val qrUi by viewModel.qrUi.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val captchaSent by viewModel.captchaSent.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { hideBottomNavigation() }
    DisposableEffect(Unit) {
        onDispose { showBottomNavigation() }
    }

    LaunchedEffect(Unit) {
        viewModel.phoneMessage.collect { scope.launch { snackbarHostState.showSnackbar(it) } }
    }
    var devLoginSheet by rememberSaveable { mutableStateOf(false) }
    if (devLoginSheet) {
        DevLogInBottomSheet(
            onDismiss = { devLoginSheet = false },
            type = DevLogInType.NetEase,
            onDone = { cookie ->
                devLoginSheet = false
                viewModel.loginByCookie(cookie)
            },
        )
    }

    LaunchedEffect(Unit) {
        viewModel.loginSuccess.collect {
            // toast 在跳转后依然可见;snackbar 会随页面离开而消失
            showToast(getString(Res.string.login_success) + " · $it", ToastGravity.Bottom)
            navController.navigateUp()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(Res.string.netease_login_to_netease)) },
            navigationIcon = {
                Box(Modifier.padding(horizontal = 5.dp)) {
                    RippleIconButton(
                        SimpIcons.ArrowBackIosNew,
                        Modifier.size(32.dp),
                        true,
                    ) {
                        navController.navigateUp()
                    }
                }
            },
            actions = {
                // 与 Spotify/YouTube 登录页一致:右上角开发者小按钮 → 底部弹框粘贴 Cookie
                IconButton(onClick = { devLoginSheet = true }) {
                    Icon(SimpIcons.LogoDev, "Developer Mode")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            FilterChip(
                selected = method == NeteaseLoginViewModel.Method.QR,
                onClick = { viewModel.setMethod(NeteaseLoginViewModel.Method.QR) },
                label = { Text(stringResource(Res.string.netease_login_qr)) },
            )
            FilterChip(
                selected = method == NeteaseLoginViewModel.Method.PHONE,
                onClick = { viewModel.setMethod(NeteaseLoginViewModel.Method.PHONE) },
                label = { Text(stringResource(Res.string.netease_login_phone)) },
            )
            FilterChip(
                selected = method == NeteaseLoginViewModel.Method.WEB,
                onClick = { viewModel.setMethod(NeteaseLoginViewModel.Method.WEB) },
                label = { Text(stringResource(Res.string.netease_login_web)) },
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when (method) {
                NeteaseLoginViewModel.Method.QR ->
                    QrMethod(
                        qrContent = qrContent,
                        qrUi = qrUi,
                        loading = loading,
                        onRefresh = { viewModel.startQrLogin() }
                    )
                NeteaseLoginViewModel.Method.PHONE -> PhoneMethod(loading, captchaSent, viewModel)
                NeteaseLoginViewModel.Method.WEB -> WebMethod(innerPadding, viewModel)
            }
            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun QrMethod(
    qrContent: String?,
    qrUi: NeteaseLoginViewModel.QrUi,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    LaunchedEffect(Unit) { if (qrContent == null) onRefresh() }
    val neteaseRed = Color(0xFFC72535)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(Res.string.netease_qr_title),
            style = typo().headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(Res.string.netease_qr_subtitle),
            style = typo().bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))

        // 白底圆角卡片装二维码(NeriPlayer 同款 28dp 圆角 + 内衬)
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(240.dp)
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White)
                    .padding(12.dp),
        ) {
            if (qrContent != null) {
                val matrix = remember(qrContent) { NeteaseQrEncoder.encode(qrContent) }
                Canvas(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .alpha(if (qrUi == NeteaseLoginViewModel.QrUi.EXPIRED) 0.35f else 1f),
                ) {
                    val n = matrix.size
                    val cell = size.minDimension / n
                    for (y in 0 until n) {
                        for (x in 0 until n) {
                            if (matrix[y][x]) {
                                drawRect(
                                    color = Color.Black,
                                    topLeft = androidx.compose.ui.geometry.Offset(x * cell, y * cell),
                                    size = androidx.compose.ui.geometry.Size(cell, cell),
                                )
                            }
                        }
                    }
                }
            }
            if (loading && qrContent == null) {
                CircularProgressIndicator(color = neteaseRed, modifier = Modifier.size(42.dp))
            }
        }

        Spacer(Modifier.height(14.dp))
        // 状态胶囊:网易红文字 + 11% 红底
        Text(
            text =
                when (qrUi) {
                    NeteaseLoginViewModel.QrUi.SCANNED -> stringResource(Res.string.netease_qr_scanned)
                    NeteaseLoginViewModel.QrUi.EXPIRED -> stringResource(Res.string.netease_qr_expired)
                    NeteaseLoginViewModel.QrUi.LOGGED_IN -> stringResource(Res.string.login_success)
                    else -> stringResource(Res.string.netease_qr_waiting_scan)
                },
            style = typo().labelLarge,
            color = neteaseRed,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(neteaseRed.copy(alpha = 0.11f))
                    .padding(horizontal = 16.dp, vertical = 9.dp),
        )
        Spacer(Modifier.height(16.dp))

        if (qrUi == NeteaseLoginViewModel.QrUi.EXPIRED) {
            Button(
                onClick = onRefresh,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = neteaseRed),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                modifier = Modifier.fillMaxWidth(0.72f),
            ) { Text(stringResource(Res.string.netease_qr_refresh)) }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun PhoneMethod(
    loading: Boolean,
    captchaSent: Boolean,
    viewModel: NeteaseLoginViewModel,
) {
    var phone by rememberSaveable { mutableStateOf("") }
    var countryCode by rememberSaveable { mutableStateOf("86") }
    var captcha by rememberSaveable { mutableStateOf("") }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = countryCode,
                onValueChange = { countryCode = it.take(4) },
                label = { Text("+") },
                modifier = Modifier.size(width = 88.dp, height = 64.dp),
                singleLine = true,
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text(stringResource(Res.string.netease_phone_number)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        OutlinedTextField(
            value = captcha,
            onValueChange = { captcha = it },
            label = { Text(stringResource(Res.string.netease_captcha)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { viewModel.sendCaptcha(phone, countryCode) },
                enabled = !loading && !captchaSent,
            ) { Text(stringResource(Res.string.netease_send_captcha)) }
            Button(
                onClick = { viewModel.loginByCaptcha(phone, captcha, countryCode) },
                enabled = !loading,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(Res.string.netease_login_by_captcha)) }
        }
    }
}

@Composable
private fun WebMethod(
    innerPadding: PaddingValues,
    viewModel: NeteaseLoginViewModel,
) {
    val state = rememberWebViewState()
    val cookieManager = createWebViewCookieManager()
    Box(modifier = Modifier.fillMaxSize()) {
        PlatformWebView(
            state = state,
            initUrl = NETEASE_WEB_LOGIN_URL,
        ) { url ->
            // 登录跳转回首页时 cookie 才落全;只要 MUSIC_U 出现就收网
            val cookies = cookieManager.getCookie("https://music.163.com")
            if (cookies.contains("MUSIC_U")) {
                viewModel.onWebCookies(cookies)
            }
        }
    }
}
