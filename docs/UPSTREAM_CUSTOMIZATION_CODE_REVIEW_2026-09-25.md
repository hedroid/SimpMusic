# SimpMusic 上游差异代码审查

审查日期：2026-09-25

审查基线：主仓 `ea3bee56`，core `8719cf6`

补充审查：2026-09-26，网易“下载到设备”修复提交 `f96c7278`

增量审查：2026-09-30，最近四个自然日（2026-09-27 00:00 至 2026-09-30 当前 HEAD）

审查范围：当前分支相对上游的功能改造，重点检查逻辑正确性、并发、数据一致性、资源释放、UI 卡顿、后台耗电和发热风险。

## 总结

当前改造整体方向合理，尤其是以下工作值得保留：

- 播放列表当前曲动画降到 12fps，明显降低静止页面持续重绘。
- 播放页封面改成按页面数据驱动，避免切歌时重建图片请求和占位闪烁。
- 网络拉取失败时保留旧快照，避免把失败误判成空数据。
- 歌单写操作使用 `NonCancellable`，避免返回页面时 ViewModel 销毁导致请求中断。
- 云端写入后通过本地事件回写库页，减少不必要的全量刷新。

不过目前仍有若干可以从控制流直接确认的问题。建议先处理 P1，再进行封版或扩大测试。

## 整改定案与不修原因（2026-09-25 复核后定案）

复核方式：逐项对照代码核实（非照抄本单原始描述），部分项的实际影响与本单描述有出入，已在备注列注明。落点提交——core：`25361c4`/`6a28091`/`a98f2af`/`dc7330f`；主仓：`8ff63997`/`b3b5ffcc`/`8232f405`。

| 项 | 决定 | 原因 / 备注 |
|---|---|---|
| CR-01 FM 三连发 | **已修** | `return@repeat` 只跳过当次迭代，首批成功后仍固定 3 次请求且后批覆盖前批。改 `for`+`break`，android+jvm 双端。core `25361c4` |
| CR-03 红心假成功 | **已修** | `getOrDefault(false)`，与 setRemoteFollowedStatus 同款教训。全仓 grep 确认这是唯一中招点。core `6a28091` |
| CR-09 分页不退出 | **已修（二轮收口 core e0a7b36）** | `return@repeat`→可 break 循环+`distinctBy` 防御（`a98f2af`）；二轮补：游标**同值不前进**（服务端确有原地踏步形状）也立即停止，此前只挡了 null。core `a98f2af`+`e0a7b36` |
| CR-11 cookie 锁外持久化 | **已修（二轮收口 core e0a7b36）** | `mergeSetCookies` 与 `replaceCookies`/logout 的 saver 均已挪进 `cookieMutex`——全部写路径同序，logout/登录替换与在途响应并发不再能旧盖新。core `dc7330f`+`e0a7b36` |
| CR-05 事件总线 | **小修（定案）** | 不做 actor 重构（过度设计）。`drainPending(source)` 按来源分侧消费，`owner()` 判据=纯数字 id（与全仓路由约定一致）。复核发现：本单所述"check 后上线双发/下线丢失"竞态实际不可达——send 与订阅收集都在 Main 线程，check-then-emit 原子；真问题只有跨源清空丢事件。主仓 `8ff63997` |
| CR-08 自动备份 | **已修（二轮收口 主仓 fd2b5a94）** | minSdk 26→31 消除 API 兼容（`b3b5ffcc`）；二轮补修剩余两项：①openOutputStream 返回 null 不再记成功（删孤儿条目+返回 false，异常路径也清半成品，自定义目录与 MediaStore 两路同修）；②删除 URI 从 `MediaStore.Downloads` 集合改回 `Files` 集合——插入/查询都在 Files（Documents/ 路径的行不在 Downloads 集合），此前删除恒返回 0、旧备份永远清不掉。 |
| CR-02 Room 迁移绕过 | **不修（定案）** | 没有历史包袱。短路路径理论存在（Room BFS 选最短路径），但受影响设备=装过 v26/27 dev 构建且跳级直升 30+，实际不存在：自有设备均逐版本升级，3.0.0 发布版用户落地即 v29。JVM/iOS 补注册反而引入新路径问题 |
| CR-04 通知快照先写后发 | **不修（定案）** | 触发条件=WorkManager 执行中被杀（12h 一次、单次跑几十秒），概率低，接受小概率漏发；重发去重闸（历史通知行 browseId 并集）不受影响 |
| CR-12 播放回调 runBlocking | **不修** | `list.size > 3` 先短路，`runBlocking` 仅队列 ≤3 首的小队列才执行，DataStore 热读为内存命中（微秒级）。已知 ANR 根因在 mayBeSavePlaybackState（另行专项），与本处无关 |
| CR-13 本地歌单同步 | **不修** | 缺陷属实，但本地歌单功能已整体下线、无 UI 入口，唯一调用点在隐藏页面——死代码，功能复活时再修 |
| CR-15 网易仓库懒加载 | **不修** | 代价仅启动多建一个 Ktor client（毫秒级），上游本就存在大量 `createdAtStart`；有启动耗时实测数据再动 |
| CR-16 分类封面锁 | **不修** | 锁内 400ms delay 是刻意的 405 频控设计（见 PITFALLS），被阻塞的仅是空态分类卡的装饰性封面回填，不阻塞页面主体 |
| CR-17 1080 封面内存 | **部分修+实测定案（B2=11984ce1；profiler 验证 2026-09-25 完成）** | ①"ViewModel 长持 Bitmap"证实并已修：播放页销毁清 `screenData.bitmap`。②AM 磨砂降采样（B1）不做：共享缓存条目+RenderEffect GPU blur，降采样反而新增条目。③**模拟器实测（网易红心歌单连续切歌 150 次，`am dumpheap`+自写 HPROF 解析器数实例）**：播放页开着，100 切后 Bitmap 实例 74 个、累计 150 切后 71 个——**计数持平=无逐曲泄漏，由 coil 内存缓存 LRU 上限管理**；Java 堆全程平台期（157→114→113→122→139MB 无线性增长），Native 堆 87→183→145MB（LRU 填充后正常驱逐回落）；**关播放页 74→46（-28）实证 B2 释放生效**，剩余为 LRU 常驻缓存（快速重开用）。CR-17"十几 MiB"量级属实但性质=有界缓存非泄漏 |
| CR-06 搜索串台 | **已修（2026-09-30 主仓 749e8600）** | searchSongs/searchAll/loadMoreSongs 全部存 job,任何新搜索先 cancelInFlightSearch 取消旧搜索+旧翻页(searchAll 与 searchSongs 都写 searchSongsResult,任一路径晚到的旧结果会覆盖新查询);取消时统一复位 songsLoadingMore(被取消的 loadMore 走不到任何复位分支,会永久卡 true 堵死翻页)。取消已足够,无需 generation |
| CR-07 歌词竞态 | **已修（2026-09-30 主仓 749e8600）** | 仅罗马音分支加显式校验(it.lyricsVideoId==videoId 才写):它是全链唯一不经 updateLyrics 的歌词写入,不触碰 lyricsVideoId 吃不到重拉自愈,请求在途切歌会把旧歌罗马音挂到新歌整首;翻译/AI 分支均走 updateLyrics 自愈,维持不动 |
| CR-10 QR client 泄漏 | **已修（2026-09-30 core 38ee9aa+主仓 749e8600）** | 会话新增 close() 显式关独立引擎,onCleared 先取消 pollJob 再 close |
| CR-14 通知差集 | **已修（2026-09-30 主仓 749e8600）** | O(n²) 重构为预计算集合(current-saved 语义等价);空快照基线判据从'列表非空'改'快照行存在'(从未有发行的艺人第一张专辑不再漏通知),配套写快照门从'一侧成功就写'改'双侧都成功才写',保证行存在⟺双侧基线有效;任一侧失败跳过写入旧行原样保留,原防重语义不变 |
| CR-18 网易发行扫描重复分页 | **已修（core 4862d64）** | NotifyWork 的 ALBUM/SINGLE 两条 flow 各自调用同一全量分页端点，每位艺人最多并发两套×10 页请求。修法=`getArtistMoreAlbums` 同艺人**单飞+30s 短窗复用**（先到者锁内翻页，后来者直接拿同一份再按 type 拆分；失败不缓存；跨 12h 扫描轮次必然重拉）——不动 NotifyWork 的双源通用结构，全量分页每艺人只跑一遍，风控暴露减半。 |
| CR-19 网易下载未校验 HTTP 状态 | **已修（主仓 5004b42a）** | execute 回调入口 `response.status.isSuccess()` 检查（audioUrl 有 10 分钟有效期，过期/区域拒绝的 4xx/5xx 错误正文此前会被存成 mp3/flac 还报完成）；读完核对 Content-Length，没读满抛 `incomplete download: read/total`。模拟器飞行模式实测两分支均见错误弹窗 |
| CR-20 网易重复下载会先破坏旧文件 | **已修（主仓 5004b42a）** | 封面/音频改 staged write：先写同目录 `.part`，写满 close 后 rename 提交——同目录 rename 对已存在目标是原子覆盖，替代"先删旧文件再直写"，顺带绕开 FUSE 新建同名 EEXIST；失败/取消 finally 清 `.part`，catch 重抛 CancellationException。实测：下载 54% 断网，旧 flac md5 全程不变、无 `.part` 残留 |
| CR-21 下载文件名漏过滤 `/` | **已修（主仓 5004b42a）** | 清理正则补 `/` 与 `\x00-\x1f` 控制字符、尾部点号修剪、清空兜底 `download_<videoId>`、截 100 字符防超长。`AC/DC`→`ACDC` 已验证（正则断言） |

