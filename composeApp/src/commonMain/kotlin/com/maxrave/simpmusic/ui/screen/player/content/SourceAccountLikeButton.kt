package com.maxrave.simpmusic.ui.screen.player.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.maxrave.simpmusic.ui.component.NeteaseBrandRed
import com.maxrave.simpmusic.ui.component.YouTubeBrandRed
import com.maxrave.simpmusic.ui.icon.NeteaseCloudMusic
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.icon.YouTubeMusic
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.source_account_favorite

/**
 * Compact source-account state overlaid on the local favourite control.
 *
 * The large heart/star remains SimpMusic's local state. Keeping this as a badge instead of a second
 * full-size button lets the same 32/48dp slot express all four local/cloud combinations. The badge
 * is also the explicit source-account action: tapping it changes only YouTube Music/NetEase.
 */
@Composable
internal fun SourceAccountLikeBadge(
    state: NowPlayingContentState,
    actions: NowPlayingContentActions,
    modifier: Modifier = Modifier,
) {
    val remote = state.remoteLikeState
    val platform = if (state.isNeteaseSong) "网易云" else "YouTube Music"
    val accent = if (state.isNeteaseSong) NeteaseBrandRed else YouTubeBrandRed
    val available = remote.liked != null && !remote.pending
    Box(
        modifier =
            modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(
                    width = 1.dp,
                    color =
                        when {
                            remote.failed -> MaterialTheme.colorScheme.error
                            remote.liked == true -> accent
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
                        },
                    shape = CircleShape,
                ).clickable(enabled = available) {
                    remote.liked?.let { actions.onSetRemoteLiked(!it) }
                },
        contentAlignment = Alignment.Center,
    ) {
        if (remote.pending) {
            CircularProgressIndicator(
                strokeWidth = 1.5.dp,
                modifier = Modifier.size(12.dp),
                color = accent,
            )
        } else {
            Icon(
                imageVector = if (state.isNeteaseSong) SimpIcons.NeteaseCloudMusic else SimpIcons.YouTubeMusic,
                contentDescription = stringResource(Res.string.source_account_favorite, platform),
                tint =
                    when {
                        remote.failed -> MaterialTheme.colorScheme.error
                        remote.liked == true -> accent
                        else -> Color.White.copy(alpha = if (remote.liked == null) 0.35f else 0.7f)
                    },
                modifier = Modifier.size(12.dp),
            )
        }
    }
}
