package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
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
import simpmusic.composeapp.generated.resources.source_account_follow

/** Independent source-account follow action displayed beside SimpMusic's local follow button. */
@Composable
fun SourceFollowButton(
    isNetease: Boolean,
    followed: Boolean?,
    pending: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val platform = if (isNetease) "网易云" else "YouTube Music"
    val accent = if (isNetease) NeteaseBrandRed else YouTubeBrandRed
    Box(
        modifier =
            modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (followed == true) accent.copy(alpha = 0.18f) else Color.Transparent)
                .border(1.5.dp, if (followed == null) accent.copy(alpha = 0.35f) else accent, CircleShape)
                .clickable(enabled = followed != null && !pending) {
                    followed?.let { onToggle(!it) }
                },
        contentAlignment = Alignment.Center,
    ) {
        if (pending) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = accent,
            )
        } else {
            Icon(
                imageVector = if (isNetease) SimpIcons.NeteaseCloudMusic else SimpIcons.YouTubeMusic,
                contentDescription = stringResource(Res.string.source_account_follow, platform),
                tint = accent.copy(alpha = if (followed == null) 0.35f else 1f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
