package com.maxrave.simpmusic.ui.screen.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.maxrave.simpmusic.ui.component.MediaRow
import com.maxrave.simpmusic.ui.navigation.destination.list.NeteasePodcastCategoryDestination
import com.maxrave.simpmusic.ui.navigation.destination.list.NeteaseRadioDetailDestination
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.extension.formatTimeAgo
import kotlinx.datetime.toLocalDateTime
import com.maxrave.simpmusic.ui.utils.formatCompactCount
import com.maxrave.simpmusic.viewModel.NeteasePodcastViewModel
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.podcast_listens
import simpmusic.composeapp.generated.resources.podcast_badge_bought
import simpmusic.composeapp.generated.resources.podcast_badge_paid
import simpmusic.composeapp.generated.resources.podcast_badge_vip
import simpmusic.composeapp.generated.resources.podcast_featured_radios
import simpmusic.composeapp.generated.resources.podcast_guess_you_like
import simpmusic.composeapp.generated.resources.podcast_latest_programs
import simpmusic.composeapp.generated.resources.podcast_my_subscriptions
import simpmusic.composeapp.generated.resources.podcast_new_radios
import simpmusic.composeapp.generated.resources.podcast_program_toplist
import simpmusic.composeapp.generated.resources.podcast_tap_to_listen
import simpmusic.composeapp.generated.resources.podcast_toplist_radios

