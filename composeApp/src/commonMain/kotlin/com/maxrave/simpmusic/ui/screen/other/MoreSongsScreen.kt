package com.maxrave.simpmusic.ui.screen.other

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.utils.toSongEntity
import com.maxrave.domain.utils.toTrack
import com.maxrave.simpmusic.ui.component.CenterLoadingBox
import com.maxrave.simpmusic.ui.component.EndOfPage
import com.maxrave.simpmusic.ui.component.NowPlayingBottomSheet
import com.maxrave.simpmusic.ui.component.RippleIconButton
import com.maxrave.simpmusic.ui.component.SongFullWidthItems
import com.maxrave.simpmusic.ui.icon.ArrowBackIosNew
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.MoreSongsUIState
import com.maxrave.simpmusic.viewModel.MoreSongsViewModel
import com.maxrave.simpmusic.viewModel.SharedViewModel
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.popular
import simpmusic.composeapp.generated.resources.sort_by_newest

/** 网易艺人"全部歌曲"分页页:热门/最新排序 chips + 近底追加(MoreAlbums 同款骨架)。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun MoreSongsScreen(
    innerPadding: PaddingValues,
    navController: NavController,
    id: String? = null,
    artistName: String? = null,
    viewModel: MoreSongsViewModel = koinViewModel(),
    sharedViewModel: SharedViewModel = koinInject(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val hazeState = rememberHazeState()
    val listState = rememberLazyListState()
    val playingTrack by remember {
        sharedViewModel.nowPlayingState.map { it?.track?.videoId }
    }.collectAsState(null)
    var showBottomSheet by remember { mutableStateOf(false) }
    var menuSong by remember { mutableStateOf<SongEntity?>(null) }

    LaunchedEffect(id) {
        if (id != null) viewModel.load(id, artistName ?: "")
    }

    // 近底触发加载下一页(剩 8 行内)
    val shouldLoadMore by remember {
        derivedStateOf {
            val state = uiState as? MoreSongsUIState.Success ?: return@derivedStateOf false
            state.hasMore &&
                !state.loadingMore &&
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                    ?.let { it >= listState.layoutInfo.totalItemsCount - 8 } == true
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = uiState) { state ->
            when (state) {
                is MoreSongsUIState.Success -> {
                    LazyColumn(
                        state = listState,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .hazeSource(state = hazeState),
                    ) {
                        item(contentType = "header") {
                            Spacer(Modifier.size(innerPadding.calculateTopPadding() + 64.dp))
                        }
                        // 排序 chips:热门(默认)/最新;切换整页重拉(服务端排序,非本地重排)
                        item(contentType = "sort_chips") {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 15.dp, vertical = 4.dp)
                                        .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = state.order == MoreSongsViewModel.ORDER_HOT,
                                    onClick = { viewModel.setOrder(MoreSongsViewModel.ORDER_HOT) },
                                    label = { Text(stringResource(Res.string.popular)) },
                                )
                                FilterChip(
                                    selected = state.order == MoreSongsViewModel.ORDER_TIME,
                                    onClick = { viewModel.setOrder(MoreSongsViewModel.ORDER_TIME) },
                                    label = { Text(stringResource(Res.string.sort_by_newest)) },
                                )
                            }
                        }
                        itemsIndexed(state.songs, key = { index, song -> "$index-${song.videoId}" }) { index, song ->
                            val track = song.toTrack()
                            SongFullWidthItems(
                                track = track,
                                isPlaying = track.videoId == playingTrack,
                                modifier = Modifier.fillMaxWidth(),
                                onMoreClickListener = {
                                    menuSong = track.toSongEntity()
                                    showBottomSheet = true
                                },
                                onClickListener = { viewModel.playFrom(index) },
                                onAddToQueue = {
                                    sharedViewModel.addListToQueue(arrayListOf(track))
                                },
                            )
                        }
                        item(contentType = "footer") {
                            if (state.loadingMore) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(strokeWidth = 2.5.dp)
                                }
                            } else {
                                EndOfPage()
                            }
                        }
                    }
                    TopAppBar(
                        modifier =
                            Modifier.hazeEffect(state = hazeState, style = HazeMaterials.ultraThin()) {
                                blurEnabled = true
                            },
                        title = {
                            Text(
                                text = state.title,
                                style = typo().titleMedium,
                                maxLines = 1,
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
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                Color.Transparent,
                                Color.Unspecified,
                                Color.Unspecified,
                                Color.Unspecified,
                                Color.Unspecified,
                            ),
                    )
                }

                is MoreSongsUIState.Error -> {
                    viewModel.makeToast(state.message)
                }

                MoreSongsUIState.Loading -> {
                    CenterLoadingBox(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(15.dp),
                    )
                }
            }
        }
        if (showBottomSheet) {
            val sheetSong = menuSong
            if (sheetSong != null) {
                NowPlayingBottomSheet(
                    onDismiss = {
                        showBottomSheet = false
                        menuSong = null
                    },
                    navController = navController,
                    song = sheetSong,
                )
            }
        }
    }
}
