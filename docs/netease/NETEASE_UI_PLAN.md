# 网易云音源 UI 接入计划

> 分支 `feat/netease-source`。本文档是 UI 接线阶段的总规划，随推进更新状态。
> 原则：**YT 用 YT 的页面，网易用网易的页面**（主页级）；**同一页面只换数据源**（详情级，按 ID 特征路由）。
> 持久化边界统一按 ID 特征回填 source（纯数字=网易），不靠调用方传参。

## 名词约定

- **独立屏**：各源各看各的页面（主页、分类页）。跟随底部导航的音源切换器（长按搜索按钮）。
- **同页路由**：同一页面按入口实体 ID 特征选数据源（歌单详情：纯数字=网易）。
- **音源切换器**：长按搜索按钮 → 菜单选择，写入 DataStore `selected_source` 持久化；
  切源即 `stopPlayer()`（清队列/正在播放/服务）并回主页——播放数据与音源绑定。
- 刷新语义：当前页立即刷，其它页下次进入时刷（懒刷新）。

## 架构定稿（演进结论）

经历"独立页 → 复用上游单屏+数据层分发 → 回退上游基线+网易独立屏"三阶段，最终：

- **主页**：`NeteaseHomeScreen`（自有文件，骨架仿上游：悬浮顶栏+chips+下拉刷新）；
  上游 `HomeScreen/MoodScreen/HomeViewModel/MoodViewModel` 已恢复网易前基线，
  仅保留两处对 YT 也有益的一行修复（accountShow 重算时序、热门艺人空数据隐藏）。
- **歌单详情**：同一页面（PlaylistScreen/VM 零改动），`PlaylistRepositoryImpl.getPlaylistData`
  按数字 ID 分流到网易数据（PlaylistBrowse 形状适配）。
- **数据层**：`NeteaseRepositoryImpl` 实现 HomeRepository 契约 + `SourceRoutingHomeRepository`
  按 selectedSource 选实现；搜索页等其它消费方继续受益。
- **Room v28**：playlist/song 的纯数字键历史行回填 source=NETEASE。

## 工作包状态

| # | 工作包 | 状态 |
|---|---|---|
| M1 | 播放链路 | ✅ 全部完成（取流：数字ID路由/https升级/Mp3Extractor/音质降级链；歌词：2026-09-14 NETEASE 官方专线接入，yrc逐字/官方翻译，见下文） |
| M2 | 歌单详情 + 库融合 | ✅ 歌单详情（同页路由，作者/年份/曲目/点歌播放全通）；❌ 库融合+品牌角标未做 |
| M3 | 搜索切源 | ✅ 完成（切换器 + 搜索页全链路：建议/热搜/四 tab/点歌播放/歌单同页路由/艺人 toast；2026-09 模拟器双源冒烟通过） |
| M4 | 网易主页 | ✅ 独立屏：账户卡/每日推荐(滤雷达)/私人雷达(曲目三行网格)/雷达歌单(5卡)/精品歌单/推荐新歌(96dp紧凑卡三行)/榜单区块/五组分类(网页catalog目录,3行网格) |
| M5 | 分类页 + 混合 | ✅ 分类页（NeteaseTagScreen 两列网格 + 热门/精品排序 chip + 会话缓存/下拉刷新，网页歌单广场同源）；✅ 混合页私人FM（2026-09-13，独立屏 + 无限续播，见下文方案） |
| M6 | 专辑/歌手页路由 | ✅ 完成（2026-09-14：两页数字 ID 同页路由 + 关注/取关 + 搜索专辑 tab/艺人卡跳转解锁 + 主页热门歌手/新碟上架两行） |
| M7 | 歌曲评论区 | ❌ UI 未开始（core 端点已验证） |
| M8 | 云盘页 | ❌ UI 未开始（core 端点已验证） |
| M9 | 关注同步 + 无版权自动切源 | ❌ 未开始 |
| M10 | 网易歌曲下载管线 | ❌ 单独评估（SimpleCache→文件改造） |

## 已落地的网易主页细节

- **行级懒加载**（2026-09-14 重构）：页面**立即渲染**（无整页 shimmer），九个行槽位
  （每日推荐歌单/私人雷达/雷达歌单/精品歌单/推荐新歌/排行榜/热门歌手/新碟上架/分类）
  各自独立状态：Loading 占位（标题+转圈，高度对齐真实行防跳位）→ 进入组合即拉取 →
  Ready/Failed（失败行占位带重试）。LazyColumn 的预取窗口天然实现滚动无缝。
