package com.maxrave.simpmusic.ui.screen.other

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.maxrave.simpmusic.ui.component.RippleIconButton
import com.maxrave.simpmusic.ui.icon.ArrowBackIosNew
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.typo
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.ChipColors
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.third_party_libraries

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThirdPartyLibrariesScreen(
    paddingValues: PaddingValues,
    navController: NavController,
) {
    val libraries by produceLibraries {
        Res.readBytes("files/aboutlibraries.json").decodeToString()
    }
    val lazyListState = rememberLazyListState()
    val hazeState = rememberHazeState()
    val surfaceContainerHighestColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val navigateUp: () -> Unit = { navController.navigateUp() }

    LibrariesContainer(
        libraries =
            libraries?.copy(
                libraries =
                    libraries
                        ?.libraries
                        ?.distinctBy { it.name }
                        ?.toImmutableList() ?: emptyList<Library>().toImmutableList(),
            ),
        modifier =
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(top = 64.dp)
                .hazeSource(hazeState),
        lazyListState = lazyListState,
        colors =
            LibraryDefaults.libraryColors(
                licenseChipColors =
                    object : ChipColors {
                        override val containerColor: Color
                            get() = surfaceContainerHighestColor
                        override val contentColor: Color
                            get() = onSurfaceColor
                    },
            ),
    )

    TopAppBar(
        modifier =
            Modifier
                .hazeBlur(HazeInput.Sources(hazeState), HazeMaterials.ultraThin()),
        title = {
            Text(
                text = stringResource(Res.string.third_party_libraries),
                style = typo().titleMedium,
                maxLines = 1,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(Alignment.CenterVertically)
                        .clickable(onClick = navigateUp),
            )
        },
        navigationIcon = {
            Box(Modifier.padding(horizontal = 5.dp)) {
                RippleIconButton(
                    SimpIcons.ArrowBackIosNew,
                    Modifier.size(32.dp),
                    true,
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = navigateUp,
                )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
            ),
    )
}