另：`PlaylistViewModel.kt` 4 处尾随空格与 3 处文件尾空行已清理（`8232f405`）。

## 2026-09-30 增量：最近四日代码审查

### 审查范围与结论

- 时间范围：2026-09-27 00:00（Asia/Shanghai）至 2026-09-30 当前 HEAD。
- 主仓：`42d16c177edffbb0bb629998b2a80cf6c9daf388..5afef21fc8ee`，75 个提交。
- core：`d6528d5ba3c172be792004c9d28a22d604488a08..9a62ae8cb71a`，28 个提交。
- 方法：逐项检查主仓与 core 增量、调用链和 Android/JVM 对称实现；本轮只做静态审查与文档更新，没有构建、模拟器或真机回归。

结论不是“播客完全隔离、没有影响歌曲”。播客的队列续页主路径已经正确按前缀隔离，但 UI 为兼容旧恢复队列加入的启发式判断会把普通网易专辑队列误判为播客；播放页还会为普通网易歌曲主动发起播客节目反查。这两项会直接影响原有歌曲功能与播放流畅性，应先修。

| 检查主题 | 已落实 | 尚未闭环 |
|---|---|---|
| 播客对歌曲的隔离 | core `loadMore` 对 `NETEASE_PODCAST_` 早退；电台队列只续本台节目 | 普通网易专辑队列会被 UI 指纹误判为播客；普通歌曲会触发播客评论反查 |
| 分页 | 分类页、详情页均有近底预取；播放器队列以服务端原始页大小推进 offset；Android/JVM 同构 | 分类双 tab 有回包串台；详情页以过滤后的可播数作 offset，会重页、提前到底 |
| 缓存/恢复 | 播客 tab 有 Koin 单例会话缓存；电台进度落 DataStore；队列身份三键单次 `edit` | 续页进队但未进详情 VM 的节目不落续播记忆；跨 DataStore/Room 的队列快照仍非原子 |
| 并行/流畅性 | 播客首页 8 路请求真实并行；Mix 首页 4 路 `async`；FM 倒数第 3 张预取 | 播客刷新可叠加 8×N 请求；byradio 只串行“发车间隔”而非请求；FM 在飞闸门设置晚一拍 |

### 本轮问题总表

| 编号 | 等级 | 状态 | 问题 |
|---|---|---|---|
| CR-22 | P1 | **已修（2026-09-30 core aa6c622+主仓 8f114971）** | 播客启发式会把普通网易专辑队列判成播客，隐藏/禁用歌曲功能。修=QueueData.Data.isNeteasePodcastQueue 只认前缀,六处 UI 门控统一替换 |
| CR-23 | P1 | **已修（2026-09-30 主仓 8f114971）** | 普通网易歌曲切歌也会后台反查最多 5 页播客节目。修=反查收紧为'播客队列+评论面板已开'才解析,面板等 threadId 落地再首载 |
| CR-24 | P1 | **已修（2026-09-30 主仓 8f114971）** | 分类页双 tab 分页回包写入“当前 tab”，快速切换会串数据并卡 loading。修=捕获 requestedTab+generation 换分类丢包(loadTab 同病一并堵) |
| CR-25 | P1 | **已修（2026-09-30 主仓 8f114971）** | 电台详情分页用过滤后数量作 offset，会重复页并提前结束。修=UiState 新增 rawProgramCount 服务端游标,首页/续页/POD 起播令牌三处换用 |
| CR-26 | P2 | **已修主缺口（2026-09-30 core aa6c622+主仓 8f114971）** | 续播缓存只覆盖详情 VM 已加载节目。修=Track/ResultSong 携带 neteaseProgramId(core 续页同链携带),记忆从队列 Track O(1) 直读。遗留:resumePlayback 目标节目不在详情页已载列表时仍 playFrom(0)(定位需按 offset 分页查,收益低未做) |
| CR-27 | P2 | **已修（2026-09-30 主仓 8f114971）** | 播客首页并行刷新没有 in-flight/job 管理。修=八路收进单个可取消 refreshJob+refreshing 流(首批落地/600ms 先到先收),UI isRefreshing 接真值 |
| CR-28 | P2 | **已修（2026-09-30 core aa6c622）** | byradio 的互斥锁未覆盖 HTTP。修=请求纳入锁内真串行(取消随协程释放不占锁) |
| CR-29 | P2 | **不修（2026-09-30 定案）** | 队列身份三键虽原子,但跨 DataStore/Room 无同一快照事务。跨存储原子性需 snapshotVersion 单调解锁或合并可事务存储=架构级改造,与 mayBeSavePlaybackState ANR 同待专项;现有'当前曲必须属于 listTracks'守卫保留 |
| CR-30 | P2 | **已修（2026-09-30 主仓 f5a67846,随并行会话入库）** | 库页标题 500ms 静默窗只丢弃状态。修=窗内意愿记账 pendingTitleFlip,窗到期 LaunchedEffect 回放;仅锁定高度页(网格 chip)回放——下载管理页(唯一实时高度页)窗内上报可能是收展动画几何反馈,回放会把四轮修掉的贴底振荡以 500ms 节奏请回来,只吞不回放 |
| CR-31 | P2 | **已修（2026-09-30 主仓 8f114971）** | 下载歌曲空态不再上报“在顶”。修=空态 LaunchedEffect(Unit) 上报在顶;Playlists 空态走网格第 0 项初始发射本就上报,核实无需动 |
| CR-32 | P2 | **已修（2026-09-30 主仓 8f114971）** | FM 分页在飞闸门进入协程后才置位。修=同步置位再 launch+try/finally 清位 |

