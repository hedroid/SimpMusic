package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maxrave.simpmusic.ui.icon.CloudOff
import com.maxrave.simpmusic.ui.icon.YouTubeMusic
import com.maxrave.simpmusic.ui.icon.Download
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.typo
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.home_offline_subtitle
import simpmusic.composeapp.generated.resources.home_offline_title
import simpmusic.composeapp.generated.resources.log_in
import simpmusic.composeapp.generated.resources.home_login_required_subtitle
import simpmusic.composeapp.generated.resources.home_login_required_title
import simpmusic.composeapp.generated.resources.listen_to_downloaded
import simpmusic.composeapp.generated.resources.retry

/**
 * Spotify-style minimal offline / error state shown on the Home tab when
 * the home feed cannot load (no network or backend failure).
 *
 * Hosts:
 * - Centered cloud-off icon
 * - Title + subtitle copy
 * - Primary action: retry the home feed
 * - Secondary action: jump to the locally downloaded library
 */
@Composable
fun OfflineErrorState(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onRetry: () -> Unit,
    onOpenDownloaded: () -> Unit,
    // 非空=登录引导形态(YT 未登录时主页的空数据不是网络错误——YTM 服务端不吐游客
    // browse 内容,别再显示"无法连接"误导;主按钮换成去登录)
    onLogIn: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .padding(horizontal = 24.dp),
        ) {
            Icon(
                imageVector = if (onLogIn != null) SimpIcons.YouTubeMusic else SimpIcons.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                modifier = Modifier.size(80.dp),
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text =
                    stringResource(
                        if (onLogIn != null) Res.string.home_login_required_title else Res.string.home_offline_title,
                    ),
                style = typo().titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    stringResource(
                        if (onLogIn != null) Res.string.home_login_required_subtitle else Res.string.home_offline_subtitle,
                    ),
                style = typo().bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = if (onLogIn != null) onLogIn else onRetry,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background,
                ),
            ) {
                Text(
                    text =
                        stringResource(
                            if (onLogIn != null) Res.string.log_in else Res.string.retry,
                        ),
                    color = MaterialTheme.colorScheme.background,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = onOpenDownloaded,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ),
            ) {
                Icon(
                    imageVector = SimpIcons.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(Res.string.listen_to_downloaded),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}
