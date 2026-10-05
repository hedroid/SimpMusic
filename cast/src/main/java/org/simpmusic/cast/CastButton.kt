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
import com.maxrave.logger.Logger

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
                // Cast 工厂内部会重新进入 CastContext 初始化——无 GMS/Cast 配置异常的设备
                // 上直接抛(initCast 已捕获降级的同一失败路径,这里没有兜底=进播放页就崩,
                // 五轮 CR)。只在 Cast 可用时才接 Google Cast 路由;不可用=纯 MediaRouteButton
                // 只吃 DLNA selector,路由选择器照常可用。initCast 幂等且从不抛,就地补一次
                // 防依赖 service 侧初始化时序;工厂调用再叠 runCatching 兜异常配置残余路径。
                if (initCast(viewContext.applicationContext)) {
                    runCatching {
                        CastButtonFactory.setUpMediaRouteButton(viewContext.applicationContext, this)
                    }.onFailure {
                        Logger.w("CastButton", "setUpMediaRouteButton failed: ${it.message}")
                    }
                }
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
