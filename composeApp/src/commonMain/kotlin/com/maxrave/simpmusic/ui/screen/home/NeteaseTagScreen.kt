package com.maxrave.simpmusic.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.maxrave.simpmusic.ui.component.CenterLoadingBox
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

/**
 * 网易标签分类页:两列自适应网格(对标网页版歌单广场),上游 MoodScreen 零改动。
 */
@Composable
fun NeteaseTagScreen(
    navController: NavController,
    viewModel: NeteaseTagViewModel = koinViewModel(),
    tag: String,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(tag) { viewModel.load(tag) }

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
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.netease_home_error),
                            style = typo().bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { viewModel.load(tag) }) {
                            Text(stringResource(Res.string.retry))
                        }
                    }

                is NeteaseTagViewModel.State.Ready ->
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(current.contents, key = { it.playlistBrowseId }) { content ->
                            Box(Modifier.padding(8.dp)) {
                                HomeItemContentPlaylist(
                                    onClick = {
                                        content.playlistBrowseId?.let { id ->
                                            navController.navigate(PlaylistDestination(playlistId = id))
                                        }
                                    },
                                    data = content,
                                )
                            }
                        }
                        item { EndOfPage() }
                    }
            }
        }
    }
}
