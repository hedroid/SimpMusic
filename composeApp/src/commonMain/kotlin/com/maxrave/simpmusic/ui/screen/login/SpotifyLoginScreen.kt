package com.maxrave.simpmusic.ui.screen.login

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.maxrave.common.Config
import com.maxrave.simpmusic.expect.ui.PlatformWebView
import com.maxrave.simpmusic.expect.ui.createWebViewCookieManager
import com.maxrave.simpmusic.expect.ui.rememberWebViewState
import com.maxrave.simpmusic.extension.getStringBlocking
import com.maxrave.simpmusic.ui.component.DevCookieLogInBottomSheet
import com.maxrave.simpmusic.ui.component.DevLogInBottomSheet
import com.maxrave.simpmusic.ui.component.DevLogInType
import com.maxrave.simpmusic.ui.component.RippleIconButton
import com.maxrave.simpmusic.ui.icon.ArrowBackIosNew
import com.maxrave.simpmusic.ui.icon.Cookie
import com.maxrave.simpmusic.ui.icon.LogoDev
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.LogInViewModel
import com.maxrave.simpmusic.viewModel.SettingsViewModel
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.log_in_to_spotify
import simpmusic.composeapp.generated.resources.login_failed
import simpmusic.composeapp.generated.resources.login_success

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun SpotifyLoginScreen(
    innerPadding: PaddingValues,
    navController: NavController,
    viewModel: LogInViewModel = koinViewModel(),
    settingsViewModel: SettingsViewModel = koinViewModel(),
    hideBottomNavigation: () -> Unit,
    showBottomNavigation: () -> Unit,
) {
    val hazeState = rememberHazeState()
    val spotifyStatus by viewModel.spotifyStatus.collectAsStateWithLifecycle()

    val fullSpotifyCookies by viewModel.fullSpotifyCookies.collectAsStateWithLifecycle()

    var devLoginSheet by rememberSaveable {
        mutableStateOf(false)
    }

    var showCookiesBottomSheet by rememberSaveable {
        mutableStateOf(false)
    }

    // Hide bottom navigation when entering this screen
    LaunchedEffect(Unit) {
        hideBottomNavigation()
    }

    // Show bottom navigation when leaving this screen
    DisposableEffect(Unit) {
        onDispose {
            showBottomNavigation()
        }
    }

    // Handle login success
    LaunchedEffect(spotifyStatus) {
        if (spotifyStatus) {
            settingsViewModel.setSpotifyLogIn(true)
            viewModel.makeToast(getString(Res.string.login_success))
            navController.navigateUp()
        }
    }

    val state = rememberWebViewState()
    val cookieManager = createWebViewCookieManager()
    val captureScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState)) {
        Column {
            Spacer(
                Modifier
                    .size(
                        innerPadding.calculateTopPadding() + 64.dp,
                    ),
            )
            // WebView for Spotify login
            PlatformWebView(
                state,
                Config.SPOTIFY_LOG_IN_URL,
                aboveContent = {
                    FloatingActionButton(
                        onClick = {
                            showCookiesBottomSheet = true
                        },
                        containerColor = Color(0xFF40D96A),
                        modifier =
                            Modifier
                                .align(
                                    Alignment.BottomStart,
                                ).padding(innerPadding)
                                .padding(
                                    25.dp,
                                ),
                    ) {
                        Icon(
                            SimpIcons.Cookie,
                            "Cookies",
                        )
                    }
                    if (devLoginSheet) {
                        DevLogInBottomSheet(
                            onDismiss = {
                                devLoginSheet = false
                            },
                            onDone = { spdc ->
                                devLoginSheet = false
                                val spdcText = "sp_dc=$spdc"
                                viewModel.saveSpotifySpdc(spdcText)
                                viewModel.makeToast(getStringBlocking(Res.string.login_success))
                                navController.navigateUp()
                            },
                            type = DevLogInType.Spotify,
                        )
                    }

                    if (showCookiesBottomSheet) {
                        DevCookieLogInBottomSheet(
                            onDismiss = {
                                showCookiesBottomSheet = false
                            },
                            type = DevLogInType.Spotify,
                            cookies = fullSpotifyCookies,
                        )
                    }
                },
            ) { url ->
                val cookie = cookieManager.getCookie(url)
                cookie.takeIf {
                    it.isNotEmpty()
                }?.let { cookie ->
                    // Not every cookie pair is "k=v": values may contain '=' (base64 padding),
                    // and stray pairs without '=' must not blow up the whole parse.
                    val cookies =
                        cookie.split("; ").mapNotNull { pair ->
                            val separator = pair.indexOf('=')
                            if (separator > 0) {
                                pair.substring(0, separator) to pair.substring(separator + 1)
                            } else {
                                null
                            }
                        }
                    viewModel.setFullSpotifyCookies(cookies)
                }
                // Some login flows land on the status page with a query string appended
                // (e.g. /en/status?flow_ctx=...). Anchoring right after `status` made those
                // URLs fail the match silently, so sp_dc was never saved and auto-login
                // appeared to do nothing.
                val statusUrl = Regex("^https://accounts\\.spotify\\.com/(?:[^/]+/)?status(?:\\?.*)?$")
                if (statusUrl.matches(url)) {
                    captureScope.launch {
                        // The cookie jar can lag behind the finished navigation right after
                        // an OAuth redirect. Re-read briefly instead of wiping a session
                        // that was never captured — otherwise the login silently does
                        // nothing and the just-created session is destroyed too.
                        var cookieString = cookie
                        var attempts = 0
                        while (cookieString.isEmpty() && attempts < 10) {
                            delay(300)
                            cookieString = cookieManager.getCookie(url)
                            attempts++
                        }
                        if (cookieString.isNotEmpty()) {
                            viewModel.saveSpotifySpdc(cookieString)
                            cookieManager.removeAllCookies()
                        } else {
                            viewModel.makeToast(getStringBlocking(Res.string.login_failed))
                        }
                    }
                }
            }
        }

        // Top App Bar with haze effect
        TopAppBar(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .hazeEffect(state = hazeState, style = HazeMaterials.ultraThin()) {
                        blurEnabled = true
                    },
            title = {
                Text(
                    text = stringResource(Res.string.log_in_to_spotify),
                    style = typo().titleMedium,
                )
            },
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
                IconButton(
                    onClick = {
                        devLoginSheet = true
                    },
                ) {
                    Icon(
                        SimpIcons.LogoDev,
                        "Developer Mode",
                    )
                }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
        )
    }
}