- **行缓存**：每行独立 10min 会话缓存（`RowCache`，命中即返回、miss 才网络并回写；
  下拉刷新 force 绕过）。tab 切换往返靠 VM 状态零重拉；进程重启缓存清空走网络
  （与旧整页 homeCache 行为一致）。下拉刷新=全部行回 Loading 原地重置（页面不闪），
  仅可见行立即 force 重拉。
- chips：固定 8 个高频快捷（华语/欧美/日语/韩语/流行/摇滚/说唱/ACG），点击进分类网格页。
- 分类体系：`/playlist/catalogue` 全量五组（语种/风格/场景/情感/主题），每组 hot 优先 15 张。
- 分类内容：`/playlist/list`（网页同源，热度序）两页 ≈100 张。
- 性能：~~feed 五组并行~~ 行级懒加载（见下）、雷达 6 详情并行、toplist 去重缓存（Mutex+10min TTL）、
  ~~home 10min 会话缓存~~ 行级 10min 行缓存（下拉刷新 force 绕过）。
- 坑与修法（详见提交历史）：推荐接口 description 是 0/1/2 序号→净化；
  雷达 trackIds=-10000 占位→带 n 的 detail 直取 tracks；track/all 对雷达不稳定→双路兜底。

## M3 搜索页适配方案（2026-09 已确认，已落地）

搜索页只有一页，属"同页换数据源"：`SearchScreen/SearchViewModel` 骨架不动，
数据层新建 `SourceRoutingSearchRepository` 按 selectedSource 分流（复刻 Home 的路由模式）；
网易分支对不支持的能力直接 emit 空 Success，`searchAll()` 的 7 个并行 job 零改动。

### tab 对照（YT 8 个 → 网易 4 个）

| YT tab | 网易 | 处理 |
|---|---|---|
| ALL | 歌手≤3置顶 → 单曲 → 歌单（拼装逻辑零改动，空类自然消失） | 保留 |
| SONGS | cloudsearch type=1（端点已有） | 保留 |
| ARTISTS | cloudsearch type=100（端点已有） | 保留 |
| PLAYLISTS | cloudsearch type=1000（端点已有） | 保留 |
| VIDEOS | MV(type=1004)，播放链路无 | 隐藏 |
| ALBUMS | type=10 端点未封装，且专辑详情 M6 未做点击必失败 | 本期隐藏，M6 后补 |
| FEATURED_PLAYLISTS | 网易搜索无此维度（=PLAYLISTS 重复） | 隐藏 |
| PODCASTS | 电台/播客播放链路无 | 隐藏 |

UI 实现：chips 按 selectedSource 过滤，不动 `SearchType` 枚举。

### 已确认的决策

- **艺人卡片**：正常展示（ALL 置顶区 + ARTISTS tab），点击提示"即将支持"，M6 完成后补跳转。
- **歌单卡片**：点击统一走 `PlaylistDestination(数字id)`——与主页点歌单进入的
  `PlaylistScreen` 完全同一个页面同一布局（同页路由 M2 已通），映射时 `resultType`
  置非 "Podcast" 默认值，确保不会误入 PodcastDestination。
- **搜索建议**：`/api/search/suggest/web`（明文 api）实体建议——歌曲卡（songDetail 批量补封面）
  + 艺人卡；**无词联想**（weapi `/search/suggest` 与 eapi `/v1/search/suggest` 实测空/404，见
  core PITFALLS）。`SuggestItemRow` 复用。
- **空态页**：网易源下不渲染 YTM mood 网格，改为五组分类目录 + 热搜词榜（`/search/hot`，点词即搜）；
  分类卡点击进 `NeteaseTagScreen`（与主页点分类同页同布局）。分类卡封面=该分类最热歌单封面，
  三级获取：进程内存 → DataStore 持久缓存（**复用 YT 现成的 `moodArtworkCache` 字段**，键空间
  天然不相交；7 天 TTL，与 YT `HomeRepositoryImpl.MoodArtwork` 同构）→ 受频控保护的网络回填
  （串行+400ms 限速+会话预算 25+失败熔断，/playlist/list 的 405 限流见 core PITFALLS——裸逐卡
  请求会把 tag 页打挂）。持久缓存跨会话累积，几天正常使用即全页有图。
  SearchViewModel 是 Koin single，观察 selectedSource 变化重取空态数据（否则换源后残留旧源数据）。
