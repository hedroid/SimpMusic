package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.maxrave.simpmusic.extension.NonLazyGrid
import com.maxrave.simpmusic.ui.icon.Album
import com.maxrave.simpmusic.ui.icon.Downloading
import com.maxrave.simpmusic.ui.icon.Favorite
import com.maxrave.simpmusic.ui.icon.Insights
import com.maxrave.simpmusic.ui.icon.LibraryMusic
import com.maxrave.simpmusic.ui.icon.RssFeed
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.icon.TrendingUp
import com.maxrave.simpmusic.ui.navigation.destination.library.LibraryDynamicPlaylistDestination
import com.maxrave.simpmusic.ui.screen.library.LibraryDynamicPlaylistType
import com.maxrave.simpmusic.ui.theme.typo
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.downloaded
import simpmusic.composeapp.generated.resources.downloaded_songs_tab
import simpmusic.composeapp.generated.resources.favorite
import simpmusic.composeapp.generated.resources.followed
import simpmusic.composeapp.generated.resources.library_podcasts
import simpmusic.composeapp.generated.resources.liked_songs
import simpmusic.composeapp.generated.resources.most_played
import simpmusic.composeapp.generated.resources.playlists

@Composable
fun LibraryTilingBox(
    navController: NavController,
    onOpenPlaylists: () -> Unit,
    onOpenCollections: () -> Unit,
    onOpenPodcasts: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val listItem =
        listOf(
            LibraryTilingState.Favorite,
            LibraryTilingState.Followed,
            LibraryTilingState.Playlists,
            LibraryTilingState.Collections,
            LibraryTilingState.MostPlayed,
            LibraryTilingState.Downloaded,
            LibraryTilingState.Podcasts,
        )
    NonLazyGrid(
        columns = 2,
        itemCount = listItem.size,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp, end = 10.dp),
    ) { number ->
        Box(
            Modifier.padding(start = 10.dp, top = 10.dp),
        ) {
            LibraryTilingItem(
                listItem[number],
                onClick = {
                    when (listItem[number]) {
                        LibraryTilingState.Favorite -> {
                            navController.navigate(
                                LibraryDynamicPlaylistDestination(
                                    type = LibraryDynamicPlaylistType.Favorite.toStringParams(),
                                ),
                            )
                        }

                        LibraryTilingState.Followed -> {
                            navController.navigate(
                                LibraryDynamicPlaylistDestination(
                                    type = LibraryDynamicPlaylistType.Followed.toStringParams(),
                                ),
                            )
                        }

                        LibraryTilingState.MostPlayed -> {
                            navController.navigate(
                                LibraryDynamicPlaylistDestination(
                                    type = LibraryDynamicPlaylistType.MostPlayed.toStringParams(),
                                ),
                            )
                        }

                        LibraryTilingState.Downloaded -> {
                            onOpenDownloads()
                        }

                        LibraryTilingState.Playlists -> onOpenPlaylists()

                        LibraryTilingState.Collections -> onOpenCollections()

                        LibraryTilingState.Podcasts -> onOpenPodcasts()
                    }
                },
            )
        }
    }
}

@Composable
fun LibraryTilingItem(
    state: LibraryTilingState,
    selected: Boolean = false,
    onClick: () -> Unit = {},
) {
    val title = stringResource(state.title)
    ElevatedCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (selected) {
                        Modifier.border(BorderStroke(3.dp, state.iconColor), RoundedCornerShape(8.dp))
                    } else {
                        Modifier
                    },
                ).clickable {
                    onClick.invoke()
                },
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.elevatedCardElevation(),
        colors =
            CardDefaults.elevatedCardColors().copy(
                containerColor = state.containerColor,
            ),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                state.icon,
                contentDescription = title,
                modifier =
                    Modifier
                        .size(50.dp)
                        .padding(10.dp),
                tint = state.iconColor,
            )
            Text(
                title,
                style = typo().titleSmall,
                color = Color.Black,
            )
        }
    }
}

data class LibraryTilingState(
    val title: StringResource,
    val containerColor: Color,
    val icon: ImageVector,
    val iconColor: Color,
) {
    companion object {
        val Favorite =
            LibraryTilingState(
                title = Res.string.liked_songs,
                containerColor = Color(0xffff99ae),
                icon = SimpIcons.Favorite,
                iconColor = Color(0xffD10000),
            )
        val Followed =
            LibraryTilingState(
                title = Res.string.followed,
                containerColor = Color(0xffFFEB3B),
                icon = SimpIcons.Insights,
                iconColor = Color.Black,
            )
        val MostPlayed =
            LibraryTilingState(
                title = Res.string.most_played,
                containerColor = Color(0xff00BCD4),
                icon = SimpIcons.TrendingUp,
                iconColor = Color.Black,
            )
        val Downloaded =
            LibraryTilingState(
                title = Res.string.downloaded,
                containerColor = Color(0xff4CAF50),
                icon = SimpIcons.Downloading,
                iconColor = Color.Black,
            )
        val Playlists =
            LibraryTilingState(
                title = Res.string.playlists,
                containerColor = Color(0xffD5B8FF),
                icon = SimpIcons.LibraryMusic,
                iconColor = Color(0xff4A148C),
            )
        val Collections =
            LibraryTilingState(
                title = Res.string.favorite,
                containerColor = Color(0xffffcc80),
                icon = SimpIcons.Album,
                iconColor = Color(0xff9A4D00),
            )
        val Podcasts =
            LibraryTilingState(
                title = Res.string.library_podcasts,
                containerColor = Color(0xffB3E5FC),
                icon = SimpIcons.RssFeed,
                iconColor = Color(0xff01579B),
            )
        val DownloadedSongs =
            LibraryTilingState(
                title = Res.string.downloaded_songs_tab,
                containerColor = Color(0xffC8E6C9),
                icon = SimpIcons.Downloading,
                iconColor = Color(0xff1B5E20),
            )
        val DownloadedPlaylists =
            LibraryTilingState(
                title = Res.string.playlists,
                containerColor = Color(0xffD5B8FF),
                icon = SimpIcons.LibraryMusic,
                iconColor = Color(0xff4A148C),
            )
    }
}