### CR-22 播客启发式会把普通网易专辑队列判成播客【P1·已修:core aa6c622+主仓 8f114971】

- 播放页：`composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/screen/player/NowPlayingScreen.kt:659`
- 迷你播放条：`composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/screen/MiniPlayer.kt:176`
- 信息、队列和歌曲菜单：`composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/component/ModalBottomSheet.kt:381`、`:1151`、`:1749`

重复出现的兜底条件为：队列至少 3 首、所有 `videoId` 都是数字、所有曲目的 `album.id` 相同且为数字。这不只是播客特征，也正好是普通网易专辑整队播放的正常形状。因此播放任意至少 3 首的网易专辑时，都可能进入播客 UI 分支。

影响：

- 迷你条红心被隐藏。
- 播放页红心、歌词、艺人/专辑信息或歌曲向卡片被隐藏或置换。
- 三点菜单中的添加到歌单等歌曲操作被隐藏。
- 队列页无尽播放开关被隐藏。
- 同一错误判断复制在至少 6 处，后续很容易继续漂移。

core `fd62b5a` 已经持久化并恢复真实 `playlistId/playlistType/continuation`，不应再用“专辑形状”猜队列类型。建议抽出唯一的 `QueueData.isNeteasePodcastQueue`，只认明确前缀；如必须兼容旧快照，应给快照增加显式 queue kind/版本，而不是从 Track 的专辑字段推断。

### CR-23 普通网易歌曲切歌也会后台反查最多 5 页播客节目【P1·已修:主仓 8f114971】

- 位置：`composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/screen/player/NowPlayingScreen.kt:879`

`podcastProgramThreadId` 的 `produceState` 只以 `nowPlayingVideoId` 为 key，不要求当前队列是播客，也不要求用户已经打开评论面板。普通网易歌曲同样有数字 `album.id`，所以每次切歌都可能把专辑 ID 当成 radioId，调用 `getDjRadioProgramsPage`，最多连续翻 5 页。

影响：

- 用户不打开评论也会发生无价值网络请求，单次切歌最多 5 次。
- 与真正的电台详情/队列续页共用 byradio 端点和 800ms 限流器，会让有效请求排队，并增加 405/空数据概率。
- 产生额外协程、日志、网络唤醒和状态更新，直接影响切歌后的稳定与流畅性。

建议只在“明确播客队列且评论面板已打开”时解析；更稳妥的做法是在节目转 Track 时直接保存 `programId/commentThreadId`，评论入口 O(1) 读取，彻底取消最多 150 期的反向搜索。

### CR-24 分类页双 tab 分页回包会串到当前 tab【P1·已修:主仓 8f114971】

- 位置：`composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/NeteasePodcastCategoryViewModel.kt:72`
- 触发器：`composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/screen/library/NeteasePodcastCategoryScreen.kt:123`

`loadMore()` 发请求时用的是调用瞬间的 `state.tab.endpointType`，但成功和失败回包却读取 `s.tab` 并更新当前 tab。若 RISING 续页在飞时切到 HOT，RISING 返回的数据会追加到 HOT；原 RISING 的 `loadingMore` 还可能永久留在 true。

建议请求前捕获 `requestedTab`、`requestedCategoryId` 和 generation，回包只更新 `charts[requestedTab]`；分类 ID 或 generation 已变化则直接丢弃。补快速切 tab + 慢回包测试。

### CR-25 电台详情分页 offset 使用过滤后数量【P1·已修:主仓 8f114971】

- 首页过滤：`composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/NeteaseRadioDetailViewModel.kt:161`
- 续页 offset：同文件 `:184`
- 起播续页令牌：同文件 `:242`
- UI 近底触发：`composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/screen/library/NeteaseRadioDetailScreen.kt:117`

服务端页先过滤掉 `mainSongId == null` 的不可播节目，状态只保存可播列表；后续却以 `state.programs.size` 作为服务端 offset，组队时的 `POD*_<offset>` 也用同一数值。只要前页存在不可播节目，下一页 offset 就会回退并重取旧项；去重后的 `fresh` 可能为空，随后把 `hasMore` 错置为 false，后续节目永久不可达。

core 播放器续页已正确按服务端原始 `programs.size` 推进（Android `MediaServiceHandlerImpl.kt:2159`，JVM 有同构实现），详情 VM 应复用同一口径：在 UiState 单独保存 `nextOffset/rawConsumedCount`，显示列表过滤与服务端游标完全解耦。

### CR-26 续播缓存不覆盖播放器续页追加的节目【P2·已修主缺口:core aa6c622+主仓 8f114971;resume 定位未载节目仍回 playFrom(0)】

- 周期保存：`composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/NeteaseRadioDetailViewModel.kt:85`
- 恢复定位：同文件 `:211`
- core 队列独立续页：`core/data/src/androidMain/kotlin/com/maxrave/data/mediaservice/MediaServiceHandlerImpl.kt:2110`

缓存确实存在：每 10 秒把 `podcast_resume_<radioId>=programId|positionMs` 写入 DataStore。但 `programId` 只通过详情 VM 当前的 `_uiState.programs` 反查。播放器在 core 内续到第 2 页以后，新增节目只进入队列，不会同步回详情 VM，因此播放这些节目时 `pid == null`，不会更新缓存。

另一个缺口是恢复只在当前已载列表查 `resumeProgramId`；目标节目不在第一页时直接 `playFrom(0)`，已保存的位置也失效。