- **搜索框占位词**：网易源下轮播只保留 歌曲/艺人/歌单 三词（与 tab 能力一致），YT 六词不变；
  `remember(isNeteaseSource)` 切源即刷新，渲染处对 index 取模兜底（列表 6→3 时防越界崩溃）。
- **搜索历史**：词无源属性，两源共用，不加 source 字段、不做 db 迁移。
- **分页**：limit=30 一次拉完（与 YT"拉两次 continuation"行为对齐），offset 翻页/加载更多
  留后续与 YT 一起做。

### 落地件（全部完成，模拟器双源冒烟通过）

- **core/service**：`searchSuggest`（明文 `/api/search/suggest/web`，歌曲卡批量 songDetail 补封面）
  、`searchHot`（`/search/hot`）两个新端点，均过 NewEndpointsProbe 真机验证。
- **core/data**：`NeteaseSong/Playlist/Artist → SongsResult/PlaylistsResult/ArtistsResult` 映射
  （videoId/browseId=数字 ID 原文，`resultType` 置非 Podcast）；仓库方法
  `searchSongsResult/searchPlaylistsResult/searchArtistsResult/searchSuggestData/searchHotWords`；
  `SourceRoutingSearchRepository`（网易分支空能力发空 Success）+ DI 换绑。
- **composeApp**：chips 按源过滤 + 换源回落 ALL；艺人点击（建议区+结果区）toast"即将支持"；
  空态热搜榜（点词即搜 `submitSearch`）+ 分类目录；占位词收敛；新增字符串
  `artist_page_coming_soon`/`hot_search`（base+zh-rCN+zh-rTW）。
- **SearchViewModel**：注入 NeteaseRepositoryImpl；`hotSearch` flow；观察 selectedSource
  重取空态数据（Koin single，不观察会残留旧源 mood/热搜）。
- **分类卡封面三级缓存**（见上文空态页条目）+ tag 页错误态重试按钮（原有）。

### 踩过的坑（详见 core PITFALLS）

- suggest 端点：weapi/eapi 版全死，只有明文 `/api/search/suggest/web` 活，且歌曲无封面需补齐。
- `/playlist/list` 频控 405：裸逐卡封面请求把 tag 页打挂（同接口连带限流），串行+限速+
  预算+熔断+持久缓存后才稳。

### 后续增强方向（待做）

- ~~tag 页排序 chip~~ ✅ 已做（2026-09-13）：**热门/精品两档**（原计划三档，
  实测 `/playlist/list` 的 `order=new` 返回空表、服务端不支持，砍掉"最新"，坑已记
  core PITFALLS）；`NeteaseTagOrder` 枚举 + `getTagContent(tag, order, force)` 会话缓存
  （key=order:tag，Mutex 单飞 + 10min TTL + 下拉刷新 force 绕过）+ 首张歌单封面回填
  分类卡持久缓存（零额外请求）+ 下拉刷新；`getMoodContent`（MoodScreen 旧路径）委托
  新方法共享缓存；顺手删了 `categoryFeedRows` 里同样注定为空的"最新"行。
- 专辑 tab：✅ 已放开（2026-09-14，type=10 端点+解析探针验证，范特西/魔杰座字段全通）。
- 艺人卡跳转：✅ 已解锁（2026-09-14，toast 换成 `ArtistDestination`，两处：建议区+结果区）。

## M6 专辑/歌手页适配方案（2026-09-14 落地）

同页路由（M2 歌单同款）：YT id 恒含字母、网易恒纯数字，仓库入口 `toLongOrNull() != null` 分流。

- **专辑页**：`AlbumRepositoryImpl.getAlbumData` 数字分流 → `NeteaseRepositoryImpl.getAlbumBrowseData`
  （`/v1/album/{id}` → `AlbumBrowse`：封面/艺人/年份(发行时间)/曲目全量，`toTrackPlaylist` 复用）。
- **歌手页**：`ArtistRepositoryImpl.getArtistData` 数字分流 → `getArtistBrowseData`——五路并行：
  详情(明文 `/artist/head/info/get`) + 热门歌曲 50(`/v1/artist/songs` hot) + 专辑 30
  (`/artist/albums/{id}`) + 相似歌手(weapi `/discovery/simiArtist`) + 动态(关注状态,
  `/artist/detail/dynamic`)。电台/随机播/单曲/MV 区块是 YT 专属 → 置 null 自然隐藏；
  专辑区无 continuation（网易一次给全，"更多"页不出现）。