/**
 * 网易云播客 chip 页(LibraryScreen Crossfade 分支挂载,VM 是 Koin single)。
 * 区块形态按内容类型二分(2026-09-29 二稿,对齐 Apple Podcasts/Spotify/网易官方):
 * **电台(频道)=横滑卡货架**(订阅对象,封面即主体,统一 160dp)——我的订阅/猜你喜欢/
 * 精选/热门榜/新晋榜;**节目(剧集)=列表行**(可播放内容单元,信息密度高)——最新节目/
 * 热门节目榜(行首排名,各取前 10)。上一稿全货架化是过度统一,剧集内容业内均为列表。
 * 分类 chips 拉 /djradio/hot 电台列表(program 端点 cateId 被服务端忽略是另一回事)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeteasePodcastScreen(
    innerPadding: PaddingValues,
    navController: NavController,
    viewModel: NeteasePodcastViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pullToRefreshState = rememberPullToRefreshState()

    val hasAnyContent =
        uiState.categories.isNotEmpty() || uiState.myRadios.isNotEmpty() ||
            uiState.programs.isNotEmpty() || uiState.personalizedRadios.isNotEmpty() ||
            uiState.recommendRadios.isNotEmpty() || uiState.hotRadios.isNotEmpty() ||
            uiState.newRadios.isNotEmpty() || uiState.programToplist.isNotEmpty()

    PullToRefreshBox(
        modifier = Modifier.fillMaxSize(),
        state = pullToRefreshState,
        onRefresh = { viewModel.refresh() },
        // 指示器=手势受理确认(≤半圈口径),后台工作静默(网易主页同款)
        isRefreshing = false,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = false,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = innerPadding.calculateTopPadding()),
                containerColor = PullToRefreshDefaults.indicatorContainerColor,
                color = PullToRefreshDefaults.indicatorColor,
                maxDistance = PullToRefreshDefaults.PositionalThreshold,
            )
        },
    ) {
        if (!hasAnyContent && uiState.programsLoading) {
            CenterLoadingBox(Modifier.fillMaxSize())
            return@PullToRefreshBox
        }
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 8.dp,
                ),
        ) {
            // 分类浏览 chips(点击=进分类电台列表页)
            if (uiState.categories.isNotEmpty()) {
                item(key = "podcast_categories") {
                    PodcastCategoryChipsRow(
                        categories = uiState.categories,
                        navController = navController,
                    )
                }
            }

            // 我的订阅(未登录/空即隐藏)
            if (uiState.myRadios.isNotEmpty()) {
                item(key = "podcast_my_subscriptions") {
                    MediaRow(title = stringResource(Res.string.podcast_my_subscriptions), modifier = Modifier.padding(top = 16.dp)) {
                        items(uiState.myRadios, key = { "my_radio_${it.id}" }) { radio ->
                            NeteaseDjRadioCard(radio = radio) {
                                navController.navigate(
                                    NeteaseRadioDetailDestination(radioId = radio.id, radioName = radio.name),
                                )
                            }
                        }
                    }
                }
            }

            // 猜你喜欢(需登录;未登录服务端回空,区块隐藏)
            if (uiState.personalizedRadios.isNotEmpty()) {
                item(key = "podcast_personalized") {
                    MediaRow(title = stringResource(Res.string.podcast_guess_you_like), modifier = Modifier.padding(top = 16.dp)) {
                        items(uiState.personalizedRadios, key = { "personalized_${it.id}" }) { radio ->
                            NeteaseDjRadioCard(radio = radio) {
                                navController.navigate(
                                    NeteaseRadioDetailDestination(radioId = radio.id, radioName = radio.name),
                                )
                            }
                        }
                    }
                }
            }

            // 最新节目(剧集=列表行,Apple New Episodes/Spotify 剧集同款;取前10,
            // 点击即播整队——整队仍是全部节目,列表只是展示窗口)
            if (uiState.programsLoading && uiState.programs.isEmpty()) {
                item(key = "podcast_programs_loading") {
                    Box(
                        Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    }
                }
            } else {
                item(key = "podcast_programs_header") {
                    Text(
                        text = stringResource(Res.string.podcast_latest_programs),
                        style = typo().headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 15.dp).padding(top = 26.dp),
                    )
                }
                items(
                    uiState.programs.take(LATEST_PROGRAMS_SHOWN),
                    key = { "program_${it.id}" },
                ) { program ->
                    NeteaseProgramRow(program = program) {
                        viewModel.playProgram(uiState.programs.indexOf(program))
                    }
                }
            }

            // 精选电台
            if (uiState.recommendRadios.isNotEmpty()) {
                item(key = "podcast_recommend") {
                    MediaRow(title = stringResource(Res.string.podcast_featured_radios), modifier = Modifier.padding(top = 16.dp)) {
                        items(uiState.recommendRadios, key = { "recommend_${it.id}" }) { radio ->
                            NeteaseDjRadioCard(radio = radio) {
                                navController.navigate(
                                    NeteaseRadioDetailDestination(radioId = radio.id, radioName = radio.name),
                                )
                            }
                        }
                    }
                }
            }

            // 热门电台榜(独立 shelf,带排名)
            if (uiState.hotRadios.isNotEmpty()) {
                item(key = "podcast_toplist_hot") {
                    MediaRow(title = stringResource(Res.string.podcast_toplist_radios), modifier = Modifier.padding(top = 16.dp)) {
                        itemsIndexed(uiState.hotRadios, key = { _, r -> "toplist_${r.id}" }) { index, radio ->
                            NeteaseDjRadioCard(radio = radio, rank = index + 1) {
                                navController.navigate(
                                    NeteaseRadioDetailDestination(radioId = radio.id, radioName = radio.name),
                                )
                            }
                        }
                    }
                }
            }

            // 新晋电台榜(独立 shelf,带排名)
            if (uiState.newRadios.isNotEmpty()) {
                item(key = "podcast_toplist_new") {
                    MediaRow(title = stringResource(Res.string.podcast_new_radios), modifier = Modifier.padding(top = 16.dp)) {
                        itemsIndexed(uiState.newRadios, key = { _, r -> "new_toplist_${r.id}" }) { index, radio ->
                            NeteaseDjRadioCard(radio = radio, rank = index + 1) {
                                navController.navigate(
                                    NeteaseRadioDetailDestination(radioId = radio.id, radioName = radio.name),
                                )
                            }
                        }
                    }
                }
            }

            // 热门节目榜(剧集榜单=列表行+行首排名,Apple Top Charts 同款;取前10)
            if (uiState.programToplist.isNotEmpty()) {
                item(key = "podcast_program_toplist_header") {
                    Text(
                        text = stringResource(Res.string.podcast_program_toplist),
                        style = typo().headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 15.dp).padding(top = 26.dp),
                    )
                }
                itemsIndexed(
                    uiState.programToplist.take(TOPLIST_PROGRAMS_SHOWN),
                    key = { _, p -> "program_toplist_${p.id}" },
                ) { index, program ->
                    NeteaseProgramRow(
                        program = program,
                        rank = index + 1,
                    ) {
                        viewModel.playProgramToplist(uiState.programToplist.indexOf(program))
                    }
                }
            }

            item(key = "podcast_end") {
                // contentPadding 已含 scaffold 底栏让位,页尾不双叠(同两云 tab 口径)
                EndOfPage(includeBottomBarPadding = false)
            }
        }
    }
}

/** 发现页列表区块的展示窗口:剧集类内容取前 N 条(全量横滑无"页底",列表太长喧宾夺主) */
private const val LATEST_PROGRAMS_SHOWN = 10
private const val TOPLIST_PROGRAMS_SHOWN = 10

/** 分类浏览 chips(点击=进分类电台列表页;与已移除的"cateId 过滤节目"假过滤不同,这里拉电台列表) */
@Composable
private fun PodcastCategoryChipsRow(
    categories: List<com.maxrave.netease.model.NeteasePodcastCategory>,
    navController: NavController,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 15.dp, vertical = 4.dp)
                .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        categories.forEach { category ->
            FilterChip(
                selected = false,
                onClick = {
                    navController.navigate(
                        NeteasePodcastCategoryDestination(categoryId = category.id, categoryName = category.name),
                    )
                },
                label = { Text(category.name, style = typo().labelMedium) },
            )
        }
    }
}

