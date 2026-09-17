package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.maxrave.simpmusic.ui.icon.NeteaseCloudMusic
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.icon.YouTubeMusic
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.source_account_favorite

/** Source-account collection state/action overlaid on the local heart. */
@Composable
fun SourceCollectionBadge(
    isNetease: Boolean,
    saved: Boolean?,
    pending: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    inactiveTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val platform = if (isNetease) "网易云" else "YouTube Music"
    val accent = if (isNetease) NeteaseBrandRed else YouTubeBrandRed
    Box(
        modifier =
            modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(
                    width = 1.dp,
                    color = if (saved == true) accent else inactiveTint.copy(alpha = 0.65f),
                    shape = CircleShape,
                ).clickable(enabled = saved != null && !pending) {
                    saved?.let { onToggle(!it) }
                },
        contentAlignment = Alignment.Center,
    ) {
        if (pending) {
            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = accent)
        } else {
            Icon(
                imageVector = if (isNetease) SimpIcons.NeteaseCloudMusic else SimpIcons.YouTubeMusic,
                contentDescription = stringResource(Res.string.source_account_favorite, platform),
                tint =
                    if (saved == true) {
                        accent
                    } else {
                        inactiveTint.copy(alpha = if (saved == null) 0.35f else 0.75f)
                    },
                modifier = Modifier.size(12.dp),
            )
        }
    }
}