- **关注/取关**：`updateFollowedStatus` 数字 id 分流 → 网易 `/artist/sub`（在 YT 同步开关
  判断**之前**——开关镜像的是 YT 账号，对网易 id 无意义；M9 的"关注同步"另算）。
- **搜索解锁**：专辑 tab（`cloudsearch type=10`，`searchAlbumsResult`+`toAlbumsResult`，
  ALL 页拼装零改动自动带出）；艺人卡（建议区+结果区）从 toast 换 `ArtistDestination`。
- **主页两行**：热门歌手（圆形头像卡 84dp）+ 新碟上架（**YT 主页同款
  `HomeItemContentPlaylist`，160dp 方卡一屏两列**，副标题显示年份），点击分别进
  艺人/专辑页。数据：weapi `/artist/top` + `/album/new`（area 参数可分地区，当前用
  ALL），`NeteaseHomeViewModel.ReadState` 扩两字段，失败只隐藏行。

### M6 踩的坑（端点生死簿已同步）

- **热门歌手**：`/top/artists`、`/api/v1/artist/top` 三通道全 404；`/toplist/artist` 三通道
  400。真路径是 weapi **`/artist/top`**（无 /api/v1 前缀）。
- 新碟上架 `/album/new` 一次通过（area: ALL/ZH/EA/KR/JP）。

## M1 歌词专线（2026-09-14 落地，M1 收口）

- **分流**：`SharedViewModel.getLyricsFromFormat` 顶部数字 ID 分流 → `getNeteaseLyrics`。
- **主选恒为 NETEASE**（与歌曲同源：yrc 逐字 + 官方翻译，第三方库不可能更准）；
  失败 → LRCLIB（按名搜）→ 本地已保存。设置项不改变主选，只决定显示。
- **映射**：`getNeteaseLyricsData`——yrc 优先于 lrc。**逐字渲染吃的是 `words` 内嵌
  `<MM:SS.mm>` 时间戳标记（RichSyncParser）+ syncType 大写 `RICH_SYNCED`**；yrc 逐字
  按 `Word.charCount` 切片后转成该格式（`toRichSyncWords`，KMP 无 String.format 手动
  补零）；lrc 行级 `LINE_SYNCED`。坑：`Line.syllables` 无人消费（第一次接喂错字段+
  syncType 小写，逐字效果没出来只剩行级高亮——别再踩）。
- **官方翻译 tlyric** 直接喂 translatedLyrics（与原文同源、时间轴天然对齐，过
  updateLyrics 的同步校验）；缺失才走 AI 翻译。官方罗马音 romalrc 本期不接
  （渲染端本地罗马音引擎已覆盖日/韩，后续增强项）。
- **设置项过滤**：网易模式下"主歌词提供商"隐藏 SimpMusic（按 YT videoId 索引）与
  YouTube 字幕（无视频实体），选项只剩 网易官方/LRCLIB/BetterLyrics；原选中项被隐藏时
  显示并按 NETEASE 生效（不改写 DataStore，切回 YT 模式原设置仍在）。
- `LyricsProvider` 枚举 +NETEASE（AMLL 歌词来源角标显示"网易官方歌词"）。
- 坑：Compose 资源的 string 访问器是**扩展属性**，每个使用文件要单独
  `import ...resources.netease_lyrics`（漏了报 Unresolved，别再踩）。

### M6 增量项（2026-09-14 二轮，全部验证）

- **艺人 ID 贯通**：`toMixContent`/`toSongHomeItem`/`toResultSong` 补填 `artistIds`
  （`toTrackPlaylist`/`toSongsResult` 原本就有）→ 播放页点艺人名直接进艺人页
  （`onNavigateToArtist` 原代码零改动，之前静默无效就是 id 为空）。
- **主页两行**：关注的歌手（`/artist/sublist`，端点升级为返回 NeteaseArtist 带头像）+
  收藏的专辑（`/mine/rn/resource/list`），行级懒加载/10min 行缓存同其它行。
- **艺人页"更多专辑"**：`AlbumRepositoryImpl.getAlbumMore` 对 `MPAD{数字}` 前缀分流 →
  `artistAlbums(limit=50)` 一次给全（无分页）；MoreAlbumsScreen 原样渲染。
