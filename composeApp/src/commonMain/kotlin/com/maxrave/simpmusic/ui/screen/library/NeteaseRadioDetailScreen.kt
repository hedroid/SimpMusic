package com.maxrave.simpmusic.ui.screen.library

import androidx.compose.foundation.background
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
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
import com.maxrave.simpmusic.ui.icon.Pause
import com.maxrave.simpmusic.ui.icon.PlayArrow
import com.maxrave.simpmusic.ui.icon.KeyboardDoubleArrowUp
import com.maxrave.simpmusic.ui.icon.KeyboardArrowDown
import com.maxrave.simpmusic.ui.icon.SortAscending
import com.maxrave.simpmusic.ui.icon.SortDescending
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.ui.utils.formatCompactCount
import com.maxrave.simpmusic.viewModel.NeteaseRadioDetailViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.podcast_play_all
import simpmusic.composeapp.generated.resources.podcast_resume
import simpmusic.composeapp.generated.resources.podcast_pause
import simpmusic.composeapp.generated.resources.podcast_collapse
import simpmusic.composeapp.generated.resources.podcast_expand
import simpmusic.composeapp.generated.resources.podcast_no_programs
import simpmusic.composeapp.generated.resources.podcast_order_earliest
import simpmusic.composeapp.generated.resources.podcast_order_latest
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
                val playback by viewModel.radioPlayback.collectAsStateWithLifecycle()
                RadioHeader(
                    radio = uiState.radio,
                    subInFlight = uiState.subInFlight,
                    playback = playback,
                    hasResume = uiState.resumeProgramId != null,
                    onToggleSubscribe = { viewModel.toggleSubscribe() },
                    onPlayAllOrResume = { viewModel.playAllOrResume() },
                    onResumePlayback = { viewModel.resumePlayback() },
                )
            }
            stickyHeader {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            // 吸顶行必须有实底:滚动时节目从下面穿过,透底会叠影
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(start = 15.dp, end = 7.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.podcast_programs),
                        style = typo().headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    // 升/降序各一颗图标(官方同款箭头+横线):降序=最新在前,升序=最早在前;
                    // 无底色纯图标(2026-09-29 用户终案:圆底太丑),选中仅 primary 着色
                    SortIconButton(
                        icon = SimpIcons.SortDescending,
                        contentDescription = stringResource(Res.string.podcast_order_latest),
                        selected = !uiState.ascending,
                        onClick = { viewModel.setAscending(false) },
                    )
                    SortIconButton(
                        icon = SimpIcons.SortAscending,
                        contentDescription = stringResource(Res.string.podcast_order_earliest),
                        selected = uiState.ascending,
                        onClick = { viewModel.setAscending(true) },
                    )
                }
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
                // 期号=第几期(serialNum):最新排序首条即当前最大期号,最早排序从第 1 期起;
                // 缺期号的数据回退位置序号
                NeteaseProgramRow(
                    program = program,
                    subtitleMode = ProgramSubtitleMode.TIME,
                ) {
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
private fun SortIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint =
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.padding(6.dp),
        )
    }
}

@Composable
private fun RadioHeader(
    radio: com.maxrave.netease.model.NeteaseDjRadio?,
    subInFlight: Boolean,
    playback: com.maxrave.simpmusic.viewModel.NeteaseRadioDetailViewModel.RadioPlayback?,
    hasResume: Boolean,
    onToggleSubscribe: () -> Unit,
    onPlayAllOrResume: () -> Unit,
    onResumePlayback: () -> Unit,
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
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                if (subed) {
                    OutlinedButton(onClick = onToggleSubscribe, enabled = !subInFlight) {
                        Text(stringResource(Res.string.podcast_subscribed), style = typo().labelMedium)
                    }
                } else {
                    Button(onClick = onToggleSubscribe, enabled = !subInFlight) {
                        Text(stringResource(Res.string.podcast_subscribe), style = typo().labelMedium)
                    }
                }
                // 播放全部/继续播放/暂停(随本电台队列状态三态,官方故事FM同款第二按钮)
                // 单按钮三态:队列非本台=有记忆"继续播放"(跳上次节目+位置)/无记忆"播放全部";
                // 队列是本台=暂停/继续播放(PlayPause)
                when (playback) {
                    null ->
                        if (hasResume) {
                            Button(onClick = onResumePlayback) {
                                Icon(
                                    SimpIcons.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(stringResource(Res.string.podcast_resume), style = typo().labelMedium)
                            }
                        } else {
                            OutlinedButton(onClick = onPlayAllOrResume) {
                                Icon(
                                    SimpIcons.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(stringResource(Res.string.podcast_play_all), style = typo().labelMedium)
                            }
                        }
                    else ->
                        OutlinedButton(onClick = onPlayAllOrResume) {
                            Icon(
                                if (playback.isPlaying) SimpIcons.Pause else SimpIcons.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                stringResource(
                                    if (playback.isPlaying) Res.string.podcast_pause else Res.string.podcast_resume,
                                ),
                                style = typo().labelMedium,
                            )
                        }
                }
            }
            radio.description?.takeIf { it.isNotBlank() }?.let { desc ->
                // 超过 3 行显示"展开",点击切换全量/收起(官方同款)
                var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                var overflowing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                Text(
                    text = desc,
                    style = typo().bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { overflowing = it.hasVisualOverflow || expanded },
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (overflowing) {
                    androidx.compose.material3.TextButton(onClick = { expanded = !expanded }) {
                        Text(
                            stringResource(if (expanded) Res.string.podcast_collapse else Res.string.podcast_expand),
                            style = typo().labelMedium,
                        )
                    }
                }
            }
        }
    }
}