/** 电台横滑卡(160dp 定宽,与主页货架 tile 同尺寸;榜单带排名角标) */
@Composable
internal fun NeteaseDjRadioCard(
    radio: com.maxrave.netease.model.NeteaseDjRadio,
    rank: Int? = null,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(160.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
    ) {
        Box {
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
                        .size(160.dp)
                        .clip(RoundedCornerShape(10.dp)),
            )
            if (rank != null) {
                Text(
                    text = rank.toString(),
                    style = typo().titleMedium,
                    color = Color.White,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        // 标题独立(1 行就 1 行、2 行截断),播主永远紧随其后的下一行——单行标题→播主在第 2 行,
        // 两行标题→播主在第 3 行,行与行之间无空隙(2026-09-29 用户定案语义)
        Text(
            text = radio.name,
            style = typo().titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            // 固定占两行:货架高度恒定,1/2行标题卡混排不再引起整行重排跳动;播主仍紧随其后
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = radio.djNickname.orEmpty(),
            style = typo().bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 节目整行(封面 56dp + 标题 + 电台·主播·N次收听 + 时长);点击=从该节目整队起播。
 *  paid 节目(付费未购)整行 0.4 alpha 置灰——与歌单灰歌同款形态;点击仍可播,
 *  走 isAvailable=false 的三档动作(默认 SKIP),不再播 26KB 试听片段。 */
/** 副标题模式:RADIO=电台·主播·收听量(跨电台列表:主页最新节目/节目榜);TIME=发布时间·收听量
 *  (电台详情页——头部已有电台/主播信息,行内重复冗余,2026-09-29 用户反馈) */
enum class ProgramSubtitleMode { RADIO, TIME }

@Composable
internal fun NeteaseProgramRow(
    program: com.maxrave.netease.model.NeteaseDjProgram,
    rank: Int? = null,
    subtitleMode: ProgramSubtitleMode = ProgramSubtitleMode.RADIO,
    onClick: () -> Unit,
) {
    // 置灰修饰(灰歌同款):paid 行整体 0.4 alpha
    val dimModifier = if (program.paid) Modifier.alpha(0.4f) else Modifier
    Row(
        modifier =
            dimModifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 15.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 榜单行首排名(Apple Top Charts 同款;普通列表不传)
        if (rank != null) {
            Text(
                text = rank.toString(),
                style = typo().titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp).width(24.dp),
            )
        }
        AsyncImage(
            model =
                ImageRequest
                    .Builder(LocalPlatformContext.current)
                    .data(program.coverUrl)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(550)
                    .build(),
            contentDescription = program.name,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
        ) {
            Text(
                text = program.name,
                style = typo().titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                // 最多两行:长标题完整显示到两行,短标题单行自然高度(2026-09-29 用户终案)
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    when (subtitleMode) {
                        ProgramSubtitleMode.RADIO ->
                            listOfNotNull(
                                program.radioName,
                                program.djNickname,
                            ).joinToString(" · ")
                        ProgramSubtitleMode.TIME ->
                            program.createTimeMs?.let { ms ->
                                // 绝对发布日期(官方同款),不用相对时间(2026-09-29 用户定案)
                                kotlinx.datetime.Instant.fromEpochMilliseconds(ms)
                                    .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                                    .date.toString()
                            } ?: ""
                    } + program.listenerCount?.let {
                        " · " + stringResource(Res.string.podcast_listens, formatCompactCount(it))
                    }.orEmpty(),
                style = typo().bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ProgramStateBadge(program = program)
        Text(
            text = formatProgramDuration(program.durationMs),
            style = typo().bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 节目可听性角标(付费未购/VIP 内容/已购买),放在时长左侧。付费=当前只有试听态
 *  (整行置灰,点播走三档动作);VIP/已购可完整播,角标仅提示。 */
@Composable
private fun ProgramStateBadge(program: com.maxrave.netease.model.NeteaseDjProgram) {
    val (textRes, color) =
        when {
            program.paid -> Res.string.podcast_badge_paid to MaterialTheme.colorScheme.error
            program.bought -> Res.string.podcast_badge_bought to MaterialTheme.colorScheme.secondary
            program.vip -> Res.string.podcast_badge_vip to MaterialTheme.colorScheme.primary
            else -> return
        }
    Text(
        text = stringResource(textRes),
        style = typo().labelSmall,
        color = color,
        modifier =
            Modifier
                .padding(end = 6.dp)
                .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

private fun formatProgramDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