- **新碟上架地区 chips**（全部/华语/欧美/韩语/日语）：分地区行缓存 10min，
  chips 切换命中缓存即时换、miss 才网络。
- 封面持久缓存可选加 Room 表/DataStore 之外的批量落盘（当前每封面一次 DataStore 写，
  量小可接受）。

## M5 混合页（私人FM）适配方案（2026-09-13 已确认，已落地）

"混合"tab（`MixForYouDestination`）是主页级页面，按架构定稿**按源切换独立屏**：
`AppNavigationGraph` 内 selectedSource==NETEASE → `NeteaseMixScreen`，否则上游
`MixForYouScreen` 零改动（与 HomeDestination 分流同款）。tab 可见性维持"任一源登录"。
LibraryViewModel 早期 TODO 里的"跨源合并分区页"设想已作废（会与网易主页内容重复，
且打破"播放与音源绑定"语义）。

- **页面**：`NeteaseMixScreen`（骨架复刻 NeteaseHomeScreen：悬浮顶栏 + PullToRefresh +
  Crossfade 状态机）。Ready = FM hero 大卡（首曲封面 + scrim + "开始收听"按钮）+
  两个三行紧凑卡分区（复用主页 `NeteaseSongCard`，改为 internal 共享）：
  - **FM 批次（单行两列增量）**：单行横滑卡，**190dp 大卡一屏两列**（主页歌单行同款
    视觉），卡带垃圾桶角标。初始只拉 1 批（3 首）；**严格"一次动作=一次请求=一批"**：
    只在滚动（手势+fling）完全静止且停在末张时判定拉下一批（延时 250ms 复核，防
    手势→fling 交接的假静止；滚动中绝不追加，防 fling 连环触发），按 songId 对现有
    列表去重后追加、整批撞重复不自动重试，加载中行尾转圈；累计 **90 首封顶**（防
    膨胀），达到时 toast 提示一次。**下拉刷新=向后追加一批（不整页重刷**，旧卡保留、
    每日区不动；Ready 态刷新走 loadMoreFm 同路，仅无内容的 Loading/Error 态才全量
    拉取）。播放队列=当前已加载列表，耗尽续批不变。
  - **每日推荐 30 首**：`dailyRecommendSongs`（需登录）——主页只放了每日推荐**歌单**，
    这 30 首歌是本页增量数据，不与主页重复。独立起播（队列名"每日推荐"，不挂 FM
    哨兵，播完即止）。**日更新持久缓存**：服务端每天 0 点换一批，按本地日（epochDay）
    判失效——当日命中会话缓存或 DataStore（`netease_daily_songs_cache`，进程重启也
    免拉），跨日或下拉刷新 force 才走网络；Mutex 单飞防并发双拉。FM 批次**不做缓存**：
    推荐流每次拉新是语义本身，会话内由 VM 状态避免重复拉。
  - **新歌速递**（2026-09-14 落地）：编辑性新歌（与主页"推荐新歌"个性化**不同源**），
    地区 chips（全部/华语/欧美/日语/韩语）。`/top/song` 三通道全 404，实际走
    `/v1/discovery/new/songs`（weapi），**参数名必须 `areaId`**（探针实测 `type`/`area`
    都被服务端静默忽略返回混合列表——坑已记 PITFALLS）；分区会话缓存 10min
    （key=areaId），chips 切换不打网络；点卡整区起播（不挂哨兵，播完即止）。
  - **最近在听**（2026-09-14 落地）：网易侧听歌周榜 `/v1/play/record`（weekData，
    按播放次数降序，需登录；**不含本应用播放**——无网易侧播放回传），会话缓存
    10min，**上限 50 条**；排版与 FM 同款单行两列（190dp 大卡，复用 `FmSongRow`
    但无垃圾桶/无增量加载，数据一次到位）；点卡整区起播。
  两分区与 FM/每日并行拉取、失败只隐藏对应分区（四区全空才 Error）。
  Error 态复用 `netease_home_error` 文案 + 重试。
- **数据**：`NeteaseRepositoryImpl.getPersonalFmContents()`——`personalRadio()`
  （weapi `/v1/radio/get`）映射成 `home.Content` 列表，**填 artists**（不复用
  `toSongHomeItem`，那个 artists=null 会丢艺人行）。无会话缓存：FM 每次拉取即"新批"。
