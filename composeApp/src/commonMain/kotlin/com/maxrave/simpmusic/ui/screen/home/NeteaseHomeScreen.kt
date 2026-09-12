package com.maxrave.simpmusic.ui.screen.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.maxrave.simpmusic.ui.component.EndOfPage
import com.maxrave.simpmusic.ui.component.HomeItem
import com.maxrave.simpmusic.ui.component.HomeShimmer
import com.maxrave.simpmusic.ui.navigation.destination.login.NeteaseLoginDestination
import com.maxrave.simpmusic.extension.isScrollingUp
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.NeteaseHomeViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.log_in_to_netease
import simpmusic.composeapp.generated.resources.netease
import simpmusic.composeapp.generated.resources.netease_home_error
import simpmusic.composeapp.generated.resources.netease_home_logged_out
import simpmusic.composeapp.generated.resources.retry

/**
 * 网易云主页(M4):独立页,挂在 HomeDestination 下按 selected_source 与 YT 主页二选一。
 * 上游 HomeScreen 零改动;行渲染复用 AdapterItems.HomeItem(点歌单/点歌导航与播放同链路)。
 * 上游按 source 切换重建本页时 LaunchedEffect(Unit) 重新拉取 —— 懒刷新语义。
 */
@Composable
fun NeteaseHomeScreen(
    onScrolling: (onTop: Boolean) -> Unit = {},
    viewModel: NeteaseHomeViewModel = koinViewModel(),
    navController: NavController,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loggedIn by viewModel.loggedIn.collectAsStateWithLifecycle()
    val scrollState = rememberLazyListState()
    val isScrollingUp by scrollState.isScrollingUp()

    // 与上游 HomeScreen 同款:列表前两项视为"在顶部"(玻璃栏展开态)
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.firstVisibleItemIndex }
            .collect {
                if (it <= 1) {
                    onScrolling(true)
                } else {
                    onScrolling(isScrollingUp)
                }
            }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Crossfade(state) { current ->
            when (current) {
                is NeteaseHomeViewModel.State.Loading -> HomeShimmer()

                is NeteaseHomeViewModel.State.Error ->
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.netease_home_error),
                            style = typo().bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = viewModel::refresh) {
                            Text(stringResource(Res.string.retry))
                        }
                    }

                is NeteaseHomeViewModel.State.Ready -> {
                    LazyColumn(
                        state = scrollState,
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        item(key = "header") {
                            Column(
                                Modifier
                                    .statusBarsPadding()
                                    .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                            ) {
                                Text(
                                    text = stringResource(Res.string.netease),
                                    style = typo().headlineLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (!loggedIn) {
                                    Spacer(Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stringResource(Res.string.netease_home_logged_out),
                                            style = typo().bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        TextButton(onClick = { navController.navigate(NeteaseLoginDestination) }) {
                                            Text(stringResource(Res.string.log_in_to_netease))
                                        }
                                    }
                                }
                            }
                        }
                        items(
                            count = current.rows.size,
                            key = { index -> "netease_home_${index}_${current.rows[index].title}" },
                        ) { index ->
                            Box(Modifier.padding(horizontal = 15.dp)) {
                                HomeItem(
                                    navController = navController,
                                    data = current.rows[index],
                                )
                            }
                        }
                        item(key = "end") { EndOfPage() }
                    }
                }
            }
        }
    }
}