建议在节目转 Track 时携带 programId，缓存从当前 Track 元数据直接读取；恢复时按 raw offset 分页查到目标节目，或保存足够的 offset/index 使其可直接定位。

### CR-27 播客首页并行刷新可无限叠加【P2·已修:主仓 8f114971】

- 并行发起：`composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/NeteasePodcastViewModel.kt:66`
- 下拉入口：`composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/screen/library/NeteasePodcastScreen.kt:111`

并行本身已经落实：分类、推荐、热门、新晋、节目榜、个性化、订阅、最新节目共 8 路独立 `launch`，不是串行。但 VM 没有 refresh job、generation 或 in-flight 守卫，UI 又把 `isRefreshing` 固定为 false。连续下拉会形成 8×N 个并发请求，旧批次可在新批次之后回写，且用户看不到真实刷新生命周期。

建议保留独立请求并行，但把它们收进单个结构化 `refreshJob`：新刷新取消旧刷新或通过 generation 丢弃旧回包；暴露真实 refreshing，至少在飞期间拒绝重复手势。不要把 8 路重新改成串行。

### CR-28 byradio 互斥锁没有串行 HTTP 请求【P2·已修:core aa6c622】

- 位置：`core/data/src/commonMain/kotlin/com/maxrave/data/repository/NeteaseRepositoryImpl.kt:1875`

注释承诺“串行+最小间隔”，但 `byradioMutex.withLock` 只包住等待和更新时间戳，`client.djRadioPrograms(...)` 在锁外执行。两个慢请求只会错开开始时间，仍可重叠在网络中；CR-23 的误触发还会扩大这一问题。

若端点必须严格串行，应把请求纳入锁或使用单消费者 Channel/actor；如果只要求限速，则应把注释改为“限制发车间隔”，并明确允许在途重叠。无论采用哪种口径，都应保证取消不会永久占锁或破坏下一次间隔。

### CR-29 队列持久化不是跨存储的同一快照【P2·不修:snapshotVersion 架构级,与 ANR 专项同候】

- 保存入口：`core/data/src/androidMain/kotlin/com/maxrave/data/mediaservice/MediaServiceHandlerImpl.kt:2796`
- 身份三键：`core/data/src/commonMain/kotlin/com/maxrave/data/dataStore/DataStoreManagerImpl.kt:388`

`playlistId/playlistType/continuation` 三键在一个 DataStore `edit` 中，三键自身是原子的；但 recent song、playlist name、三键身份、Room 队列仍通过多个 suspend 调用依次写入。进程在中间退出或两个异步 `mayBeSaveRecentSong(false)` 交错时，恢复侧仍可能组合出不同代的 recent/身份/队列。

建议给 DataStore 元数据和 Room 队列都写同一个单调 snapshotVersion，恢复时只接受版本一致的组合；或把恢复必需的队列身份与队列实体并入一个可事务提交的存储。现有“当前曲必须属于 listTracks”的守卫应保留，但它不能提供跨存储原子性。

### CR-30 库页标题静默窗丢弃最终滚动状态【P2·已修:主仓 f5a67846,锁定高度页回放/实时高度页只吞】

- 位置：`composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/screen/library/LibraryScreen.kt:345`

标题翻转后 500ms 内所有相反状态都被直接吞掉，窗口结束时没有读取或回放最新 `onTop`。如果用户在窗口内快速反向滑动或切到一个已保存为相反滚动态的 chip，最终标题可能停在错误状态，直到下一次列表 index 变化才自愈。

建议保留“动画期间不翻转”的抗振荡策略，但记录 `pendingOnTop`，窗口结束后只应用最后一个值；或用可取消 debounce/sample 状态机，避免把用户最终状态永久丢掉。

### CR-31 下载歌曲空态不会恢复标题展开【P2·已修:主仓 8f114971】

- 位置：`composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/screen/library/LibraryCollectionScreen.kt:245`

下载 Songs 非空时会通过 LazyList 上报 `onScrolling(true)`；空态只显示居中文案，不上报。切页时标题又不再统一复位，因此从一个收起标题的深滚动页面切到“无下载歌曲”时，空页面会继承收起态且没有任何滚动事件可纠正。

建议空态进入时用 `LaunchedEffect(Unit) { onScrolling(true) }` 明确声明在顶；Playlists 空态也应核对同一约定。

### CR-32 FM 分页在飞闸门置位晚一拍【P2·已修:主仓 8f114971】

- VM：`composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/NeteaseMixViewModel.kt:194`
- 近尾预取：`composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/screen/home/NeteaseMixScreen.kt:416`

倒数第 3 张预取方向正确，能把网络等待移出行尾；但 `loadMoreFm()` 在调用点检查 `_fmLoadingMore` 后，进入 `viewModelScope.launch` 才把它置 true。近尾预取和下拉刷新在协程真正调度前各调用一次时，两次都会通过守卫并发拉批；同时置位/清位还没有 `try/finally`，取消或异常可能留下错误状态。

建议在 `launch` 之前原子置位（`compareAndSet(false, true)`），协程内 `try/finally` 清理。去重合并只能避免重复显示，不能抵消重复网络、405 与回包乱序成本。

### 本轮正向确认与验证限制

- 播客队列不会再落入歌曲无尽电台：Android `MediaServiceHandlerImpl.kt:1577` 与 JVM 对应实现都按 `NETEASE_PODCAST_` 早退，RADIO 子类型只续本台节目，LATEST/TOPLIST 播完即止。
- 播放器队列分页推进正确：core 以服务端原始页大小推进 offset，失败保留令牌重试；问题只在详情 VM 的 UI 分页口径没有同步。
- 分类页与详情页都存在真实的近底分页触发，不是“只写了 API 没接 UI”；但 CR-24/25 会破坏分页完整性。
- 播客首页“最新节目”是固定 shelf：只请求 `offset=0` 的 30 条、界面展示前 10 条，`more` 被忽略，点击后以这 30 条组队且播完即止。按当前产品形态这是有意的首屏内容，不算漏接分页；若未来增加“查看全部”，必须另接完整分页，不能直接复用当前 shelf 状态。
- 缓存不是完全没做：播客 tab 由 Koin `single` + `loadedOnce` 提供进程内缓存，电台续播写 DataStore，队列身份也落 DataStore；问题是覆盖范围和跨存储一致性不足，而不是零缓存。
- 并行不是完全没做：播客首页多路请求和 Mix 首页 4 路请求都真实并行，电台详情“先详情、后节目”是为了依赖 `programCount` 的有意串行；需要修的是无界重复并发和限流语义，不应笼统把所有请求都改成并行。
- 按项目约定，本轮未执行 Gradle 构建；上述问题来自控制流与状态机静态核实。修复后至少应补：普通网易专辑播放回归、快速切分类 tab、含不可播节目的多页电台、播放到 core 续页后强杀恢复、连续下拉刷新、弱网多电台切换、下载空态标题、FM 近尾+下拉并发。

