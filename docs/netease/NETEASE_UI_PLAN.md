# 网易云音源 UI 接入计划

> 分支 `feat/netease-source`。本文档是 UI 接线阶段的总规划，随推进更新状态。
> 原则：**YT 用 YT 的页面，网易用网易的页面**（主页级）；**同一页面只换数据源**（详情级，按 ID 特征路由）。
> 持久化边界统一按 ID 特征回填 source（纯数字=网易），不靠调用方传参。

## 名词约定

- **独立屏**：各源各看各的页面（主页、分类页）。跟随底部导航的音源切换器（长按搜索按钮）。
- **同页路由**：同一页面按入口实体 ID 特征选数据源（歌单详情：纯数字=网易）。
- **音源切换器**：长按搜索按钮 → 菜单选择，写入 DataStore `selected_source` 持久化。
  切源统一走 `SharedViewModel.switchSource(source)`（2026-09-15 c214f486 收敛三处入口：
  底栏菜单/QR 登录成功自动切/登出·访客回落）：停播清内存队列 + **清持久化恢复源**
  （queue 表 + recentMediaId + playlistFromSaved）并回主页——播放数据与音源绑定；
  core 两平台 mayBeRestoreQueue 另有形状防御兜底（恢复曲 ID 形状 ≠ selectedSource 即跳过），
  重启不再跨源恢复。
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
| M2 | 歌单详情 + 库融合 | ✅ 全部完成（歌单详情同页路由；2026-09-15 库页"您的网易云"三分区 tab + 品牌角标，见下文） |
| M3 | 搜索切源 | ✅ 完成（切换器 + 搜索页全链路：建议/热搜/四 tab/点歌播放/歌单同页路由/艺人 toast；2026-09 模拟器双源冒烟通过） |
| M4 | 网易主页 | ✅ 独立屏：账户卡/每日推荐(滤雷达)/私人雷达(曲目三行网格)/雷达歌单(5卡)/精品歌单/推荐新歌(96dp紧凑卡三行)/榜单区块/五组分类(网页catalog目录,3行网格)。2026-09-15 变更：关注的歌手/收藏的专辑两行移出主页（已迁库页"您的网易云"tab，M2 收口）；热门歌手行采 YTM chart 布局并沉底 |
| M5 | 分类页 + 混合 | ✅ 分类页（NeteaseTagScreen 两列网格 + 热门/精品排序 chip + 会话缓存/下拉刷新，网页歌单广场同源）；✅ 混合页私人FM（2026-09-13，独立屏 + 无限续播，见下文方案）。2026-09-15：tab 可见性从"任一源登录"改为**按当前源的登录态**（YT登录+选YT 或 网易登录+选网易），消除 YT 未登录+选 YT 露出空 mixes 页 |
| M6 | 专辑/歌手页路由 | ✅ 完成（2026-09-14：两页数字 ID 同页路由 + 关注/取关 + 搜索专辑 tab/艺人卡跳转解锁 + 主页热门歌手/新碟上架两行） |
| M7 | 歌曲评论区 | ❌ UI 未开始（core 端点+repo 方法已验证；播放页 Spotify 主题详情卡已展示前 2 热评，缺独立评论页） |
| M8 | 云盘页 | ❌ UI 未开始（cloudDisk 端点已封装于 NeteaseEndpoints，repo 映射+UI 未接） |
| M9 | 关注同步 + 无版权自动切源 | ❌ 未开始。注：设置开关"无版权音乐自动切换音源"已于 2026-09-15 隐藏（死设置，运行时零消费），M9 落地时随 StreamRepositoryImpl 灰歌回退一起恢复 |
| M10 | 网易歌曲下载管线 | ❌ 单独评估（SimpleCache→文件改造） |

## 已落地的网易主页细节

- **行级懒加载**（2026-09-14 重构）：页面**立即渲染**（无整页 shimmer），九个行槽位
  （每日推荐歌单/私人雷达/雷达歌单/精品歌单/推荐新歌/排行榜/热门歌手/新碟上架/分类）
  各自独立状态：Loading 占位（标题+转圈，高度对齐真实行防跳位）→ 进入组合即拉取 →
  Ready/Failed（失败行占位带重试）。LazyColumn 的预取窗口天然实现滚动无缝。
