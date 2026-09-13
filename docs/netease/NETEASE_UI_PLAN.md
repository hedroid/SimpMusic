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
| M1 | 播放链路 | ✅ 取流（数字ID路由/https升级/Mp3Extractor/音质降级链）；⚠️ 歌词未接（YRC转换器已在 core，待接歌词管线） |
| M2 | 歌单详情 + 库融合 | ✅ 歌单详情（同页路由，作者/年份/曲目/点歌播放全通）；❌ 库融合+品牌角标未做 |
| M3 | 搜索切源 | ✅ 完成（切换器 + 搜索页全链路：建议/热搜/四 tab/点歌播放/歌单同页路由/艺人 toast；2026-09 模拟器双源冒烟通过） |
| M4 | 网易主页 | ✅ 独立屏：账户卡/每日推荐(滤雷达)/私人雷达(曲目三行网格)/雷达歌单(5卡)/精品歌单/推荐新歌(96dp紧凑卡三行)/榜单区块/五组分类(网页catalog目录,3行网格) |
| M5 | 分类页 + 混合 | ✅ 分类页（NeteaseTagScreen 两列网格 + 热门/精品排序 chip + 会话缓存/下拉刷新，网页歌单广场同源）；❌ 混合页私人FM |
| M6 | 专辑/歌手页路由 | ❌ 未开始 |
| M7 | 歌曲评论区 | ❌ UI 未开始（core 端点已验证） |
| M8 | 云盘页 | ❌ UI 未开始（core 端点已验证） |
| M9 | 关注同步 + 无版权自动切源 | ❌ 未开始 |
| M10 | 网易歌曲下载管线 | ❌ 单独评估（SimpleCache→文件改造） |

## 已落地的网易主页细节

- chips：固定 8 个高频快捷（华语/欧美/日语/韩语/流行/摇滚/说唱/ACG），点击进分类网格页。
- 分类体系：`/playlist/catalogue` 全量五组（语种/风格/场景/情感/主题），每组 hot 优先 15 张。
- 分类内容：`/playlist/list`（网页同源，热度序）两页 ≈100 张。
- 性能：feed 五组并行、雷达 6 详情并行、toplist 去重缓存（Mutex+10min TTL）、
  home 10min 会话缓存（下拉刷新 force 绕过）。
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
- 专辑 tab（type=10 端点十几行）：等 M6 专辑详情页完成后一并放开。
- 艺人卡跳转：M6 完成后把 toast 换成 `ArtistDestination`（两处：建议区+结果区）。
- 封面持久缓存可选加 Room 表/DataStore 之外的批量落盘（当前每封面一次 DataStore 写，
  量小可接受）。

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