### 2026-09-30 处置定案（本轮已实施）

- **已修 10 项**：CR-22/23/24/25（P1 全清）+ CR-26 主缺口/27/28/30/31/32。提交：core `aa6c622`（22/26/28）、主仓 `8f114971`（22~28/31/32 的 UI/VM 侧）、主仓 `f5a67846`（30,与并行会话的库页源门控修复同文件入库）。
- **不修 1 项**：CR-29（跨存储快照原子性=snapshotVersion 架构级改造，与 mayBeSavePlaybackState ANR 同待专项）。
- **遗留小缺口**：CR-26 的 resumePlayback 目标节目不在详情页已载列表时仍 `playFrom(0)`（按 offset 分页定位收益低，未做）；CR-22 弃用形状指纹后，fd62b5a 之前保存的旧队列快照恢复时会被判非播客（一次性升级过渡窗口，首次保存自愈）。
- **验证口径**：本轮仅静态核实+编译验证（` :composeApp:compileAndroidMain`/`:domain`/`:data`/`:netease` 的 android+jvm 变体全过），**未跑模拟器/真机回归**。建议回归清单沿用上节：普通网易专辑（≥3 首整队）播放回归是 CR-22 的最高优先验证项（红心/歌词/歌曲菜单/无尽开关应全部恢复）、播客队列反向回归（上述条目应仍隐藏）、快速切分类 tab、含不可播节目的多页电台翻页、播到 core 续页后收听记忆落盘、连续下拉播客首页、下载空态标题、FM 近尾+下拉并发。

## P1：建议封版前修复

### CR-01 私人 FM 成功后仍会固定请求三次【已修 core 25361c4】

- 位置：`core/data/src/androidMain/kotlin/com/maxrave/data/mediaservice/MediaServiceHandlerImpl.kt:1985`
- JVM 对应实现也有同样问题。

代码在 `repeat(3)` 内使用 `return@repeat`。这只会结束当前迭代，不会退出整个循环。因此第一次拿到有效 FM 批次后仍会继续发起两次请求，并可能用后续结果覆盖前面的结果。

影响：

- FM 拉取流量和网络唤醒次数最多放大到三倍。
- 增加网易接口风控、405 和后台发热风险。
- 后续批次可能覆盖首次成功结果。

建议：改为普通 `for` 循环并在成功时 `break`，Android/JVM 两端同步修改并补单元测试。

### CR-02 Room 升级路径可能绕过网易 source 回填【不修·定案：无历史包袱】

- 位置：`core/data/src/commonMain/kotlin/com/maxrave/data/db/MusicDatabase.kt:101`
- Android 手工迁移：`core/data/src/androidMain/kotlin/com/maxrave/data/db/MusicDatabase.android.kt:30`

数据库提供了手工 `27 -> 28` 迁移，用来把历史纯数字歌曲和歌单标记为 `NETEASE`；但同时又声明了自动 `27 -> 29` 和 `26 -> 29`。Room 可能直接走跨版本路径，跳过 `27 -> 28` 回填。JVM 和 iOS builder 也没有注册这条手工迁移。

影响：历史网易数据可能继续被当作 YouTube 数据，导致分源、收藏、播放和列表匹配异常。

建议：

- 移除会绕过数据回填的自动路径，或在所有跨越 28 的路径中执行相同回填。
- 明确 Android/JVM/iOS 的迁移策略。
- 增加 26、27、28、29 到最新版的 migration tests，并校验历史数字 ID 的 source。

### CR-03 网易红心业务失败仍会更新本地缓存【已修 core 6a28091】

- 位置：`core/data/src/commonMain/kotlin/com/maxrave/data/repository/NeteaseRepositoryImpl.kt:1172`
- 端点返回约定：`core/service/netease/src/commonMain/kotlin/com/maxrave/netease/NeteaseEndpoints.kt:677`

`likeSong` 在 HTTP 成功但业务 `code != 200` 时返回 `Result.success(false)`；`setSongLiked` 却使用 `result.isSuccess` 判断是否更新缓存。因此服务端拒绝操作后，本地缓存仍会切换红心状态。

影响：UI 虽可能提示失败，但后续读取会看到错误的云端红心状态，直到缓存过期或重新拉取。

建议：改为 `result.getOrDefault(false)`，只有业务结果为 true 才更新缓存。顺带全仓检查所有返回 `Result<Boolean>` 的云端写操作，禁止仅判断 `isSuccess`。

### CR-04 新发行通知存在永久漏发窗口【不修·定案：接受小概率漏发】

- 快照写入：`androidApp/src/main/java/com/maxrave/simpmusic/service/test/notification/NotifyWork.kt:118`
- 通知投递与历史写入：`androidApp/src/main/java/com/maxrave/simpmusic/service/test/notification/NotifyWork.kt:137`

当前流程在扫描每位歌手时立即更新发行快照，等全部扫描结束后才统一发送系统通知并插入通知历史。如果进程在这两个阶段之间被停止、崩溃或取消，新发行已经被标记为“见过”，下一轮不会再次进入差集，用户将永久收不到这次通知。

建议：采用按艺人提交或 outbox 模式：

1. 计算差集。
2. 在同一持久化阶段写入待投递事件和新快照。
3. 投递系统通知。
4. 标记事件已投递。

至少应避免在通知事件持久化之前推进快照。

### CR-05 LibraryMutationBus 存在事件重复和丢失竞争【小修·定案 8ff63997：按来源分侧 drain】

- 事件总线：`composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/LibraryMutationBus.kt:54`
- YT pending 消费：`composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/LibraryViewModel.kt:442`
- 网易 pending 消费：`composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/LibraryViewModel.kt:493`

`subscriptionCount` 检查、加入 pending、`tryEmit` 是三个独立步骤：

- collector 在检查后上线时，同一事件可能既实时送达又进入 pending，之后重复应用。
- collector 在检查后下线时，事件可能既没有入队，也没有送达。
- YT 和网易加载完成后分别清空同一个 pending 队列。先完成的一侧可能取走另一来源的移除事件；移除逻辑只在对应状态为 `Success` 时生效，因此事件可能被永久丢弃。

建议：改为单写者 actor/reducer，所有事件先可靠入队，带上来源和单调序号；对应状态成功应用后再确认消费。不要通过瞬时 `subscriptionCount` 决定是否持久化。

### CR-06 搜索分页会串入旧查询结果【已修：主仓 749e8600】

- 位置：`composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/SearchViewModel.kt:225`

新的 `searchSongs` 不会取消旧搜索，也不会取消旧的加载更多任务。快速输入 A 后再搜 B 时：

- A 可能晚于 B 返回并覆盖 B。
- A 的下一页可能追加到 B 的结果中。
- 全局 `lastQuery` 与旧 continuation 可能不属于同一个查询。