- **行缓存**：每行独立 10min 会话缓存（`RowCache`，命中即返回、miss 才网络并回写）。
  tab 切换往返靠 VM 状态零重拉；进程重启缓存清空走网络（与旧整页 homeCache 行为一致）。
- **下拉刷新=静默刷新（2026-09-20 终稿，四轮迭代）**：已就绪的行**保持内容不回占位**，
  后台 force 重拉（绕过行缓存）、落地即原位替换；失败行回 Loading 重试；未加载行维持原状。
  顶部指示器是"手势已受理"的确认信号而非进度条：**首批落地或 600ms 到点先到先收**（约半圈）。
  四轮教训：①初版"全部行重置转圈占位"=首屏多圈同转；②行回写丢更新竞态（→`update{}`）；
  ③静默化后指示器等全部行——最慢行(分类目录/排行榜聚合)10s+陪跑；④首批落地收（~1-2s）
  用户仍读作"转了好几圈"，最终加 600ms 硬上限。`forceNextLoads` 已删（refresh 对已加载行
  显式 force，滚动加载永远走缓存）。
- **混合页(FM)下拉同款化（2026-09-20）**：Ready 态下拉=静默追加一批（顶部指示器曾经完全不转
  无反馈）；Loading/Error 态=整页重载（指示器曾陪跑 4 个并行请求全程）。现统一 `refreshing`
  StateFlow + 600ms 上限/首批落地先收；防抖动重触发（compareAndSet）。
  实测（10fps 逐帧）：两页指示器均 ~0.5s 收，内容全程稳定，数据静默落地替换。
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
- **跨源歌词供应商（YT 歌选用第三方歌词库，2026-09-14 确认要做）**：
  统一模式 = 按名搜目标库 + 时长 ±3s 校验 + 版本词黑名单（Live/DJ/cover/翻自）+ id 直连歌词，
  设置页可选、匹配失败回退 LRCLIB；核心价值 = tlyric 官方翻译 > AI 翻译且免 AI 配额。
  优先级：① 网易（探针已验证：アイドル/Shape of You 命中带官方翻译；已知风险：错版本配到
  DJ 版、热门歌"晴天"漏配需双序搜索、yrc 逐字外语歌基本只有行级——详见项目 AGENTS.md
  跨源歌词 TODO）；② QQ 音乐（社区接口稳定、qrc 解密成熟、华语覆盖最全）；③ 酷狗（与 QQ
  重叠，做一家即可）；④ 酷我（最低）；⑤ Apple Music **不做**（MusicKit 合规拿不到完整时间轴）。
  接入点全部现成：`getLyricsFromFormat` 分支 + `LyricsProvider` 枚举 + SettingScreen
  YT 模式列表（渲染零改动，映射参考 `toRichSyncWords`）。
  **参考实现**：[Replica0110/Lyrico](https://github.com/Replica0110/Lyrico)（Kotlin，929★，
  歌词搜索/匹配/逐字全链路）。重点借鉴其 `utils/MusicMatchUtils.kt` 的匹配算法（比"时长±3s+
  黑名单"精细一个量级）：版本噪声正则清洗（~30 词）、smartSimilarity 四算法混合（短串偏
  Levenshtein）、片段双向匹配 + coverage 压虚高、时长分档只占 12% 权重、搜索位次小加分、
  多 query 组合搜索（片段拼接/双序/单独 title）。其 SearchSource 插件化架构不需要照搬，
  只抄纯函数部分。详见项目 AGENTS.md 跨源歌词 TODO。

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

## M2 库融合（2026-09-15 落地，347404be + core 14e4408）

- **"您的网易云"chip**（`LibraryChipType.NETEASE_PLAYLIST`，持久化值 `netease_playlist`，
  旧值照常解析）：仅网易登录时显示（与 YT chip 对 YT 登录的门控对称）；登出时
  持久化选中会被 init 弹回 YOUR_LIBRARY，运行中登出由 VM collect neteaseCookie
  实时弹回并清三分区数据。
- **三分区单页**（`LibraryNeteaseTab`，LazyVerticalGrid FixedSize 132dp）：
  歌单（`getLibraryPlaylists`，红心歌单按 specialType=FAVORITE 稳定排序置顶，
  core 侧兜底服务端乱序）→ 关注的歌手（复用主页 `NeteaseArtistRow`，internal 化，
  showRank=false）→ 收藏的专辑（复用 `NeteaseAlbumRow`）。三分区并行拉取、
  独立降级（失败分区直接隐藏不拖垮整页，网易主页"失败只隐藏行"同款）；
  全空才显示空态。下拉刷新 force 绕过艺人/专辑行缓存且不清空已显示分区
  （`neteaseRefreshing` 独立指示器，只有首拉才进 Loading 整页 spinner）。
  点击路由：歌单 tile→PlaylistDestination / 艺人→ArtistDestination / 专辑→
  AlbumDestination，数字 ID 同页路由全通（实测三跳均正常）。
- **品牌角标**（`SourceBadge.kt` 的 `NeteaseSourceBadge` + `isNeteaseContent`）：
  半透明黑圆底 + 白色 Netease 图标叠缩略图右上角，**只在混源上下文 opt-in 渲染**——
  收藏网格/下载网格（`GridLibraryPlaylist.showSourceBadge`）与最近添加行
  （`PlaylistFullWidthItems.showNeteaseBadge`）；纯源页面（网易主页/您的网易云）不传。
  纯 YT 用户永不出现（网易实体只有经网易使用才进本地库）。判源：playlist 表
  source 列；album 表无 source 列，按 browseId 纯数字形状（项目惯用法）。
  歌曲行/艺人行不加（跨源播放有设计容忍，加了纯噪音）。
- **歌单曲目滚动分页**（2026-09-15 二轮，core e63c6b1）：`/playlist/track/all` 已 404 死透
  （weapi 全歌单），原"track/all limit=500"路径实际是把大歌单**截断在 500 首**。新管线 =
  `/v6/playlist/detail`(n=0) 拿全量 trackIds → `/v3/song/detail` 按页分片（500/批，
  `NETEASE_PLAYLIST_PAGE_SIZE`）。分页令牌 `NETEASE_PL_PAGE_{offset}` 走共享
  PlaylistViewModel 的 continuation 契约（`getPlaylistData` 返回令牌 → 滚动近底触发
  `getContinueTrack` → SongRepositoryImpl 前缀路由分支 → `getPlaylistTracksPage` 续拉），
  页面/UI 零改动；雷达类 trackIds 不可用时仍走带 n 的 detail 一次全量（无令牌）。
  **顺带修了潜伏解析 bug**：trackIds 条目是 `{"id":<歌曲id>,"v":<版本号>}`，旧代码读 `v`
  拿的是版本号（雷达"-10000 占位"的老结论混有此 bug 成分）——已改 id 优先。
  实测（页大小临时 100 + 490 首红心歌单）：100×4+90 五页全量、顺序稳定、表头计数一致、
  末页令牌归 null 正常收尾。坑已记 core PITFALLS（track/all 死亡 + trackIds 形状）。
- **状态同步 + 收藏管理二轮（2026-09-15，159c3491 + core adb78fd）**：
  - **红心 OR 合并**：开关开时播放页红心 = 本地 || 云村（`/song/like/get` 全量 ids，
    会话缓存升级为 10min TTL + Mutex 单飞，替代旧"红心歌单→playlistDetail"链路且不再
    永不回刷）；云端有本地无 → 回填本地行。与 YT `combineLocalAndYouTubeLiked` 完全对称。
  - **艺人关注态同步**：浏览艺人页把 `/artist/detail/dynamic` 的 subscribed 落库
    （`setFollowedLocal`——insertArtist 是 INSERT IGNORE，必须显式 UPDATE）并校正按钮；
    此前浏览时丢弃服务端态，从库页点进已关注歌手总显示未关注。YT 侧镜像语义不变。
  - **关注 toast 按源分流**：网易歌手 → "已在网易云关注/已取消"，不再说 YouTube。
  - **库页管理入口（内外双入口）**：收藏歌单 tile / 收藏专辑卡**长按 → 确认弹窗 → 取消收藏**
    （仅收藏的露出；自建歌单判定 = creatorId==uid，repo 记上次 userPlaylists 的 creator 映射，
    未知 fail-closed）；歌单 tile 副标题改为显示创建者昵称。**详情页"更多"菜单同样露出**
    （歌单页仅收藏歌单、专辑页仅网易专辑 → "取消收藏"行 → 二次确认；歌单路径同时熄灭本地
    红心）。两入口共用 PlaylistBottomSheet 的可选 onUnsubscribe 行。
  - **歌单内移除歌曲**：自建网易歌单的歌曲三点菜单加"从歌单中移除"（manipulate op=del，
    内存列表剔除 + 表头计数减一；复用既有 remove_from_playlist 字符串）。
  - 模拟器实测：关注态落库/按钮、红心 false→true 合并、两类取消收藏弹窗、移除歌曲
    全通；toast 文案与艺人页关注按钮无法用 adb tap 驱动（input 注入 vs Compose 的
    模拟器噪声，账号状态经探针确认无损），留日常使用确认。
  - **歌单收藏同步云村（2026-09-16 四轮定案）**：任何入口打开网易歌单点红心收藏，云端同步
    `/playlist/subscribe`（取消收藏=t=0）；浏览时 adopt-on 回填云端订阅态（/v6/playlist/detail
    顶层 `subscribed` 字段，详情缓存优先）。**独立开关"收藏与网易云同步"**（默认开，设置页
    位于"关注与网易云同步"正下方；关=仅本地，与 YT 歌单一致）。自建歌单跳过云端（自己的
    歌单无"收藏"概念）；本地 Room 标记总是生效。注意 /playlist/subscribe 有端点级 405 频控
    （见 core PITFALLS），脚本连发会触发。
  - **自建歌单删除（2026-09-16）**：`/playlist/delete` 端点；库页长按自建歌单=删除（收藏
    歌单=取消收藏，红心歌单=无操作），详情页"更多"菜单对自建露"删除歌单"。不可逆操作，
    强确认弹窗（"永久删除，无法恢复"）；红心歌单全路径排除（服务端也拒绝，repo 兜底 require）。
  - **红心歌单内"从歌单移除"=取消红心（2026-09-16）**：红心歌单的移除走 /song/like t=0 +
    本地 liked 清零（与播放页红心同一逻辑），不走 manipulate del；其余自建歌单仍走 op=del。
  - **双品牌角标推广（2026-09-16）**：角标升级为双品牌（网易音符标/YT 播放标，
    `SourceBadge(source)` + `contentSource()` 判源），覆盖所有混源 chip 页——您的库最近添加
    行（歌曲/歌单/专辑/艺人全覆盖）+ 最多播放 Canvas 卡 + 收藏/下载网格。单源页（您的网易云/
    YouTube 歌单/排行榜/播客）与本地自建歌单封面不加（本地歌单内容混源）。
  - **本地/来源账号双状态收口（2026-09-16）**：播放页、歌单页、专辑页、歌手页均把
    SimpMusic 本地状态与来源账号状态拆成两个独立按钮；来源按钮使用 YT/网易品牌图标，
    加载与失败不再伪装成本地状态。迷你播放条仍只表示本地红心。YT 补齐歌曲/歌单/专辑
    收藏和歌手关注的远端读取与显式写入；网易关注同步开关从死设置改为真正门控。
    在线歌单菜单的“保存到本地”改为“复制为本地歌单”：一次性生成无平台绑定、可独立
    增删改的本地快照；原有本地歌单→YT 关联同步能力保留在本地歌单同步入口。
    “添加到歌单”面板按歌曲来源显示本地/YT/网易目标，网易仅列当前账号自建歌单并走
    `/playlist/manipulate/tracks op=add`。YT 与网易的歌曲、关注、歌单/专辑收藏分别使用同构
    同步开关；初次读取只 adopt 远端 true，避免启用同步时误删已有本地收藏。
  - **双源 UI 与库入口收口（2026-09-17）**：添加到歌单面板的来源标签简化为“本地歌单 /
    YouTube Music / 网易云音乐”，未登录的平台不显示入口，数字网易歌曲不显示 YT 目标；单艺人
    歌曲从播放页菜单直接进入艺人页，多艺人才弹选择。设置页将每个平台的歌曲/艺人/歌单专辑
    同步项收进一个多选弹窗，YT 总入口未登录时禁用。库页保留原有快捷入口并增加歌单/收藏/播客
    独立页；“已下载”页用与“您的库”一致的彩色快捷卡在本页切换歌曲和歌单，不再嵌套跳转。
  - **取消红心失效修复（2026-09-16，core a9b7b84）**：播放页红心 `toggleLike` 的方向判据
    原读 `controlState.isLiked`（只在切歌/controlState 变化时刷新）——OR-merge 回填写 Room
    不触发它，整首歌期间判据停在 false，点亮的心点一下发出去的是"点赞(1)"而非"取消(0)"，
    云端永远收不到取消、歌永远留在红心歌单。修法：两平台 handler 判方向改**现读 Room**
    （与显示态同源）；controlState 只供通知栏图标。附带 B1：红心下降沿时若正停在红心歌单
    页，立即从内存列表剔掉当前歌（页面原只在进入时拉数据，取消后返回看到旧列表）。
  - **歌曲行角标两修（2026-09-16，a656ed71）**：①角标原画在 Crossfade 封面**之前**，被封面
    盖住——歌曲行从来没显示过（歌单/专辑/歌手行的角标在封面后所以正常）；②YT 角标原用
    YT Music 圆环标，16dp 下圆环线宽不足 2px 视觉上"没有角标"——这正是"有的歌曲没角标"
    的全部真相。修法：角标移到封面后；YT 改 **YouTube 红底 + 白色实心播放三角**，网易保持
    黑底白音符（像素级双色检测验证通过）。
- **原主页两行迁入**：关注的歌手/收藏的专辑从网易主页移除后在此复活（TODO #6 收口），
  数据链路 `getSubscribedArtists`/`getStarredAlbums`（10min 行缓存）原样复用；
  分区顺序（2026-09-15 用户定案）：歌单 → 收藏的专辑 → **关注的歌手（沉底）**。
- 字符串：`your_netease`（您的网易云）/`no_netease_content`/`netease_playlists`（歌单）/
  `starred_albums`（收藏的专辑）/复用 `followed`（已关注，对齐 YT 库页概念），3 locale。


## 收藏体系云端化 + 库页改组（2026-09-19/20 定案，多轮迭代终稿）

> 迭代路径备忘：单按钮+叹号(pending_sync) → 两端不一致角标(方向弹窗) → 云朵按钮 →
> **收藏全面云端化（终稿）**。中途三版全部废弃，教训：自动同步语义必然需要无穷的
> 调和机制（同步债/差异方向/adopt 规则），显式双状态比隐式同步便宜得多；最终用户
> 选择"干脆只留云端"。pending_sync 表（v29 撞号已清理）与同步开关、云朵组件全删。

**终稿规则（收藏=云端账号状态，本地字段降级为镜像缓存）**：
- 红心/关注/收藏心的显示源=云端快照（切歌/进页拉取），点击=直接调云端接口；
  成功后镜像写本地 `liked/followed` 行（库页喜欢的歌曲/关注的歌手分区读本地照常）。
- 覆盖面一条路径：播放页三主题、迷你条、三点菜单"点赞"、通知栏红心（core handler
  toggleLike）、批量多选点赞、艺人关注、歌单/专辑收藏心。
- **未登录置灰（gate 判定源=该源登录态，绝不用云端快照 null 判显隐**——YT 未登录时
  subscribed/liked 常返回 false 而非 null，必误判）：艺人关注=置灰不可点占位；
  播放页红心/加歌单、菜单点赞/添加到歌单行=enabled 置灰无涟漪；迷你条红心=置灰+
  点击提示"登录后才能使用收藏"；歌单/专辑心同迷你条。设置页两个"退出"按钮未登录置灰。
- toast 文案中性化（已喜欢/已收藏/已关注，无"云端"字样）；失败按平台分流。
- **同步类设置全部删除**（YT 三项/网易三项多选、立即同步、说明文案），DataStore 键保留无消费。

**库页改组**：chips = [YT 歌单(登录)/您的网易云(登录)/排行榜/Wrapped(开记录)/下载管理]；
"您的库"chip 下线（TilingBox/Canvas 卡/最近添加随之不可达，**本地歌单/收藏/播客暂无入口**
——独立路由仍在，等用户定入口形态）；下载管理 chip=原已下载页内容体（歌曲/歌单双段，
`DownloadedManagementBody` 共用组件，独立路由保留）。启动回落 defaultLibraryChip()：
网易→YT→排行榜，**读 cookie 带 500ms 超时**（DataStore 首读竞态会拿到空串，曾致回落
排行榜、"您的网易云"看似刷不出）。

**本地歌单双平台同步上云**：原"同步到 YouTube"入口扩展——YT 曲目走原管线（修了混源
直发数字 id 的 bug，先按源过滤），网易曲目走新管线（/playlist/create 隐私歌单 +
playlistDetail 求差集增量 add 500/批）；`local_playlist.netease_playlist_id` 列（Room v29）
。纯网易歌单不建空 YT 歌单；两平台独立 toast 互不阻断。

**灰歌提示**：网易 403 取不到流（cover/无版权）时 toast"该歌曲在当前音源不可播放
（可能需 VIP 或无版权）"，不再误报超时（ToastType.PlayerError.unavailable 标志）。

**杂项**：歌曲行当前曲指示符三态（播放=Lottie/暂停=静态暂停符号，行内 koinInject
SharedViewModel 读 isPlaying）；netease_account 表自愈（repairAccountRowIfMissing：
cookie 在而表空时拉账号摘要补行，防清库只清 Room 的分裂）；网易云账户管理空表修复同源。

**踩坑记录**：
- **DB v29 撞号**：开发期两版未发布的 v29（pending_sync 表 vs netease_playlist_id 列）
  同号不同 schema，Room 不跑迁移直接 schema mismatch（症状：音频在播但 songEntity
  查空、MiniPlayer 不出现）。教训：**开发期每次改 schema 清 app 数据库或递增版本号**。
- HeartCheckBox 加 modifier/enabled 参数时，新参数必须放在 trailing-lambda 参数
  （onStateChange）之前，否则全仓 trailing-lambda 调用集体编译炸。
- 账户管理"无账户"=清库遗留（cookie 在 DataStore、账号行在 Room），自愈已覆盖。

**新增 TODO（2026-09-20）**：
- 迷你播放条/歌单详情页/播放页/歌手页 **封面加品牌角标**（库页角标已按用户要求全撤，
  此四处为用户主动要求恢复）；
- 主页混源数据偶发（未复现；已知唯一混源路径=本地库栏目天然跨源，待用户复现截图）；
- 多选模式面板（SelectedSongsBottomSheet）的加入喜欢/添加到歌单未置灰（混源判定复杂，
  当前逐首失败+汇总 toast）；
- 本地歌单等"您的库"下线后的入口形态待定。

## 播放队列页增强（2026-09-20 落地，双源通用非网易专属，80188193..4552c149）

需求：队列标题显示当前曲目位置 xx/YY（字号与"无尽队列"一致）；列表浮动定位按钮，点击滚回当前曲行。两个队列 UI 都做了：

- **队列弹窗 `QueueBottomSheet`**（ModalBottomSheet.kt；M3 Expressive/经典主题播放页 + MiniPlayer/歌词页入口共用）：
  标题行变"队列 xx/YY"（titleMedium + bodySmall 计数，与 endless_queue 同 style）；定位按钮用 Material
  `SmallFloatingActionButton` 右下浮动（bottom=116dp≈抬高两行歌高，容器 75% 透明），
  `animateScrollToItem(currentQueueIndex)`。索引复用 `deriveOrderIndex`（NowPlayingScreen artwork pager
  同款：播放器索引指向当前曲时采信、否则 videoId 回退 indexOfLast），输入全是响应式状态，
  弹窗开着切歌/拖动换序实时联动。
- **AM 主题队列视图 `AppleMusicQueueView`**：计数挂"正在播放"副标题行（"正在播放 12/34"，
  queueSectionSubtitle，与无尽队列开关同款字号——右半行被开关占了、下行歌单名跑马灯占满，副标题是唯一空位）；
  定位按钮复用 AM 自有浮动圆钮语言（`AppleMusicFloatingCircleButton`，38dp 白24%，从歌词页私有提升到
  AppleMusicShared 共用）。定位按钮悬在列表最后一行上方（bottom=QUEUE_BOTTOM_FADE+16dp）。
- **AM 队列语义定稿（重要变更）**：原"仅显示待播曲目"（drop 已播前缀，Apple Music 官方形状）→
  **完整队列**（与 QueueBottomSheet 同构，已播在当前曲上方）。两次返工的教训都在这：upcoming-only 时
  当前曲不在列表里，定位按钮无处可落（用户报"没定位到正播放的歌"）；把当前曲塞到列表头又导致
  顶部无内容可翻（用户报"不能往上翻了"）。终稿=完整队列 + 本地索引==绝对索引（offset 换算全删，
  拖动/点击/⋯菜单直用索引）+ 列表初始锚定当前曲（`rememberLazyListState(initialFirstVisibleItemIndex)`）+
  切歌与定位都滚到 `currentOrderIndex` + 当前曲行 isPlaying 均衡器高亮。
- **顺手修复**：QueueBottomSheet 的 LazyColumn 原是 Column 裸子节点，拿到无界高度约束 → 整个队列
  一次性全组合不虚拟化；包 `Box(weight(1f))` 修复（AM 视图本来就是这种结构）。
- **新增图标** `SimpIcons.MyLocation`（Material my_location，定位语义通用图标）。
- **实测**（模拟器 2026-09-20）：双主题计数/定位/定位后上翻/拖动换序联动全过（拖当前曲 12→14 位，
  计数 12/34→14/34，均衡器跟歌走；拖非当前曲计数不变=正确语义，顺序是否移动看列表即可）。
- 遗留观察：adb 自动化测试 dismissing 单曲"⋯"菜单后，右下角出现过一次半透明白色残影（再点即消），
  未复现；用户手动复现触发路径前不立项。
- **2026-09-20 二轮（FM 锁定 + 网易无尽续播，core 4a88555）**：
  - 无尽开关对**网易私人FM队列锁定为开**——FM 语义即无限电台（loadMore 凭哨兵放行与开关无关），
    两个队列 UI 的 Switch checked 强制 true，点关闭弹 toast（新字符串 `endless_queue_fm_locked`×5 locale），
    不落 DataStore。
  - **网易普通队列的无尽续播打通**（原为"可做快赢"项）：无尽钩子耗尽时按尾曲 ID 形状分流——
    网易数字 ID 转 `NETEASE_RADIO_<id>` 哨兵（continuation="0"）走 simiSong 首批，替代原来无条件
    getRelated 对数字 ID 的静默失败；simiSong 见底后 `reseedNeteaseRadioIfEndless` 以当前尾曲换种子
    续链（种子没变=整批撞重即停，防循环）。android+jvm 双端同构。追加的歌照旧进 listTracks，
    队列页计数 YY 会随之增长。
  - 运行时验证：编译双 target 通过后由用户在模拟器上手测接管（2026-09-20，未回报问题）。

## 剩余工作盘点（2026-09-16 重整）

> 本节是**索引**（全局视图），刻意精简；接手顺序：项目 `AGENTS.md`（会话自动加载，
> 含各 TODO 资产级细节与实测要点）→ 本文档各工作包小节（已做项的实施记录）→
> `core/service/netease/PITFALLS.md`（改网易代码前必读的端点坑）。
> **实施级细节分布**：相似歌曲页/跨源歌词/M10 下载/AI 三件 → AGENTS.md 有完整 TODO 小节，
> 可直接开工；M9 灰歌/M7 评论/M8 云盘/播客/MV → 仅有索引行，**接手先出方案再动码**
> （方案写回本文档对应小节，沿用 M3/M5/M6 的先例）。
> M2 已全部收口（库页三分区/双品牌角标/收藏同步/删除入口/红心逻辑/曲目分页），不再列入。

### 未做（计划内，按建议优先级）

| 项 | 现状资产 | 规模 |
|---|---|---|
| M9 灰歌自动切源 | `getNeteaseStream` 降级链现成，缺 title+artist 搜 YT 回退；设置开关已藏待恢复 | 中 |
| 相似歌曲独立页 | simiSong 端点/电台队列/`NETEASE_RADIO_` 哨兵全现成，缺列表页 UI | 小（纯 UI） |
| M7 评论页 | songComments 分页端点+repo 方法现成，详情卡已在用前 2 热评 | 中 |
| M8 云盘页 | cloudDisk 端点已封装，repo 映射+UI 未接；云盘歌可播不可缓存下载 | 中 |
| 网易播客 | 搜索 type=1004/1009 通道已知；dj 生态端点与播放链路待调研（节目音频是否同走取流）；UI 候选复用库页播客分区或并入"您的网易云" | 中（含调研） |
| 跨源歌词供应商（YT 歌用网易/QQ 词库） | 接入点全现成（getLyricsFromFormat/LyricsProvider），Lyrico 匹配算法待移植 | 中偏大 |
| 网易 MV | 搜索 type=1004 现隐藏；需打破 isVideo 恒 false 的管线假设（取流/追踪/watchtime/详情卡），走 /mv/detail + /mv/url | 中偏大 |
| M9 灰歌跨源回退 | 关注同步已完成；仅剩无版权歌曲自动切到 YT | 中 |
| M10 离线下载管线 | SimpleCache→文件式改造；公共 Download 导出路径已通（AGENTS.md 调研） | 大 |
| Listen Together 混源过滤 | 需协议层设计（数字 ID 会发给 Metrolist 客户端） | 待设计 |
| AI 三件 / 歌曲导出改造 | 调研结论在 AGENTS.md | 大 / 另评估 |

### 可做（小成本快赢）

- **写端点 405 频控的用户可见反馈**：订阅/取消收藏/红心同步失败目前静默（只刷 logcat），
  405 时 toast"操作过于频繁，请稍后再试"。
- **haze 顶栏闪烁修复推广**：库页四宫格页已修（底色兜底+fade 转场，065f3ca1），同款玻璃顶栏
  的高频页（歌单/专辑详情等）可照搬两步修法。
- 无限队列网易尾曲续播：**已打通（2026-09-20）**——无尽钩子按 ID 形状分流（YT=RDAMVM+getRelated，
  网易=NETEASE_RADIO_ 哨兵+simiSong 首批），simiSong 见底后以当前尾曲换种子续链
  （种子没变即整批撞重则停，防循环）；android+jvm 双端。详见"播放队列页增强"小节。
- RYD/SponsorBlock 对数字 ID 短路：去 logcat 噪音，和 songInfo 短路同款改法。
- 官方罗马音 romalrc：M1 遗留增强项，渲染端本地引擎现成。
- 陈旧 TODO 注释清理：HomeViewModel:184（与"独立屏"定稿相悖已过时）；MusicSourceProvider
  C_TIER（评论/艺人详情已实现，只剩云盘与播客）。
- 搜索 offset 翻页、播放页艺人卡粉丝数渲染排查。
- 网易艺人"全部歌曲"页：人气区「更多」因网易无对应歌单页已隐藏（2026-09-17，原点击报错）；
  如需恢复，`/v1/artist/songs` 原生支持 order/limit/offset 分页，加一个列表页即可，
  热门 50 首之外还能带"按时间排序"切换。

### 可优化

- **四套同构会话缓存**（RowCache/TagCache/dailySongsCache/likedIdsCache：Mutex 单飞+TTL+force
  绕过——红心缓存后已到第四套）抽公共组件。
- 红心 OR-merge 的取消方向：云端取消后本地要等下次点赞才熄灭（adopt-on 单向防误判）；
  likedIds 10min TTL 刷新发现 cloud=false 且本地亮时可回写熄灭。
- 独立屏 VM 对 selectedSource 变化的重取模式统一（Search/Home 各自 collect）。
- 分类卡封面逐张 DataStore 写改批量落盘。
- 角标判源 contentSource 对 PlaylistsResult（YT 远端形状）依赖调用上下文，混源场景如出现需补分支。

### 下一步建议顺序

**M9 灰歌回退**（体验痛点最大，被隐藏的开关也等着恢复）→ **相似歌曲独立页 / M7 评论页**
（资产全现成，纯 UI 活）→ M8 云盘 → 其余按需。
（原序首项"库页分区+迁库页"已随 M2 完成，移出。）

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
