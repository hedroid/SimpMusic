package org.simpmusic.cast

import android.view.ContextThemeWrapper
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.R as MediaRouterR
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

/**
 * Cast icon button backed by the original MediaRouter UI.
 *
 * DLNA renderers are published into MediaRouter by [DlnaMediaRouteProvider], so the existing
 * chooser/controller dialogs keep their original layout and behaviour while showing both Google
 * Cast and DLNA routes.
 */
@Composable
fun CastIconButton(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    val context = LocalContext.current
    val combinedRouteSelector = remember(context) { ensureDlnaMediaRouteProvider(context) }
    val tintArgb = tint.toArgb()

    // Keyed on the tint so the drawable is rebuilt only when the colour actually changes. The host
    // screen recomposes on every playback-progress tick (~10x/sec); rebuilding here would hammer
    // setRemoteIndicatorDrawable at that rate, and each call unschedules the old drawable and
    // refreshes the button's drawable state — enough to trample the press state and swallow taps.
    //
    // mutate() is essential: AppCompatResources hands back a drawable sharing its ConstantState,
    // so tinting it without mutating would recolour every other user of this resource.
    val indicator =
        remember(context, tintArgb) {
            AppCompatResources
                .getDrawable(context, R.drawable.ic_music_cast)
                ?.mutate()
                ?.apply { setTint(tintArgb) }
        }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            val themedContext = ContextThemeWrapper(viewContext, MediaRouterR.style.Theme_MediaRouter)
            MediaRouteButton(themedContext).apply {
                CastButtonFactory.setUpMediaRouteButton(viewContext.applicationContext, this)
                routeSelector = combinedRouteSelector
                // MediaRouteButton inherits Widget.AppCompat.ActionButton, which bakes in 12dp of
                // horizontal padding and scaleType=center. Removing it keeps the glyph visible in
                // the 24dp slot used by the player controls.
                setPadding(0, 0, 0, 0)
            }
        },
        update = { button ->
            button.routeSelector = combinedRouteSelector
            button.setRemoteIndicatorDrawable(indicator)
        },
    )
}
