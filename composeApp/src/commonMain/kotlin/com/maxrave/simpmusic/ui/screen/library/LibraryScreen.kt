package com.maxrave.simpmusic.ui.screen.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.maxrave.common.LibraryChipType
import com.maxrave.domain.source.MusicSource
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.data.type.PlaylistType
import com.maxrave.domain.utils.LocalResource
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.extension.copy
import com.maxrave.simpmusic.extension.isScrollingUp
import com.maxrave.simpmusic.ui.component.AddToPlaylistModalBottomSheet
import com.maxrave.simpmusic.ui.component.Chip
import com.maxrave.simpmusic.ui.component.EndOfPage
import com.maxrave.simpmusic.ui.component.GridLibraryPlaylist
import com.maxrave.simpmusic.ui.component.LibraryItem
import com.maxrave.simpmusic.ui.component.LibraryItemState
import com.maxrave.simpmusic.ui.component.LibraryItemType
import com.maxrave.simpmusic.ui.component.LibraryTilingBox
import com.maxrave.simpmusic.ui.component.LibraryTilingItem
import com.maxrave.simpmusic.ui.component.LibraryTilingState
import com.maxrave.simpmusic.ui.component.ListenTogetherIconButton
import com.maxrave.simpmusic.ui.component.RippleIconButton
import com.maxrave.simpmusic.ui.component.rememberSurfaceDarkColors
import com.maxrave.simpmusic.ui.component.selection.SelectedSongsBottomSheet
import com.maxrave.simpmusic.ui.component.selection.SongSelectionTopAppBar
import com.maxrave.simpmusic.ui.component.selection.rememberSongSelectionState
import com.maxrave.simpmusic.ui.icon.Groups
import com.maxrave.simpmusic.ui.icon.PeopleAlt
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.navigation.destination.home.ListenTogetherDestination
import com.maxrave.simpmusic.ui.navigation.destination.library.LibraryCollectionDestination
import com.maxrave.simpmusic.ui.navigation.destination.library.LibraryDynamicPlaylistDestination
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.LibraryViewModel
import com.maxrave.simpmusic.viewModel.NeteasePodcastViewModel
import com.maxrave.simpmusic.viewModel.SongSelectionViewModel
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import com.maxrave.simpmusic.viewModel.LibraryDynamicPlaylistViewModel
import com.maxrave.simpmusic.viewModel.SharedViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.download_management
import simpmusic.composeapp.generated.resources.listen_together
import simpmusic.composeapp.generated.resources.netease_podcast

