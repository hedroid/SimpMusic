package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maxrave.domain.extension.now
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.utils.VersionManager
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.app_name
import simpmusic.composeapp.generated.resources.version_format

/** App scaffold padding shared with footers that need to clear the overlaid player/navigation bar. */
internal val LocalAppContentPadding = compositionLocalOf { PaddingValues() }

/** Extra floating player clearance on rail layouts, where Scaffold has no bottom bar inset. */
internal val LocalAppBottomOverlayPadding = compositionLocalOf { 0.dp }

/** Credit line shared by every page footer and the about page: "@{year} {app} {version}\nhedroid". */
@Composable
fun endOfPageCredit(): String =
    "@${now().year} " + stringResource(Res.string.app_name) + " " +
        stringResource(
            Res.string.version_format,
            VersionManager.getVersionName(),
        ) + "\nhedroid"

@Composable
fun EndOfPage(
    withoutCredit: Boolean = false,
    includeBottomBarPadding: Boolean = true,
) {
    val bottomBarPadding =
        if (includeBottomBarPadding) {
            LocalAppContentPadding.current.calculateBottomPadding()
        } else {
            0.dp
        }
    val footerHeight: Dp = 88.dp + bottomBarPadding + LocalAppBottomOverlayPadding.current
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(footerHeight),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (!withoutCredit) {
            Text(
                text = endOfPageCredit(),
                style = typo().bodySmall,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .padding(
                            top = 20.dp,
                        ).alpha(0.8f),
            )
        }
    }
}
