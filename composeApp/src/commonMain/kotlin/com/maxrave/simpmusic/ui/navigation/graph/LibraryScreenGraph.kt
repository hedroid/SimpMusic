package com.maxrave.simpmusic.ui.navigation.graph

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.navigation.NavController
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.maxrave.simpmusic.ui.navigation.destination.library.LibraryCollectionDestination
import com.maxrave.simpmusic.ui.navigation.destination.library.LibraryDynamicPlaylistDestination
import com.maxrave.simpmusic.ui.screen.library.LibraryCollectionScreen
import com.maxrave.simpmusic.ui.screen.library.LibraryDynamicPlaylistScreen

@ExperimentalMaterial3Api
fun NavGraphBuilder.libraryScreenGraph(
    innerPadding: PaddingValues,
    navController: NavController,
) {
    composable<LibraryCollectionDestination> { entry ->
        val data = entry.toRoute<LibraryCollectionDestination>()
        LibraryCollectionScreen(
            innerPadding = innerPadding,
            navController = navController,
            type = data.type,
        )
    }

    // 转场纯 fade(不带 slide):滑动进入页会横扫覆盖源页的列表区,库页 haze 玻璃顶栏
    // 逐帧采样到剧变内容表现为"顶栏闪一下";fade 均匀淡入无扫掠。仅这对目的地,
    // 其它导航保持全局滑动观感。
    composable<LibraryDynamicPlaylistDestination>(
        enterTransition = { fadeIn(animationSpec = tween(300)) },
        exitTransition = { fadeOut(animationSpec = tween(300)) },
        popEnterTransition = { fadeIn(animationSpec = tween(300)) },
        popExitTransition = { fadeOut(animationSpec = tween(300)) },
    ) { entry ->
        val data = entry.toRoute<LibraryDynamicPlaylistDestination>()
        LibraryDynamicPlaylistScreen(
            innerPadding = innerPadding,
            navController = navController,
            type = data.type,
        )
    }
}
