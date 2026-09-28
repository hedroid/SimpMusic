package com.maxrave.simpmusic.ui.screen.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.LocalPlatformContext
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.maxrave.simpmusic.ui.component.CenterLoadingBox
import com.maxrave.simpmusic.ui.component.EndOfPage
import com.maxrave.simpmusic.ui.component.NormalAppBar
import com.maxrave.simpmusic.ui.icon.ArrowBackIosNew
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.ui.utils.formatCompactCount
import com.maxrave.simpmusic.viewModel.NeteaseRadioDetailViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.podcast_no_programs
import simpmusic.composeapp.generated.resources.podcast_programs
import simpmusic.composeapp.generated.resources.podcast_programs_unavailable
import simpmusic.composeapp.generated.resources.retry
import simpmusic.composeapp.generated.resources.podcast_subscribe
import simpmusic.composeapp.generated.resources.podcast_subscribed

/**
 * 网易云播客电台详情页:头图区(封面+名称+主播+订阅按钮+描述) + 节目列表(30/批近底分页)。
 * 点节目=从该节目整队起播(mainSong.id,VM 详见 [NeteaseRadioDetailViewModel])。
 */
@Composable
fun NeteaseRadioDetailScreen(
    navController: NavController,
    radioId: Long,
    radioName: String,
    innerPadding: PaddingValues,
    viewModel: NeteaseRadioDetailViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(radioId) {
        viewModel.load(radioId)
    }

    Column(Modifier.fillMaxSize()) {
        NormalAppBar(
            title = {
                Text(
                    text = uiState.radio?.name ?: radioName,
                    style = typo().titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leftIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(SimpIcons.ArrowBackIosNew, contentDescription = "Back")
                }
            },
        )
        if (uiState.loading && uiState.radio == null) {
            CenterLoadingBox(Modifier.fillMaxSize())
            return@Column
        }
        val listState = rememberLazyListState()
        // 近底 8 行触发追加(SimilarSongs/MoreSongs 同款)
        val shouldLoadMore by remember {
            derivedStateOf {
                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                last >= listState.layoutInfo.totalItemsCount - 8
            }
        }
        LaunchedEffect(shouldLoadMore, uiState.hasMore) {
            if (shouldLoadMore && uiState.hasMore && !uiState.loadingMore) {
                viewModel.loadMore()
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    bottom = innerPadding.calculateBottomPadding() + 8.dp,
                ),
        ) {
            // 电台头图区
            item(key = "radio_header") {
                RadioHeader(
                    radio = uiState.radio,
                    subInFlight = uiState.subInFlight,
                    onToggleSubscribe = { viewModel.toggleSubscribe() },
                )
            }
            item(key = "radio_programs_header") {
                Text(
                    text = stringResource(Res.string.podcast_programs),
                    style = typo().headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                )
            }
            if (uiState.programs.isEmpty() && !uiState.loading) {
                item(key = "radio_no_programs") {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text =
                                stringResource(
                                    if (uiState.programsUnavailable) {
                                        Res.string.podcast_programs_unavailable
                                    } else {
                                        Res.string.podcast_no_programs
                                    },
                                ),
                            style = typo().bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (uiState.programsUnavailable) {
                            // 音乐合集型电台 byradio 恒空 / 405 频控——静置或稍后重试
                            Button(
                                onClick = { viewModel.retry() },
                                modifier = Modifier.padding(top = 12.dp),
                            ) {
                                Text(stringResource(Res.string.retry), style = typo().labelMedium)
                            }
                        }
                    }
                }
            }
            itemsIndexed(
                uiState.programs,
                key = { _, program -> "radio_program_${program.id}" },
            ) { index, program ->
                NeteaseProgramRow(program = program) {
                    viewModel.playFrom(index)
                }
            }
            if (uiState.loadingMore) {
                item(key = "radio_loading_more") {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                }
            }
            item(key = "radio_end") {
                // contentPadding 已含 scaffold 底栏让位,页尾不双叠(同两云 tab 口径)
                EndOfPage(includeBottomBarPadding = false)
            }
        }
    }
}

@Composable
private fun RadioHeader(
    radio: com.maxrave.netease.model.NeteaseDjRadio?,
    subInFlight: Boolean,
    onToggleSubscribe: () -> Unit,
) {
    if (radio == null) return
    Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 10.dp)) {
        AsyncImage(
            model =
                ImageRequest
                    .Builder(LocalPlatformContext.current)
                    .data(radio.coverUrl)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(550)
                    .build(),
            contentDescription = radio.name,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(10.dp)),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = radio.name,
                style = typo().titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(radio.djNickname, radio.category).joinToString(" · "),
                style = typo().bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    listOfNotNull(
                        radio.subCount?.let { formatCompactCount(it) },
                        radio.programCount
                            .takeIf { it > 0 }
                            ?.let { count ->
                                "${formatCompactCount(count.toLong())} ${stringResource(Res.string.podcast_programs)}"
                            },
                    ).joinToString(" · "),
                style = typo().bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val subed = radio.subed == true
            if (subed) {
                OutlinedButton(onClick = onToggleSubscribe, enabled = !subInFlight) {
                    Text(stringResource(Res.string.podcast_subscribed), style = typo().labelMedium)
                }
            } else {
                Button(onClick = onToggleSubscribe, enabled = !subInFlight) {
                    Text(stringResource(Res.string.podcast_subscribe), style = typo().labelMedium)
                }
            }
            radio.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Text(
                    text = desc,
                    style = typo().bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
