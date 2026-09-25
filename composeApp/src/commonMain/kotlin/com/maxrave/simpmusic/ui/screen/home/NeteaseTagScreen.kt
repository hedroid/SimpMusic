package com.maxrave.simpmusic.ui.screen.home

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.maxrave.data.repository.NeteaseTagOrder
import com.maxrave.simpmusic.ui.component.CenterLoadingBox
import com.maxrave.simpmusic.ui.component.Chip
import com.maxrave.simpmusic.ui.component.EndOfPage
import com.maxrave.simpmusic.ui.component.HomeItemContentPlaylist
import com.maxrave.simpmusic.ui.navigation.destination.list.PlaylistDestination
import com.maxrave.simpmusic.ui.icon.ArrowBackIosNew
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.NeteaseTagViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.netease_home_error
import simpmusic.composeapp.generated.resources.retry
import simpmusic.composeapp.generated.resources.sort_highquality
import simpmusic.composeapp.generated.resources.sort_hot

/**
 * 网易标签分类页:两列自适应网格(对标网页版歌单广场),上游 MoodScreen 零改动。
 * 顶部排序 chip:热门/精品(/playlist/list 的 order 实测只支持 hot,new 返回空),
 * 下拉刷新绕过会话缓存强制重拉。
 */
@Composable
fun NeteaseTagScreen(
    navController: NavController,
    viewModel: NeteaseTagViewModel = koinViewModel(),
    tag: String,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // enum 不能直接进 Bundle,存 ordinal
    var orderIndex by rememberSaveable { mutableIntStateOf(0) }
    val order = NeteaseTagOrder.entries[orderIndex]

    LaunchedEffect(tag, order) { viewModel.load(tag, order) }

    // isRefreshing 只跟随"下拉触发的刷新";切 chip 的 Loading 不点亮下拉指示器
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state !is NeteaseTagViewModel.State.Loading) refreshing = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // 顶栏:返回 + 标签名
        Box(Modifier.fillMaxWidth()) {
            IconButton(onClick = navController::navigateUp, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(
                    imageVector = SimpIcons.ArrowBackIosNew,
                    contentDescription = null,
                )
            }
            Text(
                text = tag,
                style = typo().headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        // 排序 chips:热门/精品(order=new 服务端不支持,不加)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val labels =
                listOf(
                    Res.string.sort_hot,
                    Res.string.sort_highquality,
                )
            NeteaseTagOrder.entries.forEachIndexed { index, tagOrder ->
                Chip(
                    isSelected = index == orderIndex,
                    text = stringResource(labels[index]),
                ) {
                    if (orderIndex != index) orderIndex = index
                }
            }
        }
        val pullToRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            modifier = Modifier.fillMaxSize(),
            state = pullToRefreshState,
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                viewModel.load(tag, order, force = true)
            },
        ) {
            Crossfade(state) { current ->
                when (current) {
                    is NeteaseTagViewModel.State.Loading -> CenterLoadingBox(Modifier.fillMaxSize())

                    is NeteaseTagViewModel.State.Error ->
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
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { viewModel.load(tag, order) }) {
                                Text(stringResource(Res.string.retry))
                            }
                        }

                    is NeteaseTagViewModel.State.Ready ->
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            modifier = Modifier.fillMaxSize(),
                            // 与主页歌单行同口径:页面水平边距 15dp、卡片间距 4dp。
                            // 旧实现无 contentPadding,间距靠每个 item 外包 Box(padding 8dp),
                            // 页边只有 8dp 且随列数浮动,与主页 15dp 不一致
                            contentPadding = PaddingValues(start = 15.dp, end = 15.dp, top = 4.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // id 参与但 index 兜底:映射形状缺 id(null)或数据源异常重复时
                            // 不至于 duplicate/null key 直接崩
                            itemsIndexed(
                                current.contents,
                                key = { index, content -> "${content.playlistBrowseId}-$index" },
                            ) { _, content ->
                                HomeItemContentPlaylist(
                                    onClick = {
                                        content.playlistBrowseId?.let { id ->
                                            navController.navigate(PlaylistDestination(playlistId = id))
                                        }
                                    },
                                    data = content,
                                    fillWidth = true,
                                )
                            }
                            // copyright 页脚:跨满整行(普通 item 只占一格宽,格式就不对了)
                            item(span = { GridItemSpan(maxLineSpan) }) { EndOfPage() }
                        }
                }
            }
        }
    }
}