- **播放**：`NeteaseMixViewModel.playFrom(contents, index)` 整批入队（RADIO），
  点哪首哪首 firstPlayed；playlistId 用哨兵常量 `NETEASE_FM_PLAYLIST_ID`（core/common
  Config，挨着 LOCAL_PLAYLIST_ID）。
- **无限续播**：队列耗尽的 endless-queue 钩子原来只认 YT（`loadMore` → `getRelated`
  → RDAMVM 电台），数字 videoId 进去必失败。现 `loadMore()` 顶部加早分支：
  playlistId==哨兵 → `getNeteaseFmBatch()` 拉 personalRadio、按现有队列 videoId 去重、
  `loadMoreCatalog` 追加；去重为空时最多再拉 2 次换批（一批只有 3 首，整批撞已有歌
  不罕见），仍空 → 置回 INITIALIZED 等下次触发（不无限重试）。
  FM 不做 RDAMVM 式"已在电台模式"早退——批批连播。仓库经 Koin 懒取（player 同款）。
  Android/JVM 两个 handler（`MediaServiceHandlerImpl`/`JvmMediaPlayerHandlerImpl`）同步改。
- **续播触发门**：`onMediaItemTransition` 里 loadMore 的触发条件是
  `list.size > 3 || endlessQueue`——FM 一批恰好 3 首天然过不了门，已加
  `|| playlistId == NETEASE_FM_PLAYLIST_ID` 放行（FM 即无限电台，不依赖 endlessQueue
  设置；两个 handler 同步改）。
- **实测坑**：`/v1/radio/get` 一批只回 **3 首**（与网易云 App"播一首补一首"的 FM
  语义一致），不是 30；页面 hero + 3 卡属正常形态。
- **字符串**：`personal_fm`/`personal_fm_subtitle`/`personal_fm_start`（base+zh-rCN+zh-rTW）。
- **不做**（后续候选）：底部 tab 标签随源切换文案；popAdjust 提示。
- **红心电台**（2026-09-13 落地，心动模式的本地替代）：原计划调
  `/playmode/intelligence/list`，实测该端点对第三方已**全面 500**（weapi/eapi/明文×
  参数网格全灭，见 core PITFALLS）——改为本地实现：红心歌单随机 30 首
  （`userLikedSongIds` 洗牌 + `songDetail` 批量补全）起播，**同挂 FM 哨兵**，
  播完自动接私人FM 续批。入口卡在 hero 下方（心形图标 + 加载转圈）。
- **FM 卡片垃圾桶**（2026-09-13 落地）：卡片图上右上角垃圾桶角标（`NeteaseSongCard`
  新增可选 `onTrash`，主页/每日不传零变化）→ 乐观移除卡片 → `/radio/trash`
  （weapi，songId+time 毫秒）标记不感兴趣 → 响应 `data[0]` 是补位歌就用它，
  拿不到补拉一批 FM 取未见过的歌插回原位。

## 调试备忘（环境）

- 崩溃日志渠道：logcat tag `CustomActivityOnCrash`（CaC 接管了默认 FATAL 输出）。
- 模拟器菜单连招：`input swipe FAB同点2000ms` → `input tap 736 2009`（网易）/`771 1883`（YT）。
- 模拟器底部 tab 顺序：主页(199)/混合(451)/库(703)/**搜索(955)**，y≈2290；搜索框在 (540,267)。
- DataStore pb 一律不做外部改写（键长变化会损坏解析）；改设置走 app 自己的 UI。
- 磁盘：单次 debug 构建 ≈2GB，满了先清项目 build 目录与 ~/.gradle 旧版本缓存；
  **删 ~/.gradle/caches/<版本> 前必须先 `./gradlew --stop`**（daemon 活着会写回损坏缓存，
  之后 Kotlin DSL 编译 NPE：getResourceAsStream must not be null）；ENOSPC 期间失败构建会给
  模块 build 留损坏 KSP 增量态（报 schema xx.json not found），连模块 build 一起删再建。
  磁盘满到 0 时 ZCode 的 Bash/Write 全堵死，可用 node_repl MCP 通道 `fs.rmSync` 清缓存自救。
- 冒烟探针：`/tmp/netease_cookies.json`（jvmTest 读取，不进仓库）驱动 `NewEndpointsProbe`。
- **core 协议/数据层踩坑全录**：`core/service/netease/PITFALLS.md`（端点生死簿、响应形状坑、KMP 限制、Flow 契约、风控）——改网易相关代码前先读。
