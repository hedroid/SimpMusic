package com.maxrave.simpmusic.ui.screen.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.maxrave.common.LibraryChipType
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.data.type.PlaylistType
import com.maxrave.domain.utils.LocalResource
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.extension.copy
import com.maxrave.simpmusic.extension.isScrollingUp
import com.maxrave.simpmusic.ui.component.AddToPlaylistModalBottomSheet
import com.maxrave.simpmusic.ui.component.Chip
import com.maxrave.simpmusic.ui.component.EndOfPage
import com.maxrave.simpmusic.ui.component.GridLibraryPlaylist
import com.maxrave.simpmusic.ui.component.LibraryItem
import com.maxrave.simpmusic.ui.component.LibraryItemState
import com.maxrave.simpmusic.ui.component.LibraryItemType
import com.maxrave.simpmusic.ui.component.LibraryTilingBox
import com.maxrave.simpmusic.ui.component.LibraryTilingItem
import com.maxrave.simpmusic.ui.component.LibraryTilingState
import com.maxrave.simpmusic.ui.component.ListenTogetherIconButton
import com.maxrave.simpmusic.ui.component.RippleIconButton
import com.maxrave.simpmusic.ui.component.rememberSurfaceDarkColors
import com.maxrave.simpmusic.ui.component.selection.SelectedSongsBottomSheet
import com.maxrave.simpmusic.ui.component.selection.SongSelectionTopAppBar
import com.maxrave.simpmusic.ui.component.selection.rememberSongSelectionState
import com.maxrave.simpmusic.ui.icon.Groups
import com.maxrave.simpmusic.ui.icon.PeopleAlt
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.navigation.destination.home.ListenTogetherDestination
import com.maxrave.simpmusic.ui.navigation.destination.library.LibraryCollectionDestination
import com.maxrave.simpmusic.ui.navigation.destination.library.LibraryDynamicPlaylistDestination
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.LibraryViewModel
import com.maxrave.simpmusic.viewModel.SongSelectionViewModel
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.chart
import simpmusic.composeapp.generated.resources.cancel
import simpmusic.composeapp.generated.resources.create
import simpmusic.composeapp.generated.resources.delete
import simpmusic.composeapp.generated.resources.downloaded_collections
import simpmusic.composeapp.generated.resources.downloaded_playlists
import simpmusic.composeapp.generated.resources.favorite
import simpmusic.composeapp.generated.resources.favorite_playlists
import simpmusic.composeapp.generated.resources.favorite_podcasts
import simpmusic.composeapp.generated.resources.library
import simpmusic.composeapp.generated.resources.library_podcasts
import simpmusic.composeapp.generated.resources.mix_for_you
import simpmusic.composeapp.generated.resources.no_YouTube_playlists
import simpmusic.composeapp.generated.resources.no_charts_found
import simpmusic.composeapp.generated.resources.no_favorite_playlists
import simpmusic.composeapp.generated.resources.no_favorite_podcasts
import simpmusic.composeapp.generated.resources.no_playlists_added
import simpmusic.composeapp.generated.resources.no_playlists_downloaded
import simpmusic.composeapp.generated.resources.playlist_name
import simpmusic.composeapp.generated.resources.playlist_name_cannot_be_empty
import simpmusic.composeapp.generated.resources.playlists
import simpmusic.composeapp.generated.resources.remove_download_message
import simpmusic.composeapp.generated.resources.remove_download_title
import simpmusic.composeapp.generated.resources.simpmusic_charts
import simpmusic.composeapp.generated.resources.wrapped
import simpmusic.composeapp.generated.resources.your_library
import simpmusic.composeapp.generated.resources.your_netease
import simpmusic.composeapp.generated.resources.your_playlists
import simpmusic.composeapp.generated.resources.your_youtube_music

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun LibraryScreen(
    innerPadding: PaddingValues,
    viewModel: LibraryViewModel = koinViewModel(),
    navController: NavController,
    onScrolling: (onTop: Boolean) -> Unit = {},
) {
    val density = LocalDensity.current

    val loggedIn by viewModel.youtubeLoggedIn.collectAsStateWithLifecycle(initialValue = false)
    // Wrapped and its recaps are built entirely from playback_event, so the chip follows the same
    // setting the Analytics tab does.
    val localTrackingEnabled by viewModel.localTrackingEnabled.collectAsStateWithLifecycle(initialValue = false)
    val monthlyRecaps by viewModel.monthlyRecaps.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlayingVideoId.collectAsStateWithLifecycle()
    val youTubePlaylist by viewModel.youTubePlaylist.collectAsStateWithLifecycle()
    val listCanvasSong by viewModel.listCanvasSong.collectAsStateWithLifecycle()
    val yourLocalPlaylist by viewModel.yourLocalPlaylist.collectAsStateWithLifecycle()
    val favoritePlaylist by viewModel.favoritePlaylist.collectAsStateWithLifecycle()
    val downloadedPlaylist by viewModel.downloadedPlaylist.collectAsStateWithLifecycle()
    val favoritePodcasts by viewModel.favoritePodcasts.collectAsStateWithLifecycle()
    val chartPlaylists by viewModel.chartPlaylists.collectAsStateWithLifecycle()
    val recentlyAdded by viewModel.recentlyAdded.collectAsStateWithLifecycle()
    val neteaseLoggedIn by viewModel.neteaseLoggedIn.collectAsStateWithLifecycle(initialValue = false)
    val neteasePlaylist by viewModel.neteasePlaylist.collectAsStateWithLifecycle()
    val subscribedArtists by viewModel.subscribedArtists.collectAsStateWithLifecycle()
    val starredAlbums by viewModel.starredAlbums.collectAsStateWithLifecycle()
    val neteaseRefreshing by viewModel.neteaseRefreshing.collectAsStateWithLifecycle()
    val ownNeteasePlaylistIds by viewModel.ownNeteasePlaylistIds.collectAsStateWithLifecycle()
    val neteaseLikedPlaylistId by viewModel.neteaseLikedPlaylistId.collectAsStateWithLifecycle()

    val selectionState = rememberSongSelectionState()
    val selectionViewModel: SongSelectionViewModel = koinViewModel()
    var showSelectionSheet by rememberSaveable { mutableStateOf(false) }
    var showSelectionAddToPlaylist by rememberSaveable { mutableStateOf(false) }
    val allSelectedDownloaded by selectionViewModel.allSelectedDownloaded.collectAsStateWithLifecycle()
    LaunchedEffect(showSelectionSheet) {
        if (showSelectionSheet) selectionViewModel.checkAllDownloaded(selectionState.selected.toList())
    }
    // The playlist/album tile long-pressed in the downloaded grid, awaiting the confirm dialog.
    // Plain remember: the payload is not saveable and the dialog is short-lived enough that a
    // process death mid-confirm can just start over.
    var removeDownloadTarget by remember { mutableStateOf<PlaylistType?>(null) }
    val accountThumbnail by viewModel.accountThumbnail.collectAsStateWithLifecycle()
    val hazeState =
        rememberHazeState(
            blurEnabled = true,
        )

    var topAppBarHeight by remember {
        mutableStateOf(0.dp)
    }
    var showAddSheet by remember { mutableStateOf(false) }

    LaunchedEffect(nowPlaying) {
        Logger.w("LibraryScreen", "Check nowPlaying: $nowPlaying")
        viewModel.getRecentlyAdded()
    }

    val chipRowState = rememberScrollState()
    val currentFilter by viewModel.currentScreen.collectAsStateWithLifecycle()
    val openLibraryPlaylists = {
        navController.navigate(LibraryCollectionDestination(LibraryChipType.LOCAL_PLAYLIST.name))
    }
    val openLibraryCollections = {
        navController.navigate(LibraryCollectionDestination(LibraryChipType.FAVORITE_PLAYLIST.name))
    }
    val openLibraryPodcasts = {
        navController.navigate(LibraryCollectionDestination(LibraryChipType.FAVORITE_PODCAST.name))
    }
    val openLibraryDownloads = {
        navController.navigate(LibraryCollectionDestination(LibraryChipType.DOWNLOADED_PLAYLIST.name))
    }

    LaunchedEffect(currentFilter) {
        when (currentFilter) {
            LibraryChipType.YOUTUBE_MUSIC_PLAYLIST -> {
                if (youTubePlaylist.data.isNullOrEmpty()) {
                    viewModel.getYouTubePlaylist()
                }
            }

            // "您的网易云"三分区(歌单/关注的歌手/收藏的专辑):空数据才拉,下拉刷新走 force。
            // 登出回落由 VM 的 neteaseCookie collect 负责,这里不会停在无数据的分区上。
            LibraryChipType.NETEASE_PLAYLIST -> {
                if (neteasePlaylist !is LocalResource.Success) {
                    viewModel.getNeteaseLibrary()
                }
            }

            // Mix for you has its own nav tab now. The filter is persisted, so a build upgraded
            // while it was selected would land here with no chip to match — send it back to the
            // default. The enum value itself stays so older persisted values still parse.
            LibraryChipType.YOUTUBE_MIX_FOR_YOU -> {
                viewModel.setCurrentScreen(LibraryChipType.YOUR_LIBRARY)
            }

            LibraryChipType.YOUR_LIBRARY -> {
                viewModel.getCanvasSong()
                viewModel.getRecentlyAdded()
            }

            LibraryChipType.LOCAL_PLAYLIST,
            LibraryChipType.FAVORITE_PLAYLIST,
            LibraryChipType.DOWNLOADED_PLAYLIST,
            LibraryChipType.FAVORITE_PODCAST,
            -> viewModel.setCurrentScreen(LibraryChipType.YOUR_LIBRARY)

            LibraryChipType.CHART -> {
                if (chartPlaylists.data.isNullOrEmpty()) {
                    viewModel.getChartPlaylists()
                }
            }

            LibraryChipType.WRAPPED -> {
                viewModel.getMonthlyRecaps()
            }
        }
    }

    Crossfade(
        modifier = Modifier.hazeSource(hazeState),
        targetState = currentFilter,
    ) { filter ->
        when (filter) {
            LibraryChipType.YOUR_LIBRARY -> {
                val state = rememberLazyListState()
                val isScrollingUp by state.isScrollingUp()
                LaunchedEffect(state) {
                    snapshotFlow { state.firstVisibleItemIndex }
                        .collect {
                            if (it <= 1) {
                                onScrolling.invoke(true)
                            } else {
                                onScrolling.invoke(isScrollingUp)
                            }
                        }
                }
                LazyColumn(
                    contentPadding =
                        innerPadding.copy(
                            top = topAppBarHeight,
                        ),
                    state = state,
                ) {
                    item {
                        LibraryTilingBox(
                            navController = navController,
                            onOpenPlaylists = openLibraryPlaylists,
                            onOpenCollections = openLibraryCollections,
                            onOpenPodcasts = openLibraryPodcasts,
                            onOpenDownloads = openLibraryDownloads,
                        )
                    }

                    if (!listCanvasSong.data.isNullOrEmpty()) {
                        item {
                            LibraryItem(
                                state =
                                    LibraryItemState(
                                        type = LibraryItemType.CanvasSong,
                                        data = listCanvasSong.data ?: emptyList(),
                                        isLoading = listCanvasSong is LocalResource.Loading,
                                    ),
                                navController = navController,
                            )
                        }
                    }

                    item {
                        LibraryItem(
                            state =
                                LibraryItemState(
                                    type =
                                        LibraryItemType.RecentlyAdded(
                                            playingVideoId = nowPlaying,
                                        ),
                                    data = recentlyAdded.data ?: emptyList(),
                                    isLoading = recentlyAdded is LocalResource.Loading,
                                ),
                            navController = navController,
                            selectionState = selectionState,
                        )
                    }
                    item {
                        EndOfPage()
                    }
                }
            }

            LibraryChipType.YOUTUBE_MUSIC_PLAYLIST -> {
                GridLibraryPlaylist(
                    navController,
                    innerPadding.copy(top = topAppBarHeight),
                    youTubePlaylist,
                    emptyText = Res.string.no_YouTube_playlists,
                    onScrolling = onScrolling,
                ) {
                    viewModel.getYouTubePlaylist()
                }
            }

            LibraryChipType.NETEASE_PLAYLIST -> {
                LibraryNeteaseTab(
                    navController = navController,
                    contentPadding = innerPadding.copy(top = topAppBarHeight),
                    playlists = neteasePlaylist,
                    artists = subscribedArtists,
                    albums = starredAlbums,
                    isRefreshing = neteaseRefreshing,
                    onRefresh = { viewModel.getNeteaseLibrary(force = true) },
                    ownPlaylistIds = ownNeteasePlaylistIds,
                    likedPlaylistId = neteaseLikedPlaylistId,
                    onUnsubscribePlaylist = { viewModel.unsubscribeNeteasePlaylist(it) },
                    onDeletePlaylist = { viewModel.deleteNeteasePlaylist(it) },
                    onUnsubscribeAlbum = { viewModel.unsubscribeNeteaseAlbum(it) },
                    onScrolling = onScrolling,
                )
            }

            // Nothing to draw: MixForYouScreen owns this content now, and the effect above bounces
            // the filter back to YOUR_LIBRARY the moment it lands here.
            LibraryChipType.YOUTUBE_MIX_FOR_YOU -> Unit

            LibraryChipType.LOCAL_PLAYLIST -> {
                GridLibraryPlaylist(
                    navController,
                    innerPadding.copy(top = topAppBarHeight),
                    yourLocalPlaylist,
                    onScrolling = onScrolling,
                    emptyText = Res.string.no_playlists_added,
                    header = {
                        LibrarySectionHeader(
                            navController = navController,
                            title = stringResource(Res.string.playlists),
                            onOpenPlaylists = openLibraryPlaylists,
                            onOpenCollections = openLibraryCollections,
                            onOpenPodcasts = openLibraryPodcasts,
                            onOpenDownloads = openLibraryDownloads,
                        )
                    },
                    createNewPlaylist = {
                        showAddSheet = true
                    },
                ) {
                    viewModel.getLocalPlaylist()
                }
            }

            LibraryChipType.FAVORITE_PLAYLIST -> {
                GridLibraryPlaylist(
                    navController,
                    innerPadding.copy(top = topAppBarHeight),
                    favoritePlaylist,
                    emptyText = Res.string.no_favorite_playlists,
                    // 混源网格:网易来源的收藏条目带品牌角标
                    showSourceBadge = true,
                    onScrolling = onScrolling,
                    header = {
                        LibrarySectionHeader(
                            navController = navController,
                            title = stringResource(Res.string.favorite),
                            onOpenPlaylists = openLibraryPlaylists,
                            onOpenCollections = openLibraryCollections,
                            onOpenPodcasts = openLibraryPodcasts,
                            onOpenDownloads = openLibraryDownloads,
                        )
                    },
                ) {
                    viewModel.getPlaylistFavorite()
                }
            }

            LibraryChipType.DOWNLOADED_PLAYLIST -> {
                GridLibraryPlaylist(
                    navController,
                    innerPadding.copy(top = topAppBarHeight),
                    downloadedPlaylist,
                    emptyText = Res.string.no_playlists_downloaded,
                    showSourceBadge = true,
                    onScrolling = onScrolling,
                    header = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            LibraryTilingBox(
                                navController = navController,
                                onOpenPlaylists = openLibraryPlaylists,
                                onOpenCollections = openLibraryCollections,
                                onOpenPodcasts = openLibraryPodcasts,
                                onOpenDownloads = openLibraryDownloads,
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                            ) {
                                LibraryTilingItem(
                                    state = LibraryTilingState.DownloadedSongs,
                                    onClick = {
                                        navController.navigate(
                                            LibraryDynamicPlaylistDestination(
                                                type = LibraryDynamicPlaylistType.Downloaded.toStringParams(),
                                            ),
                                        )
                                    },
                                )
                                Text(
                                    text = stringResource(Res.string.downloaded_collections),
                                    style = typo().titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
                                )
                            }
                        }
                    },
                    onRemoveDownload = { item ->
                        removeDownloadTarget = item
                    },
                ) {
                    viewModel.getDownloadedPlaylist()
                }
            }

            LibraryChipType.FAVORITE_PODCAST -> {
                GridLibraryPlaylist(
                    navController,
                    innerPadding.copy(top = topAppBarHeight),
                    favoritePodcasts,
                    emptyText = Res.string.no_favorite_podcasts,
                    onScrolling = onScrolling,
                    header = {
                        LibrarySectionHeader(
                            navController = navController,
                            title = stringResource(Res.string.library_podcasts),
                            onOpenPlaylists = openLibraryPlaylists,
                            onOpenCollections = openLibraryCollections,
                            onOpenPodcasts = openLibraryPodcasts,
                            onOpenDownloads = openLibraryDownloads,
                        )
                    },
                ) {
                    viewModel.getFavoritePodcasts()
                }
            }

            LibraryChipType.CHART -> {
                GridLibraryPlaylist(
                    navController,
                    innerPadding.copy(top = topAppBarHeight),
                    chartPlaylists,
                    emptyText = Res.string.no_charts_found,
                    onScrolling = onScrolling,
                ) {
                    viewModel.getChartPlaylists()
                }
            }

            LibraryChipType.WRAPPED -> {
                LibraryWrappedTab(
                    navController = navController,
                    contentPadding = innerPadding.copy(top = topAppBarHeight),
                    recaps = monthlyRecaps,
                    onScrolling = onScrolling,
                ) {
                    viewModel.getMonthlyRecaps()
                }
            }
        }
    }
    val coroutineScope = rememberCoroutineScope()
    if (showAddSheet) {
        var newTitle by remember { mutableStateOf("") }
        val showAddSheetState =
            rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
            )
        val hideEditTitleBottomSheet: () -> Unit =
            {
                coroutineScope.launch {
                    showAddSheetState.hide()
                    showAddSheet = false
                }
            }
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = showAddSheetState,
            containerColor = Color.Transparent,
            contentColor = Color.Transparent,
            dragHandle = null,
            scrimColor = Color.Black.copy(alpha = .5f),
        ) {
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                colors = CardDefaults.cardColors().copy(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Card(
                        modifier =
                            Modifier
                                .width(60.dp)
                                .height(4.dp),
                        colors =
                            CardDefaults.cardColors().copy(
                                containerColor = MaterialTheme.colorScheme.outline,
                            ),
                        shape = RoundedCornerShape(50),
                    ) {}
                    Spacer(modifier = Modifier.height(5.dp))
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { s -> newTitle = s },
                        label = {
                            Text(text = stringResource(Res.string.playlist_name))
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    TextButton(
                        onClick = {
                            if (newTitle.isBlank()) {
                                viewModel.makeToast(runBlocking { getString(Res.string.playlist_name_cannot_be_empty) })
                            } else {
                                viewModel.createPlaylist(newTitle)
                                hideEditTitleBottomSheet()
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.CenterHorizontally),
                    ) {
                        Text(text = stringResource(Res.string.create))
                    }
                }
            }
        }
    }
    Column(
        Modifier
            .background(Color.Transparent)
            // 传页面背景色:返回本页时首 1-2 帧 hazeSource 尚无内容,玻璃层显示底色而非
            // 全透明,消除"透明→磨砂"的顶栏闪烁
            .hazeEffect(hazeState, style = HazeMaterials.ultraThin(MaterialTheme.colorScheme.surface)) {
                blurEnabled = true
            }.onGloballyPositioned { coordinates ->
                topAppBarHeight = with(density) { coordinates.size.height.toDp() }
            },
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(Res.string.library),
                    style = typo().titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            navigationIcon = {
                AnimatedVisibility(
                    !accountThumbnail.isNullOrEmpty(),
                    modifier = Modifier.padding(horizontal = 12.dp),
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(LocalPlatformContext.current)
                                .data(accountThumbnail)
                                .crossfade(550)
                                .build(),
                        placeholder = rememberVectorPainter(SimpIcons.PeopleAlt),
                        error = rememberVectorPainter(SimpIcons.PeopleAlt),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape),
                    )
                }
            },
            // The Library bar had no actions slot at all — added for the Listen Together entry,
            // which the design canvas puts on Home AND Library.
            actions = {
                ListenTogetherIconButton { navController.navigate(ListenTogetherDestination) }
            },
        )
        AnimatedVisibility(visible = selectionState.isActive) {
            SongSelectionTopAppBar(
                state = selectionState,
                // Stacked BELOW the Library TopAppBar in the same Column, which already consumed
                // the status-bar inset — leaving the default here reserved it twice and opened a
                // status-bar-sized band of dead blur between the two bars. Same fix as Search;
                // the overlay-style call sites (Album, Artist, Recently…) keep the default because
                // they COVER their normal bar instead of standing under it.
                windowInsets = WindowInsets(0),
                onSelectAll = {
                    selectionState.toggleSelectAll(
                        (recentlyAdded.data ?: emptyList())
                            .filterIsInstance<SongEntity>()
                            .map { it.videoId },
                    )
                },
                onOpenActions = { showSelectionSheet = true },
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
            )
        }
        Row(
            modifier =
                Modifier
                    .horizontalScroll(chipRowState)
                    .padding(horizontal = 15.dp)
                    .padding(bottom = 8.dp)
                    .background(Color.Transparent),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val localLibraryPages =
                setOf(
                    LibraryChipType.LOCAL_PLAYLIST,
                    LibraryChipType.FAVORITE_PLAYLIST,
                    LibraryChipType.DOWNLOADED_PLAYLIST,
                    LibraryChipType.FAVORITE_PODCAST,
                )
            val topLevelLibraryChips =
                listOf(
                    LibraryChipType.YOUR_LIBRARY,
                    LibraryChipType.YOUTUBE_MUSIC_PLAYLIST,
                    LibraryChipType.NETEASE_PLAYLIST,
                    LibraryChipType.CHART,
                    LibraryChipType.WRAPPED,
                )
            topLevelLibraryChips.forEach { type ->
                if (type == LibraryChipType.YOUTUBE_MUSIC_PLAYLIST && !loggedIn) {
                    return@forEach
                }
                // "您的网易云"分区只在网易登录时出现(与 YT 分区对 YT 登录的门控对称)
                if (type == LibraryChipType.NETEASE_PLAYLIST && !neteaseLoggedIn) {
                    return@forEach
                }
                // Nothing to recap without the plays — gated exactly as the YouTube chip above
                // is gated on being logged in.
                if (type == LibraryChipType.WRAPPED && !localTrackingEnabled) {
                    return@forEach
                }
                Chip(
                    isAnimated = false,
                    isSelected =
                        type == currentFilter ||
                            (type == LibraryChipType.YOUR_LIBRARY && currentFilter in localLibraryPages),
                    text =
                        when (type) {
                            LibraryChipType.YOUR_LIBRARY -> stringResource(Res.string.your_library)
                            LibraryChipType.YOUTUBE_MUSIC_PLAYLIST -> stringResource(Res.string.your_youtube_music)
                            LibraryChipType.NETEASE_PLAYLIST -> stringResource(Res.string.your_netease)
                            LibraryChipType.YOUTUBE_MIX_FOR_YOU -> stringResource(Res.string.mix_for_you)
                            LibraryChipType.LOCAL_PLAYLIST -> stringResource(Res.string.your_playlists)
                            LibraryChipType.FAVORITE_PLAYLIST -> stringResource(Res.string.favorite_playlists)
                            LibraryChipType.DOWNLOADED_PLAYLIST -> stringResource(Res.string.downloaded_playlists)
                            LibraryChipType.FAVORITE_PODCAST -> stringResource(Res.string.favorite_podcasts)
                            LibraryChipType.CHART -> stringResource(Res.string.simpmusic_charts)
                            LibraryChipType.WRAPPED -> stringResource(Res.string.wrapped)
                        },
                ) {
                    viewModel.setCurrentScreen(type)
                }
            }
        }
        if (showSelectionSheet) {
            val selectedIds = selectionState.selected.toList()
            SelectedSongsBottomSheet(
                count = selectedIds.size,
                onDismiss = { showSelectionSheet = false },
                onPlayNext = {
                    selectionViewModel.playNext(selectedIds)
                    selectionState.exit()
                },
                onAddToQueue = {
                    selectionViewModel.addToQueue(selectedIds)
                    selectionState.exit()
                },
                onAddToPlaylist = { showSelectionAddToPlaylist = true },
                onDownload = {
                    selectionViewModel.download(selectedIds)
                    selectionState.exit()
                },
                allDownloaded = allSelectedDownloaded,
                onRemoveDownload = {
                    selectionViewModel.removeDownload(selectedIds)
                    selectionState.exit()
                },
                onAddToFavorite = {
                    selectionViewModel.addToFavorite(selectedIds)
                    selectionState.exit()
                },
            )
        }
        if (showSelectionAddToPlaylist) {
            val selectedIds = selectionState.selected.toList()
            val localPlaylists by selectionViewModel.listLocalPlaylist.collectAsStateWithLifecycle()
            AddToPlaylistModalBottomSheet(
                isBottomSheetVisible = true,
                listLocalPlaylist = localPlaylists,
                listYouTubePlaylist = emptyList(),
                onDismiss = { showSelectionAddToPlaylist = false },
                onClick = { playlist ->
                    selectionViewModel.addToPlaylist(playlist.id, selectedIds)
                    selectionState.exit()
                },
                onYTPlaylistClick = {},
            )
        }
        removeDownloadTarget?.let { target ->
            AlertDialog(
                containerColor = rememberSurfaceDarkColors().container,
                titleContentColor = rememberSurfaceDarkColors().content,
                textContentColor = rememberSurfaceDarkColors().content,
                title = { Text(text = stringResource(Res.string.remove_download_title)) },
                text = { Text(text = stringResource(Res.string.remove_download_message)) },
                onDismissRequest = { removeDownloadTarget = null },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.removeDownloadedPlaylist(target)
                        removeDownloadTarget = null
                    }) {
                        Text(text = stringResource(Res.string.delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { removeDownloadTarget = null }) {
                        Text(text = stringResource(Res.string.cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun LibrarySectionHeader(
    navController: NavController,
    title: String,
    onOpenPlaylists: () -> Unit,
    onOpenCollections: () -> Unit,
    onOpenPodcasts: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LibraryTilingBox(
            navController = navController,
            onOpenPlaylists = onOpenPlaylists,
            onOpenCollections = onOpenCollections,
            onOpenPodcasts = onOpenPodcasts,
            onOpenDownloads = onOpenDownloads,
        )
        Text(
            text = title,
            style = typo().titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 4.dp),
        )
    }
}
