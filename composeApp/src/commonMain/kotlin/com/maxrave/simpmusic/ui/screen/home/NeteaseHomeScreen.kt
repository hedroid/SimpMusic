package com.maxrave.simpmusic.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.maxrave.simpmusic.extension.isScrollingUp
import com.maxrave.simpmusic.ui.component.Chip
import com.maxrave.simpmusic.ui.component.EndOfPage
import com.maxrave.simpmusic.ui.component.HomeItem
import com.maxrave.simpmusic.ui.component.HomeShimmer
import com.maxrave.simpmusic.ui.navigation.destination.login.NeteaseLoginDestination
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.NeteaseHomeViewModel
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.all
import simpmusic.composeapp.generated.resources.log_in_to_netease
import simpmusic.composeapp.generated.resources.netease_home_error
import simpmusic.composeapp.generated.resources.netease_home_logged_out
import simpmusic.composeapp.generated.resources.retry

/**
 * 网易云主页(M4):独立页,挂在 HomeDestination 下按 selected_source 与 YT 主页二选一。
 * 骨架逐行对照上游 HomeScreen —— 悬浮顶栏(上滑收起/下滑展开/滚动高斯模糊)+
 * 分类 chips(网易高质量标签)+ PullToRefresh + 内容列表,仅 feed 数据换成网易。
 * 切源重建本页时 LaunchedEffect(Unit) 重新拉取 —— 懒刷新语义。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun NeteaseHomeScreen(
    onScrolling: (onTop: Boolean) -> Unit = {},
    viewModel: NeteaseHomeViewModel = koinViewModel(),
    navController: NavController,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loggedIn by viewModel.loggedIn.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val selectedTag by viewModel.selectedTag.collectAsStateWithLifecycle()
    val scrollState = rememberLazyListState()
    val isScrollingUp by scrollState.isScrollingUp()
    val hazeState = rememberHazeState(blurEnabled = true)
    val pullToRefreshState = rememberPullToRefreshState()
    val chipRowState = rememberScrollState()
    var topAppBarHeightPx by remember { mutableIntStateOf(0) }

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

    val onRefresh: () -> Unit = viewModel::refresh

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        PullToRefreshBox(
            modifier =
                Modifier
                    .hazeSource(hazeState),
            state = pullToRefreshState,
            onRefresh = onRefresh,
            isRefreshing = state is NeteaseHomeViewModel.State.Loading,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = state is NeteaseHomeViewModel.State.Loading,
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(
                                top =
                                    with(LocalDensity.current) {
                                        topAppBarHeightPx.toDp()
                                    },
                            ),
                    containerColor = PullToRefreshDefaults.indicatorContainerColor,
                    color = PullToRefreshDefaults.indicatorColor,
                    maxDistance = PullToRefreshDefaults.PositionalThreshold,
                )
            },
        ) {
            Crossfade(state) { current ->
                when (current) {
                    is NeteaseHomeViewModel.State.Loading ->
                        Column {
                            Spacer(
                                Modifier.height(
                                    with(LocalDensity.current) {
                                        topAppBarHeightPx.toDp()
                                    },
                                ),
                            )
                            HomeShimmer()
                        }

                    is NeteaseHomeViewModel.State.Error ->
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = stringResource(Res.string.netease_home_error),
                                style = typo().bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!loggedIn) {
                                Text(
                                    text = stringResource(Res.string.netease_home_logged_out),
                                    style = typo().bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = { navController.navigate(NeteaseLoginDestination) }) {
                                    Text(stringResource(Res.string.log_in_to_netease))
                                }
                            }
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
                            item(key = "top-space") {
                                Spacer(
                                    Modifier.height(
                                        with(LocalDensity.current) {
                                            topAppBarHeightPx.toDp()
                                        },
                                    ),
                                )
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
        // 悬浮顶栏 + 分类 chips:逐行对照上游(滚动后模糊,上滑收起顶栏但 chips 常驻)
        AnimatedContent(
            targetState = scrollState.firstVisibleItemIndex == 0 && scrollState.firstVisibleItemScrollOffset == 0,
            transitionSpec = {
                fadeIn(tween(300)).togetherWith(fadeOut(tween(300)))
            },
        ) { target ->
            Column(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .then(
                            if (target) {
                                Modifier.background(Color.Transparent)
                            } else {
                                Modifier
                                    .hazeEffect(hazeState, style = HazeMaterials.ultraThin()) {
                                        blurEnabled = true
                                    }
                            },
                        ).onGloballyPositioned { coordinates ->
                            topAppBarHeightPx = coordinates.size.height
                        },
            ) {
                AnimatedVisibility(
                    visible = isScrollingUp,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    HomeTopAppBar(navController = navController)
                }
                AnimatedVisibility(
                    visible = !isScrollingUp,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Spacer(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(
                                    WindowInsets.statusBars,
                                ),
                    )
                }
                Row(
                    modifier =
                        Modifier
                            .horizontalScroll(chipRowState)
                            .padding(vertical = 8.dp, horizontal = 15.dp)
                            .background(Color.Transparent),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // 首位"全部"+高质量分类标签,与 YT 态 mood chips 同交互位
                    Chip(
                        isAnimated = state is NeteaseHomeViewModel.State.Loading,
                        isSelected = selectedTag == null,
                        text = stringResource(Res.string.all),
                    ) { viewModel.selectTag(null) }
                    tags.forEach { tag ->
                        Chip(
                            isAnimated = state is NeteaseHomeViewModel.State.Loading,
                            isSelected = selectedTag == tag.name,
                            text = tag.name,
                        ) { viewModel.selectTag(tag.name) }
                    }
                }
            }
        }
    }
}