import simpmusic.composeapp.generated.resources.chart
import simpmusic.composeapp.generated.resources.cancel
import simpmusic.composeapp.generated.resources.create
import simpmusic.composeapp.generated.resources.delete
import simpmusic.composeapp.generated.resources.downloaded_collections
import simpmusic.composeapp.generated.resources.downloaded_playlists
import simpmusic.composeapp.generated.resources.favorite
import simpmusic.composeapp.generated.resources.favorite_playlists
import simpmusic.composeapp.generated.resources.favorite_podcasts
import simpmusic.composeapp.generated.resources.library
import simpmusic.composeapp.generated.resources.library_podcasts
import simpmusic.composeapp.generated.resources.mix_for_you
import simpmusic.composeapp.generated.resources.no_YouTube_playlists
import simpmusic.composeapp.generated.resources.no_charts_found
import simpmusic.composeapp.generated.resources.no_favorite_playlists
import simpmusic.composeapp.generated.resources.no_favorite_podcasts
import simpmusic.composeapp.generated.resources.no_playlists_added
import simpmusic.composeapp.generated.resources.no_playlists_downloaded
import simpmusic.composeapp.generated.resources.playlist_name
import simpmusic.composeapp.generated.resources.playlist_name_cannot_be_empty
import simpmusic.composeapp.generated.resources.playlists
import simpmusic.composeapp.generated.resources.remove_download_message
import simpmusic.composeapp.generated.resources.remove_download_title
import simpmusic.composeapp.generated.resources.simpmusic_charts
import simpmusic.composeapp.generated.resources.wrapped
import simpmusic.composeapp.generated.resources.your_library
import simpmusic.composeapp.generated.resources.your_netease
import simpmusic.composeapp.generated.resources.your_playlists
import simpmusic.composeapp.generated.resources.your_youtube_music

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    innerPadding: PaddingValues,
    viewModel: LibraryViewModel = koinViewModel(),
    navController: NavController,
    onScrolling: (onTop: Boolean) -> Unit = {},
) {
    val density = LocalDensity.current

    val loggedIn by viewModel.youtubeLoggedIn.collectAsStateWithLifecycle(initialValue = false)
    // Wrapped(年度回顾)chip 已隐藏(2026-09-30):recaps/localTrackingEnabled 两个收集器随之
    // 下岗,恢复 chip 时一并加回(monthlyRecaps/localTrackingEnabled + getMonthlyRecaps)。
    val nowPlaying by viewModel.nowPlayingVideoId.collectAsStateWithLifecycle()
    val youTubePlaylist by viewModel.youTubePlaylist.collectAsStateWithLifecycle()
    val youTubeLikedPlaylists by viewModel.youTubeLikedPlaylists.collectAsStateWithLifecycle()
    val youTubeAutoPlaylists by viewModel.youTubeAutoPlaylists.collectAsStateWithLifecycle()
    val youTubeAlbums by viewModel.youTubeAlbums.collectAsStateWithLifecycle()
    val followedYTArtists by viewModel.followedYTArtists.collectAsStateWithLifecycle()
    val listCanvasSong by viewModel.listCanvasSong.collectAsStateWithLifecycle()
    val yourLocalPlaylist by viewModel.yourLocalPlaylist.collectAsStateWithLifecycle()
    val favoritePlaylist by viewModel.favoritePlaylist.collectAsStateWithLifecycle()
    val downloadedPlaylist by viewModel.downloadedPlaylist.collectAsStateWithLifecycle()
    val favoritePodcasts by viewModel.favoritePodcasts.collectAsStateWithLifecycle()
    val chartPlaylists by viewModel.chartPlaylists.collectAsStateWithLifecycle()
    val recentlyAdded by viewModel.recentlyAdded.collectAsStateWithLifecycle()
    val neteaseLoggedIn by viewModel.neteaseLoggedIn.collectAsStateWithLifecycle(initialValue = false)
    val neteasePlaylist by viewModel.neteasePlaylist.collectAsStateWithLifecycle()
    val subscribedArtists by viewModel.subscribedArtists.collectAsStateWithLifecycle()
    val starredAlbums by viewModel.starredAlbums.collectAsStateWithLifecycle()
    val neteaseRefreshing by viewModel.neteaseRefreshing.collectAsStateWithLifecycle()
    val youTubeRefreshing by viewModel.youTubeRefreshing.collectAsStateWithLifecycle()
    val ownNeteasePlaylistIds by viewModel.ownNeteasePlaylistIds.collectAsStateWithLifecycle()
    val neteaseLikedPlaylistId by viewModel.neteaseLikedPlaylistId.collectAsStateWithLifecycle()

    val selectionState = rememberSongSelectionState()
    val selectionViewModel: SongSelectionViewModel = koinViewModel()
    // 网易云播客 chip 页 VM(Koin single:数据随 single 存活,tab 往返零重拉)
    val neteasePodcastViewModel: NeteasePodcastViewModel = koinViewModel()
    var showSelectionSheet by rememberSaveable { mutableStateOf(false) }
    var showSelectionAddToPlaylist by rememberSaveable { mutableStateOf(false) }
    val allSelectedDownloaded by selectionViewModel.allSelectedDownloaded.collectAsStateWithLifecycle()
    LaunchedEffect(showSelectionSheet) {
        if (showSelectionSheet) selectionViewModel.checkAllDownloaded(selectionState.selected.toList())
    }
    // The playlist/album tile long-pressed in the downloaded grid, awaiting the confirm dialog.
    // Plain remember: the payload is not saveable and the dialog is short-lived enough that a
    // process death mid-confirm can just start over.
    var removeDownloadTarget by remember { mutableStateOf<PlaylistType?>(null) }
    // 顶栏头像=当前音源登录账号的头像(用户 2026-09-30):网易源=云村账号,其它=Google 账号
    val accountThumbnail by viewModel.sourceAccountThumbnail.collectAsStateWithLifecycle()
    val hazeState =
        rememberHazeState()

    // 网格页内容留白:锁定"展开态"高度(maxOf),不跟随收起动画——contentPadding 若逐帧
    // 跟随动画,LazyGrid 每帧重锚定首可见项→触发新一轮滚动上报→翻转标题显隐→又改
    // padding,自持振荡(用户实测"滑到底顶部来回跳");锁定后标题行收/展是纯覆盖层,
    // 内容零位移。首页不受此害是因为它的留白是第 0 项内部的 Spacer,滚远后改高度
    // 不碰当前锚点。只增不减,进程重启从展开态起步(下面 showTitleBar 非 saveable)。
    var topAppBarHeight by remember {
        mutableStateOf(0.dp)
    }
    // 下载管理页专用:实时(随动画)高度。该页的"歌曲/歌单"切换行是列表外层的固定头,
    // 锁定展开高度会让它在收起态钉在 y≈480,chips 行下方留一条永不消失的空白带
    // (用户实测"滑到底部后顶部显示一半下不去")。它消费的是 Column 外层 padding,
    // 列表内部坐标不参与——变高变矮只改视口大小,不触发列表重锚定,无振荡风险,
    // 与网格页的列表 contentPadding 性质不同。
    var topBarLiveHeight by remember {
        mutableStateOf(0.dp)
    }
    var showAddSheet by remember { mutableStateOf(false) }

    LaunchedEffect(nowPlaying) {
        Logger.w("LibraryScreen", "Check nowPlaying: $nowPlaying")
        viewModel.getRecentlyAdded()
    }

    val chipRowState = rememberLazyListState()
    val currentFilter by viewModel.currentScreen.collectAsStateWithLifecycle()
    // 顶栏第一排(标题行)随内容滚动收起——与首页顶栏同款效果(用户 2026-09-30)。
    // 信号沿用各 chip 页的 onScrolling(true=在顶/上滑回顶,false=深入内容下滑):
    // 在 Crossfade 内容里按来源页过滤后驱动标题行显隐,原样转发给 App 驱动底栏。
    // 非 saveable:进程重启恒从展开态起步,与上面 padding 锁定值天然一致,避免
    // "保存了收起态+padding 重新锁定小值"的错位入场。
    var showTitleBar by remember { mutableStateOf(true) }
    // 标题行翻转的静默窗截止时刻(见 Crossfade 里 tabScrolling 的说明)
    var suppressTitleFlipUntilNs by remember { mutableStateOf(0L) }
    // 静默窗内被吞掉的最后一个"翻转意愿"(null=无):窗到期回放,防用户快速反向滑/
    // 切到保存了相反滚动态的页时终态被永久丢弃(CR-30)。仅锁定高度页(网格各 chip)
    // 记录——下载管理页(实时高度)的窗内上报可能是收展动画的几何反馈,回放=振荡回归
    var pendingTitleFlip by remember { mutableStateOf<Boolean?>(null) }
    val openLibraryPlaylists = {
        navController.navigate(LibraryCollectionDestination(LibraryChipType.LOCAL_PLAYLIST.name))
    }
    val openLibraryCollections = {
        navController.navigate(LibraryCollectionDestination(LibraryChipType.FAVORITE_PLAYLIST.name))
    }
    val openLibraryPodcasts = {
        navController.navigate(LibraryCollectionDestination(LibraryChipType.FAVORITE_PODCAST.name))
    }
    val openLibraryDownloads = {
        navController.navigate(LibraryCollectionDestination(LibraryChipType.DOWNLOADED_PLAYLIST.name))
    }

    // chip 按音源显隐(用户 2026-09-30 定稿):网易云/网易播客=网易源+网易登录;
    // 您的 YouTube Music/排行榜=YT 源+YT 登录;下载管理恒可见。selectedSource 是
    // StateFlow,无 initialValue 收集即首帧取 .value(VM 新建、源键首读落地前是
    // 空串,按 YT 侧处理,与 App 默认源同款口径);两个登录态是 DataStore 冷流,
    // 重组首帧恒为 initialValue,回落判定因此不吃这里的值(见下)。
    val selectedSource by viewModel.selectedSource.collectAsStateWithLifecycle()
    val sourceIsNetease = selectedSource == MusicSource.NETEASE.name
    val neteaseChipsVisible = neteaseLoggedIn && sourceIsNetease
    val ytChipsVisible = loggedIn && !sourceIsNetease

    // "您的库"chip 页已下线;下载管理升为顶层 chip,本地歌单等独立路由保留但不再从
    // chip 行进入。顺序(用户 2026-09-20 定序):网易云 → YouTube Music → 排行榜 →
    // 下载管理(Wrapped 年度回顾 chip 已于 2026-09-30 隐藏,恢复时加回
    // LibraryChipType.WRAPPED 并放开其内容分支);"进库默认选第一个可见 chip"的
    // 取值顺序与此保持一致。
    val topLevelLibraryChips =
        listOf(
            LibraryChipType.NETEASE_PLAYLIST,
            // 网易云播客(仅网易登录显示,未登录不出现——与您的网易云同款门控)
            LibraryChipType.NETEASE_PODCAST,
            LibraryChipType.YOUTUBE_MUSIC_PLAYLIST,
            LibraryChipType.CHART,
            LibraryChipType.DOWNLOADED_PLAYLIST,
        )
    // 上面门控的落地点:把显隐规则折成实际渲染的 chip 列表(索引同时供下方滚动 effect 用)
    val visibleLibraryChips =
        topLevelLibraryChips.filter { type ->
            val neteaseChip = type == LibraryChipType.NETEASE_PLAYLIST || type == LibraryChipType.NETEASE_PODCAST
            val ytChip = type == LibraryChipType.YOUTUBE_MUSIC_PLAYLIST || type == LibraryChipType.CHART
            (neteaseChip && neteaseChipsVisible) || (ytChip && ytChipsVisible) ||
                (!neteaseChip && !ytChip)
        }
    // 选中 chip 必须可见(UI-CR-15):程序性变化(回落弹回/切源/登出重定向/深滚动位恢复)
    // 之后把选中项滚入视野——完全可见时不动,避免与用户手点可见 chip 的滚动打架。
    // 可见性也作 key:chip 集合增删会让同一选中项的 index 平移(如网易两 chip 插到前面)。
    LaunchedEffect(currentFilter, neteaseChipsVisible, ytChipsVisible) {
        val index = visibleLibraryChips.indexOf(currentFilter)
        if (index >= 0) {
            val layoutInfo = chipRowState.layoutInfo
            val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            val fullyVisible =
                itemInfo != null &&
                    itemInfo.offset >= layoutInfo.viewportStartOffset &&
                    itemInfo.offset + itemInfo.size <= layoutInfo.viewportEndOffset
            if (!fullyVisible) {
                chipRowState.animateScrollToItem(index)
            }
        }
    }

    // 回落判定:选中 chip 被音源/登录态变化藏掉时弹回第一个可见 chip。门控三流
    // (网易登录/YT 登录/音源)在 effect 内 combine 后消费——组合级 state 在进歌单
    // 详情返回等重组首帧是 initialValue(false),会把"未初始化"当"未登录",曾把
    // "您的网易云"选中误弹回下载管理(2026-09-30 回归);combine 的首个 emission
    // 保证判定时三条门控全是真值。已下线 enum(YOUR_LIBRARY/WRAPPED/混源旧值等)
    // 走 else 分支同样在此回落,下方 load effect 的重定向分支已并入本处。
    LaunchedEffect(currentFilter) {
        val filter = currentFilter
        combine(
            viewModel.neteaseLoggedIn,
            viewModel.youtubeLoggedIn,
            viewModel.selectedSource,
        ) { netease, yt, source -> Triple(netease, yt, source) }
            .collect { (netease, yt, source) ->
                // 空串只出现在 VM 新建、selectedSource 镜像首值落地前(DataStore 键
                // 恒有非空默认),此时不做判定,等真值落地 combine 重发——否则网易
                // 用户首进库页会被空串当 YT 侧误判。
                if (source.isEmpty()) return@collect
                val neteaseVisible = netease && source == MusicSource.NETEASE.name
                val ytVisible = yt && source != MusicSource.NETEASE.name
                val visible =
                    when (filter) {
                        LibraryChipType.NETEASE_PLAYLIST, LibraryChipType.NETEASE_PODCAST -> neteaseVisible
                        LibraryChipType.YOUTUBE_MUSIC_PLAYLIST, LibraryChipType.CHART -> ytVisible
                        LibraryChipType.DOWNLOADED_PLAYLIST -> true
                        else -> false
                    }
                if (!visible) {
                    viewModel.setCurrentScreen(
                        when {
                            neteaseVisible -> LibraryChipType.NETEASE_PLAYLIST
                            ytVisible -> LibraryChipType.YOUTUBE_MUSIC_PLAYLIST
                            else -> LibraryChipType.DOWNLOADED_PLAYLIST
                        },
                    )
                }
            }
    }

    LaunchedEffect(currentFilter) {
        // 标题行显隐不做切页复位:每个 chip 页自己上报滚动状态(含播客/下载管理),
        // Crossfade 按页恢复滚动位置(SaveableStateProvider),首帧上报的就是真实
        // 状态(深处=false)——复位会跟它打架,把刚展开的标题又压回去。
        Logger.w(
            "LIBPROBE",
            "currentFilter=$currentFilter netease=${neteasePlaylist::class.simpleName}/${subscribedArtists::class.simpleName}/${starredAlbums::class.simpleName}",
        )
        when (currentFilter) {
            LibraryChipType.YOUTUBE_MUSIC_PLAYLIST -> {
                // 未加载过、或"登录态下系统歌单置顶行为空"(首拉撞上 cookie 恢复竞态/
                // split 间歇失败时 auto 分区空——登录账号必有 LM/SE,空=那次数据不可信)
                // 才拉。不看 created 空:删除唯一自建歌单后 YTM 服务端删除是异步的(~1min),
                // 按空重拉会把还没删掉的歌单又拉回来(2026-09-22)。子页写操作走
                // LibraryMutationBus 本地回写,不依赖返回时刷新。
                if (youTubePlaylist !is LocalResource.Success ||
                    youTubeAutoPlaylists.data.isNullOrEmpty()
                ) {
                    viewModel.getYouTubeLibrary()
                }
            }

            // "您的网易云"三分区(歌单/关注的歌手/收藏的专辑):空数据才拉;子页动作本地回写。
            // 登出回落由 VM 的 neteaseCookie collect 负责,这里不会停在无数据的分区上。
            LibraryChipType.NETEASE_PLAYLIST -> {
                if (neteasePlaylist !is LocalResource.Success) {
                    viewModel.getNeteaseLibrary()
                }
            }

            // 网易云播客:首拉整页;之后每次选中只静默刷新订阅区(详情页订阅动作要反映回来)
            LibraryChipType.NETEASE_PODCAST -> {
                neteasePodcastViewModel.onPageSelected()
            }

            LibraryChipType.DOWNLOADED_PLAYLIST -> {
                viewModel.getDownloadedPlaylist()
            }

            LibraryChipType.CHART -> {
                if (chartPlaylists.data.isNullOrEmpty()) {
                    viewModel.getChartPlaylists()
                }
            }

            // 已下线 enum(YOUTUBE_MIX_FOR_YOU/WRAPPED/YOUR_LIBRARY/LOCAL_PLAYLIST/
            // FAVORITE_*)的重定向已并入上方的回落 effect(统一吃 combine 真值),
            // 这里只管各 chip 页的数据装载;K2 要求枚举 when 穷尽,else 吸掉旧值。
            else -> {}
        }
    }

    Crossfade(
        modifier = Modifier.hazeSource(hazeState),
        targetState = currentFilter,
    ) { filter ->
        // 只认"当前 chip 页"的滚动上报:Crossfade 过渡期旧页仍在组合、fling 可能还没停,
        // 旧页迟到的 false 会把切页时刚复位的标题行又压回去(实测竞态);对新页无影响。
        // 转发给 App 的底栏信号维持原行为(新旧页都转发,与改造前一致)。
        val tabScrolling: (onTop: Boolean) -> Unit = { onTop ->
            if (filter == viewModel.currentScreen.value) {
                if (onTop == showTitleBar) {
                    // 与当前态一致=终态已对齐,清掉待回放
                    pendingTitleFlip = null
                } else if (System.nanoTime() >= suppressTitleFlipUntilNs) {
                    // 翻转后开 500ms 静默窗,窗内吞掉后续翻转:标题收/展动画会改变列表几何
                    // (下载管理页=视口高度,吃实时高度),在列表底部触发钳制回拉→index 逐帧
                    // 变化→又触发上报→反向翻转→动画重启,自持振荡(用户 2026-09-30 四轮
                    // 实测"下载页滑到底部开始跳")。动画的全部几何反馈都落在窗内;真手势的
                    // 方向反转在窗外,不受影响。
                    suppressTitleFlipUntilNs = System.nanoTime() + 500_000_000L
                    pendingTitleFlip = null
                    showTitleBar = onTop
                } else if (filter != LibraryChipType.DOWNLOADED_PLAYLIST) {
                    // 窗内被吞的翻转意愿记账,窗到期回放(CR-30)。锁定高度页(网格各 chip)
                    // 标题收/展是纯覆盖层、内容零位移,回放不会产生新反馈,安全;下载管理页
                    // 是唯一实时高度页,窗内上报本身就可能是收展动画的几何反馈,回放它会把
                    // 四轮修掉的贴底振荡以 500ms 节奏请回来——只吞不回放
                    pendingTitleFlip = onTop
                }
            }
            onScrolling(onTop)
        }
        // 静默窗到期回放:把窗内最后被吞的用户终态落地(锁定高度页专属,见上)
        LaunchedEffect(pendingTitleFlip, suppressTitleFlipUntilNs) {
            if (pendingTitleFlip == null) return@LaunchedEffect
            val until = suppressTitleFlipUntilNs
            val remainingMs = (until - System.nanoTime()) / 1_000_000L
            if (remainingMs > 0) kotlinx.coroutines.delay(remainingMs + 8)
            // 等待期间出现新翻转(窗被重开/意愿被消费)则放弃本次回放
            if (suppressTitleFlipUntilNs != until) return@LaunchedEffect
            val v = pendingTitleFlip ?: return@LaunchedEffect
            if (v != showTitleBar) {
                showTitleBar = v
                // 回放的翻转同样开静默窗(窗内新意愿照常记账,不丢单向终态)
                suppressTitleFlipUntilNs = System.nanoTime() + 500_000_000L
            }
            pendingTitleFlip = null
        }
        when (filter) {
            // 下载管理 chip 页:复用独立页的内容体,chip 页无 TopAppBar(库页自带标题区)
            LibraryChipType.DOWNLOADED_PLAYLIST -> {
                val dynamicViewModel: LibraryDynamicPlaylistViewModel = koinViewModel()
                val sharedVm: SharedViewModel = koinInject()
                DownloadedManagementBody(
                    topPadding = topBarLiveHeight,
                    bottomPadding = innerPadding.calculateBottomPadding(),
                    navController = navController,
                    viewModel = viewModel,
                    dynamicPlaylistViewModel = dynamicViewModel,
                    sharedViewModel = sharedVm,
                    onScrolling = tabScrolling,
                )
            }

            LibraryChipType.YOUTUBE_MUSIC_PLAYLIST -> {
                LibraryYouTubeTab(
                    navController = navController,
                    contentPadding = innerPadding.copy(top = topAppBarHeight),
                    playlists = youTubePlaylist,
                    likedPlaylists = youTubeLikedPlaylists,
                    autoPlaylists = youTubeAutoPlaylists,
                    albums = youTubeAlbums,
                    artists = followedYTArtists,
                    isRefreshing = youTubeRefreshing,
                    onRefresh = { viewModel.getYouTubeLibrary(force = true) },
                    onDeletePlaylist = { viewModel.deleteYouTubePlaylist(it) },
                    onUnsubscribePlaylist = { viewModel.unsubscribeYouTubePlaylist(it) },
                    onCreatePlaylist = { viewModel.createYouTubePlaylistInLibrary(it) },
                    onScrolling = tabScrolling,
                )
            }

            LibraryChipType.NETEASE_PLAYLIST -> {
                LibraryNeteaseTab(
                    navController = navController,
                    contentPadding = innerPadding.copy(top = topAppBarHeight),
                    playlists = neteasePlaylist,
                    artists = subscribedArtists,
                    albums = starredAlbums,
                    isRefreshing = neteaseRefreshing,
                    onRefresh = { viewModel.getNeteaseLibrary(force = true) },
                    ownPlaylistIds = ownNeteasePlaylistIds,
                    likedPlaylistId = neteaseLikedPlaylistId,
                    onUnsubscribePlaylist = { viewModel.unsubscribeNeteasePlaylist(it) },
                    onDeletePlaylist = { viewModel.deleteNeteasePlaylist(it) },
                    onUnsubscribeAlbum = { viewModel.unsubscribeNeteaseAlbum(it) },
                    onCreatePlaylist = { viewModel.createNeteasePlaylistInLibrary(it) },
                    onScrolling = tabScrolling,
                )
            }

            LibraryChipType.NETEASE_PODCAST -> {
                NeteasePodcastScreen(
                    innerPadding = innerPadding.copy(top = topAppBarHeight),
                    navController = navController,
                    viewModel = neteasePodcastViewModel,
                    onScrolling = tabScrolling,
                )
            }

            // Nothing to draw: MixForYouScreen owns this content now, and the effect above bounces
            // the filter back to YOUR_LIBRARY the moment it lands here.
            LibraryChipType.YOUTUBE_MIX_FOR_YOU -> Unit

            LibraryChipType.LOCAL_PLAYLIST -> {
                GridLibraryPlaylist(
                    navController,
                    innerPadding.copy(top = topAppBarHeight),
                    yourLocalPlaylist,
                    onScrolling = tabScrolling,
                    emptyText = Res.string.no_playlists_added,
                    header = {
                        LibrarySectionHeader(
                            navController = navController,
                            title = stringResource(Res.string.playlists),
                            onOpenPlaylists = openLibraryPlaylists,
                            onOpenCollections = openLibraryCollections,
                            onOpenPodcasts = openLibraryPodcasts,
                            onOpenDownloads = openLibraryDownloads,
                        )
                    },
                    createNewPlaylist = {
                        showAddSheet = true
                    },
                ) {
                    viewModel.getLocalPlaylist()
                }
            }

            LibraryChipType.FAVORITE_PLAYLIST -> {
                GridLibraryPlaylist(
                    navController,
                    innerPadding.copy(top = topAppBarHeight),
                    favoritePlaylist,
                    emptyText = Res.string.no_favorite_playlists,
                    // 混源网格:网易来源的收藏条目带品牌角标
                    onScrolling = tabScrolling,
                    header = {
                        LibrarySectionHeader(
                            navController = navController,
                            title = stringResource(Res.string.favorite),
                            onOpenPlaylists = openLibraryPlaylists,
                            onOpenCollections = openLibraryCollections,
                            onOpenPodcasts = openLibraryPodcasts,
                            onOpenDownloads = openLibraryDownloads,
                        )
                    },
                ) {
                    viewModel.getPlaylistFavorite()
                }
            }

            LibraryChipType.DOWNLOADED_PLAYLIST -> {
                GridLibraryPlaylist(
                    navController,
                    innerPadding.copy(top = topAppBarHeight),
                    downloadedPlaylist,
                    emptyText = Res.string.no_playlists_downloaded,
                    onScrolling = tabScrolling,
                    header = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            LibraryTilingBox(
                                navController = navController,
                                onOpenPlaylists = openLibraryPlaylists,
                                onOpenCollections = openLibraryCollections,
                                onOpenPodcasts = openLibraryPodcasts,
                                onOpenDownloads = openLibraryDownloads,
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                            ) {
                                LibraryTilingItem(
                                    state = LibraryTilingState.DownloadedSongs,
                                    onClick = {
                                        navController.navigate(
                                            LibraryDynamicPlaylistDestination(
                                                type = LibraryDynamicPlaylistType.Downloaded.toStringParams(),
                                            ),
                                        )
                                    },
                                )
                                Text(
                                    text = stringResource(Res.string.downloaded_collections),
                                    style = typo().titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
                                )
                            }
                        }
                    },
                    onRemoveDownload = { item ->
                        removeDownloadTarget = item
                    },
                ) {
                    viewModel.getDownloadedPlaylist()
                }
            }

            LibraryChipType.FAVORITE_PODCAST -> {
                GridLibraryPlaylist(
                    navController,
                    innerPadding.copy(top = topAppBarHeight),
                    favoritePodcasts,
                    emptyText = Res.string.no_favorite_podcasts,
                    onScrolling = tabScrolling,
                    header = {
                        LibrarySectionHeader(
                            navController = navController,
                            title = stringResource(Res.string.library_podcasts),
                            onOpenPlaylists = openLibraryPlaylists,
                            onOpenCollections = openLibraryCollections,
                            onOpenPodcasts = openLibraryPodcasts,
                            onOpenDownloads = openLibraryDownloads,
                        )
                    },
                ) {
                    viewModel.getFavoritePodcasts()
                }
            }

            LibraryChipType.CHART -> {
                GridLibraryPlaylist(
                    navController,
                    innerPadding.copy(top = topAppBarHeight),
                    chartPlaylists,
                    emptyText = Res.string.no_charts_found,
                    onScrolling = tabScrolling,
                    // 排行榜 tile 封面右上角标 YTM 品牌角标(替代原 SimpMusic 图标)
                    showSourceBadge = true,
                ) {
                    viewModel.getChartPlaylists()
                }
            }

            // Nothing to draw: chip 已隐藏(2026-09-30),上面的 effect 会立刻弹回 CHART,
            // 与 YOUTUBE_MIX_FOR_YOU 同款;恢复 chip 时换回 LibraryWrappedTab(...) 分支。
            LibraryChipType.WRAPPED -> Unit
            else -> Unit
        }
    }
    val coroutineScope = rememberCoroutineScope()
    if (showAddSheet) {
        var newTitle by remember { mutableStateOf("") }
        val showAddSheetState =
            rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
            )
        val hideEditTitleBottomSheet: () -> Unit =
            {
                coroutineScope.launch {
                    showAddSheetState.hide()
                    showAddSheet = false
                }
            }
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = showAddSheetState,
            containerColor = Color.Transparent,
            contentColor = Color.Transparent,
            dragHandle = null,
            scrimColor = Color.Black.copy(alpha = .5f),
        ) {
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                colors = CardDefaults.cardColors().copy(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Card(
                        modifier =
                            Modifier
                                .width(60.dp)
                                .height(4.dp),
                        colors =
                            CardDefaults.cardColors().copy(
                                containerColor = MaterialTheme.colorScheme.outline,
                            ),
                        shape = RoundedCornerShape(50),
                    ) {}
                    Spacer(modifier = Modifier.height(5.dp))
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { s -> newTitle = s },
                        label = {
                            Text(text = stringResource(Res.string.playlist_name))
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    TextButton(
                        onClick = {
                            if (newTitle.isBlank()) {
                                viewModel.makeToast(runBlocking { getString(Res.string.playlist_name_cannot_be_empty) })
                            } else {
                                viewModel.createPlaylist(newTitle)
                                hideEditTitleBottomSheet()
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.CenterHorizontally),
                    ) {
                        Text(text = stringResource(Res.string.create))
                    }
                }
            }
        }
    }
    Column(
        Modifier
            .background(Color.Transparent)
            .hazeBlur(HazeInput.Sources(hazeState), HazeMaterials.ultraThin().then { blurEnabled(true) }).onGloballyPositioned { coordinates ->
                // 双轨:maxOf 锁定值给网格页 contentPadding(防振荡,见声明处说明);
                // 实时值给下载管理页的固定头(它需要跟着顶栏走,且无振荡风险)。
                val measured = with(density) { coordinates.size.height.toDp() }
                topAppBarHeight = maxOf(topAppBarHeight, measured)
                topBarLiveHeight = measured
            },
    ) {
        // 标题行随内容滚动收起(与首页顶栏同款):下滑深入内容时只剩 chip 行贴顶,
        // 回滑到顶时标题行展开;收起态由同尺寸的状态栏 Spacer 占位,chip 行不钻到
        // 状态栏下面。padding 锁定展开高度,标题收/展是纯覆盖层——内容零位移、
        // 也不会反过来触发滚动上报(振荡根源已断)。
        AnimatedVisibility(
            visible = showTitleBar,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.library),
                        style = typo().titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
                navigationIcon = {
                    AnimatedVisibility(
                        !accountThumbnail.isNullOrEmpty(),
                        modifier = Modifier.padding(horizontal = 12.dp),
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        AsyncImage(
                            model =
                                ImageRequest
                                    .Builder(LocalPlatformContext.current)
                                    .data(accountThumbnail)
                                    .crossfade(550)
                                    .build(),
                            placeholder = rememberVectorPainter(SimpIcons.PeopleAlt),
                            error = rememberVectorPainter(SimpIcons.PeopleAlt),
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(26.dp)
                                    .clip(CircleShape),
                        )
                    }
                },
                // The Library bar had no actions slot at all — added for the Listen Together entry,
                // which the design canvas puts on Home AND Library.
                actions = {
                    ListenTogetherIconButton(
                        contentDescription = stringResource(Res.string.listen_together),
                    ) { navController.navigate(ListenTogetherDestination) }
                },
            )
        }
        AnimatedVisibility(
            visible = !showTitleBar,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Spacer(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars),
            )
        }
        AnimatedVisibility(visible = selectionState.isActive) {
            SongSelectionTopAppBar(
                state = selectionState,
                // Stacked BELOW the Library TopAppBar in the same Column, which already consumed
                // the status-bar inset — leaving the default here reserved it twice and opened a
                // status-bar-sized band of dead blur between the two bars. Same fix as Search;
                // the overlay-style call sites (Album, Artist, Recently…) keep the default because
                // they COVER their normal bar instead of standing under it.
                windowInsets = WindowInsets(0),
                onSelectAll = {
                    selectionState.toggleSelectAll(
                        (recentlyAdded.data ?: emptyList())
                            .filterIsInstance<SongEntity>()
                            .map { it.videoId },
                    )
                },
                onOpenActions = { showSelectionSheet = true },
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
            )
        }
        LazyRow(
            state = chipRowState,
            modifier =
                Modifier
                    .padding(horizontal = 15.dp)
                    .padding(bottom = 8.dp)
                    .background(Color.Transparent),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            // LazyRow(UI-CR-15):配合上面的 LaunchedEffect 保证程序性变化后选中项滚入
            // 视野;rememberLazyListState 同样 saveable,切页返回保留滚动位置。
        ) {
            items(visibleLibraryChips, key = { it }) { type ->
                Chip(
                    isAnimated = false,
                    isSelected = type == currentFilter,
                    text =
                        when (type) {
                            LibraryChipType.YOUR_LIBRARY -> stringResource(Res.string.your_library)
                            LibraryChipType.YOUTUBE_MUSIC_PLAYLIST -> stringResource(Res.string.your_youtube_music)
                            LibraryChipType.NETEASE_PLAYLIST -> stringResource(Res.string.your_netease)
                            LibraryChipType.NETEASE_PODCAST -> stringResource(Res.string.netease_podcast)
                            LibraryChipType.YOUTUBE_MIX_FOR_YOU -> stringResource(Res.string.mix_for_you)
                            LibraryChipType.LOCAL_PLAYLIST -> stringResource(Res.string.your_playlists)
                            LibraryChipType.FAVORITE_PLAYLIST -> stringResource(Res.string.favorite_playlists)
                            LibraryChipType.DOWNLOADED_PLAYLIST -> stringResource(Res.string.download_management)
                            LibraryChipType.FAVORITE_PODCAST -> stringResource(Res.string.favorite_podcasts)
                            LibraryChipType.CHART -> stringResource(Res.string.simpmusic_charts)
                            LibraryChipType.WRAPPED -> stringResource(Res.string.wrapped)
                        },
                ) {
                    viewModel.setCurrentScreen(type)
                }
            }
        }
        if (showSelectionSheet) {
            val selectedIds = selectionState.selected.toList()
            SelectedSongsBottomSheet(
                count = selectedIds.size,
                selectionIds = selectedIds,
                onDismiss = { showSelectionSheet = false },
                onPlayNext = {
                    selectionViewModel.playNext(selectedIds)
                    selectionState.exit()
                },
                onAddToQueue = {
                    selectionViewModel.addToQueue(selectedIds)
                    selectionState.exit()
                },
                onAddToPlaylist = {
                selectionViewModel.loadCloudPlaylists()
                showSelectionAddToPlaylist = true
            },
                onDownload = {
                    selectionViewModel.download(selectedIds)
                    selectionState.exit()
                },
                allDownloaded = allSelectedDownloaded,
                onRemoveDownload = {
                    selectionViewModel.removeDownload(selectedIds)
                    selectionState.exit()
                },
                onAddToFavorite = {
                    selectionViewModel.addToFavorite(selectedIds)
                    selectionState.exit()
                },
            )
        }
        if (showSelectionAddToPlaylist) {
            val selectedIds = selectionState.selected.toList()
            val localPlaylists by selectionViewModel.listLocalPlaylist.collectAsStateWithLifecycle()
            val youTubePlaylists by selectionViewModel.youTubePlaylists.collectAsStateWithLifecycle()
            val neteasePlaylists by selectionViewModel.neteasePlaylists.collectAsStateWithLifecycle()
            val youTubeLoadFailedState by selectionViewModel.youTubePlaylistsFailed.collectAsStateWithLifecycle()
            val neteaseLoadFailedState by selectionViewModel.neteasePlaylistsFailed.collectAsStateWithLifecycle()
            AddToPlaylistModalBottomSheet(
                isBottomSheetVisible = true,
                // 本地分区按政策隐藏(此前传 localPlaylists 但组件不渲染,弹窗实际为空);
                // 2026-09-24 多选路径接云端分区,与单曲弹窗同款
                listLocalPlaylist = emptyList(),
                listYouTubePlaylist = youTubePlaylists,
                listNeteasePlaylist = neteasePlaylists,
                youTubeLoadFailed = youTubeLoadFailedState,
                neteaseLoadFailed = neteaseLoadFailedState,
                onRetryCloudPlaylists = { selectionViewModel.loadCloudPlaylists() },
                videoIds = selectedIds,
                onDismiss = { showSelectionAddToPlaylist = false },
                onClick = {},
                onYTPlaylistClick = { playlist ->
                    selectionViewModel.addToYouTubePlaylist(playlist.browseId, selectedIds)
                    selectionState.exit()
                },
                onNeteasePlaylistClick = { playlist ->
                    selectionViewModel.addToNeteasePlaylist(playlist.browseId, selectedIds)
                    selectionState.exit()
                },
            )
        }
        removeDownloadTarget?.let { target ->
            AlertDialog(
                containerColor = rememberSurfaceDarkColors().container,
                titleContentColor = rememberSurfaceDarkColors().content,
                textContentColor = rememberSurfaceDarkColors().content,
                title = { Text(text = stringResource(Res.string.remove_download_title)) },
                text = { Text(text = stringResource(Res.string.remove_download_message)) },
                onDismissRequest = { removeDownloadTarget = null },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.removeDownloadedPlaylist(target)
                        removeDownloadTarget = null
                    }) {
                        Text(text = stringResource(Res.string.delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { removeDownloadTarget = null }) {
                        Text(text = stringResource(Res.string.cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun LibrarySectionHeader(
    navController: NavController,
    title: String,
    onOpenPlaylists: () -> Unit,
    onOpenCollections: () -> Unit,
    onOpenPodcasts: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LibraryTilingBox(
            navController = navController,
            onOpenPlaylists = onOpenPlaylists,
            onOpenCollections = onOpenCollections,
            onOpenPodcasts = onOpenPodcasts,
            onOpenDownloads = onOpenDownloads,
        )
        Text(
            text = title,
            style = typo().titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 4.dp),
        )
    }
}