建议：保存 `searchJob` 和 `loadMoreJob`；查询词、来源或搜索类型改变时全部取消。每次搜索生成 generation/token，所有响应写状态前校验仍属于当前查询。

### CR-07 网易歌词存在跨歌曲写入竞争【已修：仅罗马音分支,主仓 749e8600】

- 空结果写入：`composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/SharedViewModel.kt:1396`
- 网易歌词与罗马音写入：`composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/SharedViewModel.kt:1716`

快速切歌后，旧歌曲的异步请求仍可能完成：

- 旧歌曲的空结果会无条件清空当前歌曲歌词，并把 `lyricsVideoId` 写成旧 ID。
- 旧歌曲的官方罗马音直接更新当前 `lyricsData`，没有校验当前播放 ID。
- 每次请求由独立 `viewModelScope.launch` 启动，没有统一可取消的歌词任务。

建议：使用一个可替换的 `lyricsJob`，切歌时取消旧任务；所有主歌词、空结果、翻译、罗马音和延迟清理写入都必须检查当前 `videoId` 或 generation。

### CR-08 自动备份兼容与假成功【已修：minSdk 31 + 假成功/清理 URI 二轮收口 主仓 fd2b5a94】

- 自定义目录写入：`androidApp/src/main/java/com/maxrave/simpmusic/service/backup/AutoBackupWorker.kt:172`
- MediaStore 插入：`androidApp/src/main/java/com/maxrave/simpmusic/service/backup/AutoBackupWorker.kt:196`
- 旧备份删除：`androidApp/src/main/java/com/maxrave/simpmusic/service/backup/AutoBackupWorker.kt:269`

项目已把 `minSdk` 从 26 提升到 31，因此 API 29 字段的兼容问题和对应 lint error 已消除。

仍未解决的逻辑问题：

- 自定义目录和 MediaStore 分支在 `openOutputStream()` 返回 null 时仍记录成功并返回 true，导致最后备份时间被更新，但文件实际没有写出。
- 插入和查询使用 `MediaStore.Files.getContentUri(VOLUME_EXTERNAL_PRIMARY)`，删除旧文件却拼接 `MediaStore.Downloads.EXTERNAL_CONTENT_URI`，collection 不一致，旧备份可能无法删除。

建议：

- 只有输出流成功打开且复制完整后才返回 true。
- 写入失败时删除已经创建的空 MediaStore/Document 条目。
- 删除时复用查询所对应的 `MediaStore.Files` URI。
- 增加 null output stream 与超过保留数量后的清理测试。

## P2：建议进入下一轮修复

### CR-09 两个网易分页封装无法提前退出【已修：a98f2af + 同值游标二轮收口 core e0a7b36】

- `core/service/netease/src/commonMain/kotlin/com/maxrave/netease/NeteaseEndpoints.kt:399`
- `core/service/netease/src/commonMain/kotlin/com/maxrave/netease/NeteaseEndpoints.kt:452`

同样误用了 `return@repeat`。没有下一页或请求失败后仍继续循环；高质量歌单在没有新游标时还可能重复请求同一页并追加重复数据。

`return@repeat`、失败和 null 游标已经修复，并增加了结果去重。剩余问题是服务端返回相同的非空 `nextBefore` 时游标仍未前进，循环会继续请求同一页；历史上已经观察过服务端游标重复。

建议取出 `next` 后，在 `next == null || next == cursor` 时立即停止，再更新 cursor。

### CR-10 扫码登录专用 HttpClient 未关闭【已修：core 38ee9aa+主仓 749e8600】

- client 创建：`core/service/netease/src/commonMain/kotlin/com/maxrave/netease/NeteaseQrLoginSession.kt:38`
- ViewModel 清理：`composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/NeteaseLoginViewModel.kt:219`

`NeteaseQrLoginSession` 自建 Ktor `HttpClient`，但没有 `close()`，ViewModel 的 `onCleared()` 只取消轮询任务。反复进入登录页可能积累连接池和网络引擎资源。

建议让 session 实现 `Closeable`/显式 `close()`，在 `onCleared()` 中取消请求并关闭 client。

### CR-11 Cookie 持久化存在旧快照覆盖新快照的竞争【已修：dc7330f + replaceCookies 二轮收口 core e0a7b36】

- 位置：`core/service/netease/src/commonMain/kotlin/com/maxrave/netease/NeteaseClient.kt:273`

响应 cookie 合并路径已经把 `cookieSaver` 移到 mutex 内，两个响应之间的持久化顺序已稳定。但 `replaceCookies()` 仍然先在锁内替换内存值，释放锁后才调用 saver；logout/登录替换与在途响应并发时，仍可能用旧快照覆盖响应刚写入的新快照。

建议 `replaceCookies()` 也在同一个 `cookieMutex.withLock` 中完成内存替换和持久化，保证所有写路径遵守同一顺序。读取 `sessionCookies` 的路径也应使用同一种同步策略。

### CR-12 播放器回调中同步读取 DataStore【不修】

- 位置：`core/data/src/androidMain/kotlin/com/maxrave/data/mediaservice/MediaServiceHandlerImpl.kt:3205`

每次 `onMediaItemTransition` 都通过 `runBlocking` 读取 `endlessQueue`。缓存命中时通常很快，但遇到 DataStore 初始化或串行写入时会阻塞播放器回调，引起切歌、通知和 UI 状态更新延迟。

建议在 service 生命周期内持续 collect 到内存中的 `StateFlow`/字段，切歌路径只读取内存值。其余播放热路径里的 `runBlocking` 也应一并盘点。

### CR-13 本地歌单同步网易会把网络失败当成空歌单【不修：死代码，功能复活再修】

- 位置：`core/data/src/commonMain/kotlin/com/maxrave/data/repository/LocalPlaylistRepositoryImpl.kt:541`

远端曲目 ID 拉取失败后通过 `orEmpty()` 变成空集合，可能把本地全部曲目重新添加到已有歌单。分批添加时，某些批次失败也会被忽略，最终仍保存远端歌单 ID并返回整体成功。

建议：

- 拉取远端基线失败时终止同步，不能按空列表处理。
- 任一批次失败时返回明确的 partial failure，不更新“已完成同步”状态。
- 记录成功/失败批次，允许安全重试。

### CR-14 通知差集计算存在 O(n²) 分配【已修：主仓 749e8600,含空快照基线判据】

- 位置：`androidApp/src/main/java/com/maxrave/simpmusic/service/test/notification/NotifyWork.kt:76`

当前在每个专辑/单曲的 `filter` 内重复执行 `map`、对称差和 `contains`。发行量较多时会制造大量临时集合和 GC，延长 WorkManager 执行时间。

建议在循环外一次计算：

- `savedIds`
- `currentIds`
- `newIds = currentIds - savedIds - notifiedIds`

随后按 `newIds` 过滤实体即可。

