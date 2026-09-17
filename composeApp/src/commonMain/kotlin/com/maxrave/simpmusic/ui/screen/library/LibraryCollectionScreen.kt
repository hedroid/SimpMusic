package com.maxrave.simpmusic.ui.screen.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.maxrave.common.LibraryChipType
import com.maxrave.domain.data.type.PlaylistType
import com.maxrave.domain.utils.toTrack
import com.maxrave.simpmusic.extension.copy
import com.maxrave.simpmusic.ui.component.EndOfPage
import com.maxrave.simpmusic.ui.component.GridLibraryPlaylist
import com.maxrave.simpmusic.ui.component.LibraryTilingItem
import com.maxrave.simpmusic.ui.component.LibraryTilingState
import com.maxrave.simpmusic.ui.component.NowPlayingBottomSheet
import com.maxrave.simpmusic.ui.component.RippleIconButton
import com.maxrave.simpmusic.ui.component.SongFullWidthItems
import com.maxrave.simpmusic.ui.component.rememberSurfaceDarkColors
import com.maxrave.simpmusic.ui.icon.ArrowBackIosNew
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.LibraryDynamicPlaylistViewModel
import com.maxrave.simpmusic.viewModel.LibraryViewModel
import com.maxrave.simpmusic.viewModel.SharedViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.cancel
import simpmusic.composeapp.generated.resources.create
import simpmusic.composeapp.generated.resources.delete
import simpmusic.composeapp.generated.resources.downloaded
import simpmusic.composeapp.generated.resources.favorite
import simpmusic.composeapp.generated.resources.library_podcasts
import simpmusic.composeapp.generated.resources.no_favorite_playlists
import simpmusic.composeapp.generated.resources.no_favorite_podcasts
import simpmusic.composeapp.generated.resources.no_downloaded_songs
import simpmusic.composeapp.generated.resources.no_playlists_added
import simpmusic.composeapp.generated.resources.no_playlists_downloaded
import simpmusic.composeapp.generated.resources.playlist_name
import simpmusic.composeapp.generated.resources.playlist_name_cannot_be_empty
import simpmusic.composeapp.generated.resources.playlists
import simpmusic.composeapp.generated.resources.remove_download_message
import simpmusic.composeapp.generated.resources.remove_download_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryCollectionScreen(
    innerPadding: PaddingValues,
    navController: NavController,
    type: String,
    viewModel: LibraryViewModel = koinViewModel(),
    dynamicPlaylistViewModel: LibraryDynamicPlaylistViewModel = koinViewModel(),
    sharedViewModel: SharedViewModel = koinViewModel(),
) {
    val pageType = runCatching { LibraryChipType.valueOf(type) }.getOrDefault(LibraryChipType.LOCAL_PLAYLIST)
    val localPlaylists by viewModel.yourLocalPlaylist.collectAsStateWithLifecycle()
    val collections by viewModel.favoritePlaylist.collectAsStateWithLifecycle()
    val downloads by viewModel.downloadedPlaylist.collectAsStateWithLifecycle()
    val podcasts by viewModel.favoritePodcasts.collectAsStateWithLifecycle()
    val downloadedSongs by dynamicPlaylistViewModel.listDownloadedSong.collectAsStateWithLifecycle()
    val nowPlaying by sharedViewModel.nowPlayingState.collectAsStateWithLifecycle()
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var removeDownloadTarget by remember { mutableStateOf<PlaylistType?>(null) }
    var downloadedSection by remember { mutableStateOf(DownloadedSection.Songs) }
    var selectedDownloadedSong by remember { mutableStateOf<com.maxrave.domain.data.entities.SongEntity?>(null) }

    LaunchedEffect(pageType) {
        when (pageType) {
            LibraryChipType.LOCAL_PLAYLIST -> viewModel.getLocalPlaylist()
            LibraryChipType.FAVORITE_PLAYLIST -> viewModel.getPlaylistFavorite()
            LibraryChipType.DOWNLOADED_PLAYLIST -> viewModel.getDownloadedPlaylist()
            LibraryChipType.FAVORITE_PODCAST -> viewModel.getFavoritePodcasts()
            else -> Unit
        }
    }

    val title =
        when (pageType) {
            LibraryChipType.LOCAL_PLAYLIST -> stringResource(Res.string.playlists)
            LibraryChipType.FAVORITE_PLAYLIST -> stringResource(Res.string.favorite)
            LibraryChipType.DOWNLOADED_PLAYLIST -> stringResource(Res.string.downloaded)
            LibraryChipType.FAVORITE_PODCAST -> stringResource(Res.string.library_podcasts)
            else -> stringResource(Res.string.playlists)
        }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(text = title, style = typo().titleMedium) },
                navigationIcon = {
                    RippleIconButton(
                        imageVector = SimpIcons.ArrowBackIosNew,
                        tint = MaterialTheme.colorScheme.onBackground,
                        onClick = { navController.popBackStack() },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { scaffoldPadding ->
        val contentPadding =
            scaffoldPadding.copy(
                bottom = innerPadding.calculateBottomPadding(),
            )
        when (pageType) {
            LibraryChipType.LOCAL_PLAYLIST -> {
                GridLibraryPlaylist(
                    navController = navController,
                    contentPadding = contentPadding,
                    data = localPlaylists,
                    emptyText = Res.string.no_playlists_added,
                    createNewPlaylist = { showCreatePlaylist = true },
                    onReload = viewModel::getLocalPlaylist,
                )
            }

            LibraryChipType.FAVORITE_PLAYLIST -> {
                GridLibraryPlaylist(
                    navController = navController,
                    contentPadding = contentPadding,
                    data = collections,
                    emptyText = Res.string.no_favorite_playlists,
                    showSourceBadge = true,
                    onReload = viewModel::getPlaylistFavorite,
                )
            }

            LibraryChipType.DOWNLOADED_PLAYLIST -> {
                Column(modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            LibraryTilingItem(
                                state = LibraryTilingState.DownloadedSongs,
                                selected = downloadedSection == DownloadedSection.Songs,
                                onClick = { downloadedSection = DownloadedSection.Songs },
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            LibraryTilingItem(
                                state = LibraryTilingState.DownloadedPlaylists,
                                selected = downloadedSection == DownloadedSection.Playlists,
                                onClick = { downloadedSection = DownloadedSection.Playlists },
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        if (downloadedSection == DownloadedSection.Songs) {
                            if (downloadedSongs.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = stringResource(Res.string.no_downloaded_songs),
                                        style = typo().labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                                ) {
                                    items(downloadedSongs, key = { it.videoId }) { song ->
                                        SongFullWidthItems(
                                            songEntity = song,
                                            isPlaying = nowPlaying?.track?.videoId == song.videoId,
                                            modifier = Modifier.fillMaxWidth(),
                                            onClickListener = {
                                                dynamicPlaylistViewModel.playSong(
                                                    it,
                                                    LibraryDynamicPlaylistType.Downloaded,
                                                )
                                            },
                                            onMoreClickListener = { selectedDownloadedSong = song },
                                            onAddToQueue = {
                                                sharedViewModel.addListToQueue(arrayListOf(song.toTrack()))
                                            },
                                        )
                                    }
                                    item { EndOfPage() }
                                }
                            }
                        } else {
                            GridLibraryPlaylist(
                                navController = navController,
                                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                                data = downloads,
                                emptyText = Res.string.no_playlists_downloaded,
                                showSourceBadge = true,
                                onRemoveDownload = { removeDownloadTarget = it },
                                onReload = viewModel::getDownloadedPlaylist,
                            )
                        }
                    }
                }
            }

            LibraryChipType.FAVORITE_PODCAST -> {
                GridLibraryPlaylist(
                    navController = navController,
                    contentPadding = contentPadding,
                    data = podcasts,
                    emptyText = Res.string.no_favorite_podcasts,
                    onReload = viewModel::getFavoritePodcasts,
                )
            }

            else -> Unit
        }
    }

    selectedDownloadedSong?.let { song ->
        NowPlayingBottomSheet(
            onDismiss = { selectedDownloadedSong = null },
            navController = navController,
            song = song,
        )
    }

    if (showCreatePlaylist) {
        CreateLocalPlaylistSheet(
            viewModel = viewModel,
            onDismiss = { showCreatePlaylist = false },
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
                TextButton(
                    onClick = {
                        viewModel.removeDownloadedPlaylist(target)
                        removeDownloadTarget = null
                    },
                ) {
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

private enum class DownloadedSection {
    Songs,
    Playlists,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateLocalPlaylistSheet(
    viewModel: LibraryViewModel,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val close = {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        contentColor = Color.Transparent,
        dragHandle = null,
        scrimColor = Color.Black.copy(alpha = .5f),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.width(60.dp).height(4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(50),
                ) {}
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(text = stringResource(Res.string.playlist_name)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
                TextButton(
                    onClick = {
                        if (title.isBlank()) {
                            viewModel.makeToast(runBlocking { getString(Res.string.playlist_name_cannot_be_empty) })
                        } else {
                            viewModel.createPlaylist(title)
                            close()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(Res.string.create))
                }
            }
        }
    }
}
