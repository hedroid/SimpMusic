# SimpMusic 上游差异代码审查

审查日期：2026-09-25

审查基线：主仓 `ea3bee56`，core `8719cf6`

补充审查：2026-09-26，网易“下载到设备”修复提交 `f96c7278`

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
| CR-06 搜索串台 | **顺延下一轮** | 竞态属实但本单夸大：searchSongs 仅显式提交触发（输入过程只走 suggest），需快速连续两次提交才可能复现。修法=存 job 取消+generation 校验 |
| CR-07 歌词竞态 | **顺延下一轮** | 主歌词/空结果分支有 `lyricsVideoId != song.videoId` 自愈（错写会触发重拉）；真问题仅罗马音分支无校验写入且不触发重拉。局部加 videoId 校验即可，不必做统一 lyricsJob 大改 |
| CR-10 QR client 泄漏 | **顺延下一轮** | 属实（每 HttpClient 独立 OkHttp 引擎，onCleared 只取消轮询），但仅反复进出登录页才积累，Closeable+close 很便宜 |
| CR-14 通知差集 | **顺延下一轮** | O(n²) 绝对量小（每艺人几十项）；真实问题是"空快照=从未有发行的艺人第一张专辑漏通知"，随循环外预计算一并重构 |
| CR-18 网易发行扫描重复分页 | **已修（core 4862d64）** | NotifyWork 的 ALBUM/SINGLE 两条 flow 各自调用同一全量分页端点，每位艺人最多并发两套×10 页请求。修法=`getArtistMoreAlbums` 同艺人**单飞+30s 短窗复用**（先到者锁内翻页，后来者直接拿同一份再按 type 拆分；失败不缓存；跨 12h 扫描轮次必然重拉）——不动 NotifyWork 的双源通用结构，全量分页每艺人只跑一遍，风控暴露减半。 |
| CR-19 网易下载未校验 HTTP 状态 | **已修（主仓 5004b42a）** | execute 回调入口 `response.status.isSuccess()` 检查（audioUrl 有 10 分钟有效期，过期/区域拒绝的 4xx/5xx 错误正文此前会被存成 mp3/flac 还报完成）；读完核对 Content-Length，没读满抛 `incomplete download: read/total`。模拟器飞行模式实测两分支均见错误弹窗 |
| CR-20 网易重复下载会先破坏旧文件 | **已修（主仓 5004b42a）** | 封面/音频改 staged write：先写同目录 `.part`，写满 close 后 rename 提交——同目录 rename 对已存在目标是原子覆盖，替代"先删旧文件再直写"，顺带绕开 FUSE 新建同名 EEXIST；失败/取消 finally 清 `.part`，catch 重抛 CancellationException。实测：下载 54% 断网，旧 flac md5 全程不变、无 `.part` 残留 |
| CR-21 下载文件名漏过滤 `/` | **已修（主仓 5004b42a）** | 清理正则补 `/` 与 `\x00-\x1f` 控制字符、尾部点号修剪、清空兜底 `download_<videoId>`、截 100 字符防超长。`AC/DC`→`ACDC` 已验证（正则断言） |

另：`PlaylistViewModel.kt` 4 处尾随空格与 3 处文件尾空行已清理（`8232f405`）。

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

### CR-06 搜索分页会串入旧查询结果【顺延下一轮】

- 位置：`composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel/SearchViewModel.kt:225`

新的 `searchSongs` 不会取消旧搜索，也不会取消旧的加载更多任务。快速输入 A 后再搜 B 时：

- A 可能晚于 B 返回并覆盖 B。
- A 的下一页可能追加到 B 的结果中。
- 全局 `lastQuery` 与旧 continuation 可能不属于同一个查询。

建议：保存 `searchJob` 和 `loadMoreJob`；查询词、来源或搜索类型改变时全部取消。每次搜索生成 generation/token，所有响应写状态前校验仍属于当前查询。

### CR-07 网易歌词存在跨歌曲写入竞争【顺延下一轮：仅罗马音分支需修】

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

### CR-10 扫码登录专用 HttpClient 未关闭【顺延下一轮】

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

### CR-14 通知差集计算存在 O(n²) 分配【顺延下一轮】

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

- [ ] CR-06 搜索 generation。（顺延下一轮；复核：仅显式提交触发，非逐键）
- [ ] CR-07 歌词 generation。（顺延下一轮；复核：主分支有 lyricsVideoId 自愈，仅罗马音分支需校验）
- [x] CR-11 Cookie 全写路径串行化。（`dc7330f`+`e0a7b36`：mergeSetCookies 与 replaceCookies 的 saver 均在锁内）

第四批，做资源和性能收口：

- [ ] CR-10 关闭扫码 client。（顺延下一轮）
- [~] CR-12 移除播放热路径 `runBlocking`。（不修：被 list.size>3 短路，仅小队列触发）
- [ ] CR-14 优化通知差集。（顺延下一轮）
- [x] CR-18 网易发行只分页一次并拆分两组。（core `4862d64`：getArtistMoreAlbums 同艺人单飞+30s 短窗复用，按 type 拆分不变）
- [~] CR-15 真正懒加载网易仓库。（不修：毫秒级，有实测数据再动）
- [~] CR-16 拆分分类封面锁。（不修：刻意的 405 频控设计）
- [x] CR-17 位图内存压测。（B2 已修 11984ce1；B1 降采样不做；模拟器实测 150 切：Bitmap 计数 74→71 持平=LRU 有界非泄漏，关播放页 74→46 实证释放——2026-09-25 完成）
- [x] CR-20 网易下载改为临时文件写入、成功后替换，失败保留旧文件。（主仓 `5004b42a`：staged write，实测 54% 断网旧文件 md5 不变）
- [x] CR-21 下载文件名补齐 `/` 等路径字符与长度边界处理。（主仓 `5004b42a`：补 `/`+控制字符+尾部点+空名兜底 videoId+100 字符截断）

建议完成前两批后先跑一次回归；全部完成后再做真机长时间播放、快速切歌、后台通知、低内存和 Android 8/9 兼容测试。