另一个边缘问题：`savedAlbum.isNullOrEmpty()` 把“已有合法空快照”和“从未建立基线”混为一谈。空快照之后出现第一张发行时可能不通知，应使用“快照行是否存在”判断基线是否建立。

### CR-15 网易仓库并未真正懒加载【不修】

- 位置：`core/data/src/commonMain/kotlin/com/maxrave/data/di/RepositoryModule.kt:47`

注释称网易仓库是 lazy，但 `HomeRepository` 和 `SearchRepository` 都是 `createdAtStart = true`，其路由实现立即依赖网易仓库。因此仅使用 YouTube 的用户启动时也会创建网易仓库和网络 client。

建议让路由实现接收 provider/lazy，或取消不必要的 eager 创建，并用启动 tracing 验证收益。

### CR-16 分类封面互斥锁覆盖延时、网络和持久化【不修：刻意的 405 频控设计】

- 位置：`core/data/src/commonMain/kotlin/com/maxrave/data/repository/NeteaseRepositoryImpl.kt:190`

`artworkMutex` 在持锁期间执行 400ms delay、网络请求和 DataStore 写入。分类卡预取排队时，用户主动打开分类页后的封面回写也可能被同一锁阻塞。

限频思路正确，但建议改成专用串行请求队列；网络限速、内存缓存和持久化分别管理，不要让用户可见路径等待整个后台预取队列。

### CR-17 1080 封面会增加常驻内存压力【B2 已修 11984ce1；B1 评估后不做（共享缓存条目+GPU blur，降采样反而更差）】

1080×1080 RGBA 位图约占 4.4 MiB，而 544 图约 1.1 MiB。pager 邻页、Apple Music 模糊背景、图片缓存及 ViewModel 保存当前 `ImageBitmap` 叠加后，播放页可能持有十几 MiB 位图。

这不是严格意义上的泄漏，但在低内存设备上会增加 GC 和重解码概率。

建议：

- 根据真实物理槽位和设备密度请求尺寸，而不是固定 1080。
- 调色完成后仅保存 palette/颜色结果，不长期保存原始 Bitmap。
- 页面关闭或不再需要时清理 ViewModel 中的位图引用。
- 使用内存 profiler 验证连续切歌 50～100 次后的 retained bitmap 数量。

### CR-18 网易发行扫描对同一艺人执行两次完整分页【已修 core 4862d64：同艺人单飞+30s 短窗复用】

- 调用位置：`androidApp/src/main/java/com/maxrave/simpmusic/service/test/notification/NotifyWork.kt:51`
- 分页实现：`core/data/src/commonMain/kotlin/com/maxrave/data/repository/NeteaseRepositoryImpl.kt:1732`

NotifyWork 使用 `combine` 同时请求 ALBUM 和 SINGLE。两条路径进入网易分支后，都会独立调用 `getArtistMoreAlbums()`，分别把该艺人的同一份发行列表最多翻 10 页，最后才按 `type` 过滤成专辑或单曲。

影响：

- 每位网易艺人最多产生两套并发的 10 页请求。
- 放大 405 风控、后台网络唤醒和耗电。
- 延长快照写入到最终通知投递之间的时间，也会放大 CR-04 的进程终止漏发窗口。

建议新增一次请求返回 `(albums, singles)` 的仓库方法，NotifyWork 对每位网易艺人只做一次全量分页，再在内存中拆成两个不相交集合。艺人详情页若同时加载两组，也应复用同一份结果或 single-flight 缓存。

## 2026-09-26 补充：网易“下载到设备”修复审查

审查目标：主仓 `f96c7278`。该提交把网易下载改为 `prepareGet().execute { bodyAsChannel() }` 流式读取，并把位图编码、封面写入和音频下载整体移到 `Dispatchers.IO`；同时用 `songEntity.toTrack()` 兜底队列重建窗口内的 `track == null`。这三个方向均正确，能够分别处理大文件整包入内存、主线程文件 IO 和冷启恢复态点击无反应问题。

但成功判定和落盘事务性尚未收口，因此暂不应把该路径标记为“全线修复”。

### CR-19 非 2xx CDN 响应会被保存为音频并误报成功【已修 5004b42a】

- 位置：`composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/SharedViewModel.kt:2354`
- 成功落状态：同文件 `:2381`
- 依赖版本：Ktor `3.6.0`

新建的 `HttpClient(CIO)` 只安装了 `HttpTimeout`，没有设置 `expectSuccess = true`，`execute` 回调中也没有检查 `response.status`。Ktor 客户端默认不会因为非成功 HTTP 状态抛异常，因此网易 CDN 链接过期、区域拒绝或上游返回 4xx/5xx 时，当前代码会继续读取响应正文、写入 `.mp3`/`.flac`，退出回调后无条件设置 `DownloadProgress.AUDIO_DONE`。

影响：

- 用户看到“下载完成”，实际文件可能是 HTML/JSON/XML 错误正文，播放器无法打开。
- 错误响应若很小，进度会快速到达 100%，表象尤其像正常成功。
- 与 CR-20 叠加时，还会先删除同名的旧有效文件，再用错误正文替换。

建议：

1. 在任何目标文件删除或创建之前检查 `response.status.isSuccess()`；或在该专用 client 上显式设置 `expectSuccess = true`。
2. 若响应包含 `Content-Length`，结束读取后校验 `read == total`，不一致时按失败处理。
3. 可额外校验 Content-Type 是否与解析出的网易音频格式相符，避免 2xx 错误页被当成音频。
4. 增加 MockEngine 测试，至少覆盖 200 音频、403 错误正文、500、声明长度大于实际正文四种响应。

### CR-20 覆盖下载失败会丢失旧文件并留下半成品【已修 5004b42a】

- 音频位置：`composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/SharedViewModel.kt:2357`
- 封面位置：同文件 `:2300`

音频和封面都先删除正式目标，再直接用 `FileOutputStream` 向正式路径写入。用户重复下载同一首歌时，只要新请求在写入过程中超时、断网、被取消或磁盘空间不足，旧的完整文件已经不可恢复，正式路径上还可能残留一个可见但不完整的新文件。

建议采用 staged write：

1. 写入与最终文件同目录的唯一 `.part` 临时文件。
2. 完成 HTTP 状态、长度及必要格式校验。
3. flush/close 成功后再替换正式文件。
4. 任何异常只删除 `.part`，保留旧文件；取消协程时重新抛出 `CancellationException`。
5. 音频成功后再提交封面，或让音频与封面共享一次最终提交阶段，避免一新一旧。

### CR-21 文件名清理漏掉 `/`，合法歌曲信息可被解释为子目录【已修 5004b42a，非本提交新增】

- 位置：`composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/SharedViewModel.kt:2282`

当前正则为 `[|\\?*<\":>]`，过滤了 Windows 的反斜杠，却漏掉 Android/Linux 的路径分隔符 `/`。例如艺人名 `AC/DC` 会生成形如 `Download/<title>_-_AC/DC.jpg` 的路径；中间目录不存在时，封面写入直接失败，音频下载不会开始。

