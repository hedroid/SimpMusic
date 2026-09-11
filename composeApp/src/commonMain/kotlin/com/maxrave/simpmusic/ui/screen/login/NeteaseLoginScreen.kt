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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import com.maxrave.simpmusic.ui.component.RippleIconButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.maxrave.netease.NeteaseQrEncoder
import com.maxrave.simpmusic.expect.ui.PlatformWebView
import com.maxrave.simpmusic.expect.ui.createWebViewCookieManager
import com.maxrave.simpmusic.expect.ui.rememberWebViewState
import com.maxrave.simpmusic.ui.icon.ArrowBackIosNew
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
    LaunchedEffect(Unit) {
        viewModel.loginSuccess.collect {
            scope.launch {
                snackbarHostState.showSnackbar(getString(Res.string.login_success) + " · $it")
            }
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
            FilterChip(
                selected = method == NeteaseLoginViewModel.Method.COOKIE,
                onClick = { viewModel.setMethod(NeteaseLoginViewModel.Method.COOKIE) },
                label = { Text(stringResource(Res.string.netease_login_cookie)) },
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when (method) {
                NeteaseLoginViewModel.Method.QR -> QrMethod(qrContent, qrUi, loading) { viewModel.startQrLogin() }
                NeteaseLoginViewModel.Method.PHONE -> PhoneMethod(loading, captchaSent, viewModel)
                NeteaseLoginViewModel.Method.COOKIE -> CookieMethod(loading, viewModel)
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
    ) {
        if (loading && qrContent == null) {
            CircularProgressIndicator()
        } else if (qrContent != null) {
            val matrix = remember(qrContent) { NeteaseQrEncoder.encode(qrContent) }
            Canvas(modifier = Modifier.size(240.dp)) {
                val n = matrix.size
                val cell = size.minDimension / (n + 8f) // 4 模块静区
                val origin = (size.minDimension - cell * n) / 2f
                drawRect(color = androidx.compose.ui.graphics.Color.White, size = size)
                for (y in 0 until n) {
                    for (x in 0 until n) {
                        if (matrix[y][x]) {
                            drawRect(
                                color = androidx.compose.ui.graphics.Color.Black,
                                topLeft = androidx.compose.ui.geometry.Offset(origin + x * cell, origin + y * cell),
                                size = androidx.compose.ui.geometry.Size(cell, cell),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text =
                    when (qrUi) {
                        NeteaseLoginViewModel.QrUi.SCANNED -> stringResource(Res.string.netease_qr_scanned)
                        NeteaseLoginViewModel.QrUi.EXPIRED -> stringResource(Res.string.netease_qr_expired)
                        else -> stringResource(Res.string.netease_qr_waiting_scan)
                    },
                style = typo().bodyLarge,
            )
            if (qrUi == NeteaseLoginViewModel.QrUi.EXPIRED) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onRefresh) { Text(stringResource(Res.string.netease_qr_expired)) }
            }
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
    var password by rememberSaveable { mutableStateOf("") }
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
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(Res.string.netease_password)) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(
            onClick = { viewModel.loginByPhone(phone, password, countryCode) },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(Res.string.netease_login_by_password)) }

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
private fun CookieMethod(
    loading: Boolean,
    viewModel: NeteaseLoginViewModel,
) {
    var raw by rememberSaveable { mutableStateOf("") }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(Res.string.netease_cookie_hint),
            style = typo().bodyMedium,
        )
        OutlinedTextField(
            value = raw,
            onValueChange = { raw = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
        Button(
            onClick = { viewModel.loginByCookie(raw) },
            enabled = !loading && raw.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(Res.string.netease_login_cookie)) }
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