建议：

- 至少同时过滤 `/` 与 `\\`，并清理控制字符、尾部点号/空白。
- 对清理后空文件名和过长文件名提供稳定兜底，可追加 videoId 避免同名歌曲互相覆盖。
- 增加包含 `/`、`\\`、`:`、emoji、全空白和超长标题的纯函数单元测试。

### 本轮验证记录

- `git diff-tree --check f96c7278^ f96c7278`：通过。
- `f96c7278` 只修改 `SharedViewModel.kt`，未增加自动化测试。
- ~~尝试执行 `./gradlew :composeApp:compileAndroidMain --no-daemon`；当前工作区在与下载提交无关的未提交更新检查改动处失败~~（该障碍随检查更新改动提交 cfc3ebfa 消除；2026-09-26 复验 `:composeApp:compileAndroidMain` BUILD SUCCESSFUL）。
- Ktor 官方行为核对：默认不根据 HTTP 状态验证响应；需启用 `expectSuccess` 或手工检查状态码。参考：<https://ktor.io/docs/client-response-validation.html>。

### CR-19/20/21 修复验证记录（2026-09-26，主仓 5004b42a，模拟器 Universal_API36 实测）

- 正常回归：网易 flac（32.7MB）+jpg 落地，`fLaC` 魔数正确；重复下载 rename 覆盖成功、无 FUSE EEXIST。
- 实验 A（飞行模式全断网点导出）：取流失败弹"出错了 / netease stream unavailable"，旧文件不动。
- 实验 B（下载 54% 时飞行模式切断）：~30s 后弹"出错了 / incomplete download: 17693872/32709079"（Content-Length 核对生效，数字与断网时刻 .part 大字节数精确一致）；旧 flac md5 全程不变（a6d2cadd…），`.part` 无残留。
- 方法论更正：**模拟器上 `svc wifi disable` 不断网**（网络走虚拟 eth0），下载会继续完成——前两轮"断网实验"实为重复下载成功覆盖，恰好内容相同 md5 一致而误像"失败保留旧文件"；断网实验必须用 `cmd connectivity airplane-mode enable` 并先 ping 确认。

## 工程质量与验证结果

执行 `./gradlew androidApp:lintDebug --console=plain`：

- 当前结果为 **0 error、36 warning，BUILD SUCCESSFUL**。
- widget preview 的 `android:tint` 已通过有依据的 `tools:ignore` 保留平台 inflater 所需行为。
- adaptive icon 继续保留在 `mipmap-anydpi-v26`；对应一条 lint warning 属于已验证不能照改的打包例外。

执行 `git diff --check upstream/dev...HEAD` 发现：

- 原先 `PlaylistViewModel.kt` 的尾随空格和源文件 EOF 空行已经清理。
- 当前源文件与本文档均无 trailing whitespace，`git diff --check` 可通过。

这些不影响运行，但建议在合并前清理。

自动化测试覆盖仍明显不足：网易相关 JVM 文件多为联机 probe，无法稳定保护以下关键路径：

- Room 多版本迁移。
- FM 重试与分页终止。
- `Result<Boolean>` 业务失败语义。
- 通知快照和 outbox 一致性。
- LibraryMutation 并发收发。
- 搜索、歌词在快速切换场景下的 generation 隔离。
- 自动备份 null output stream 与 MediaStore 清理行为。
- Cookie replace/logout 与响应合并的并发顺序。
- 网易发行列表单次分页后拆分专辑/单曲。

## 推荐整改顺序

> 2026-09-25 定案后的实际状态。已修=本轮完成；不修=定案带原因（见文首定案表）；顺延=下一轮打包。

第一批，直接修复确定性逻辑错误：

- [x] CR-01 FM 三次请求。（core `25361c4`）
- [x] CR-03 红心假成功缓存。（core `6a28091`）
- [x] CR-09 重复非空游标停止。（基础循环 `a98f2af`；同值游标二轮收口 core `e0a7b36`）
- [x] CR-08 自动备份假成功与清理 URI。（主仓 `fd2b5a94`：null 流不再记成功+删除 URI 改回 Files 集合）
- [x] CR-19 网易下载校验 HTTP 状态与响应完整性。（主仓 `5004b42a`：status 检查+Content-Length 核对，飞行模式实测两分支）

第二批，处理数据一致性：

- [~] CR-02 Room 迁移路径。（不修：无历史包袱，受影响设备实际不存在）
- [~] CR-04 通知快照与投递原子性。（不修：接受小概率漏发）
- [x] CR-05 LibraryMutation 可靠消费。（小修：drainPending 按来源分侧消费，主仓 `8ff63997`；actor 重构不做）
- [~] CR-13 歌单同步部分失败。（不修：本地歌单已下线，死代码）

第三批，处理异步竞态：

- [x] CR-06 搜索串台。（749e8600:searchSongs/searchAll/loadMore 存 job 相互取消+复位 songsLoadingMore;取消足够无需 generation）
- [x] CR-07 歌词竞态。（749e8600:仅罗马音分支加 lyricsVideoId 校验,其余分支自愈不动）
- [x] CR-11 Cookie 全写路径串行化。（`dc7330f`+`e0a7b36`：mergeSetCookies 与 replaceCookies 的 saver 均在锁内）

第四批，做资源和性能收口：

- [x] CR-10 关闭扫码 client。（core 38ee9aa+主仓 749e8600）
- [~] CR-12 移除播放热路径 `runBlocking`。（不修：被 list.size>3 短路，仅小队列触发）
- [x] CR-14 优化通知差集。（749e8600:预计算集合+空快照按'行存在'判基线+写快照双侧成功门）
- [x] CR-18 网易发行只分页一次并拆分两组。（core `4862d64`：getArtistMoreAlbums 同艺人单飞+30s 短窗复用，按 type 拆分不变）
- [~] CR-15 真正懒加载网易仓库。（不修：毫秒级，有实测数据再动）
- [~] CR-16 拆分分类封面锁。（不修：刻意的 405 频控设计）
- [x] CR-17 位图内存压测。（B2 已修 11984ce1；B1 降采样不做；模拟器实测 150 切：Bitmap 计数 74→71 持平=LRU 有界非泄漏，关播放页 74→46 实证释放——2026-09-25 完成）
- [x] CR-20 网易下载改为临时文件写入、成功后替换，失败保留旧文件。（主仓 `5004b42a`：staged write，实测 54% 断网旧文件 md5 不变）
- [x] CR-21 下载文件名补齐 `/` 等路径字符与长度边界处理。（主仓 `5004b42a`：补 `/`+控制字符+尾部点+空名兜底 videoId+100 字符截断）

建议完成前两批后先跑一次回归；全部完成后再做真机长时间播放、快速切歌、后台通知、低内存和 Android 8/9 兼容测试。
