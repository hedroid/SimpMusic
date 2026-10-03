# Apple Music 播放页性能优化 Code Review（2026-10-03）

> 审查基线：`af7c32b4..5dce3211`，当前主仓 HEAD `5dce3211`；本文后半部分同时复核 HEAD 之上的未提交修复。
>
> 审查提交：`c0cf49bb`（第一批 GPU/合成减负）、`505873c3`（timeline 拆分、歌词扫光限帧、marquee 收敛）、`5dce3211`（歌词 blur 层数收敛）。
>
> 审查方式：逐项检查提交 diff、当前调用链及 Android/JVM 对称实现；执行 `git diff --check`、`:composeApp:compileAndroidMain`、`:composeApp:compileKotlinJvm`、`:androidApp:installDebug`，并在 API 36 模拟器做三键坐标、图标和页面切换录屏验收。
>
> 工作区边界：`NowPlayingScreen.kt` 与 `NowPlayingContentAppleMusic.kt` 是本轮针对 CR-01/02 的未提交修复；`AppleMusicShared.kt` 是 Apple 播放三键居中修复；`Comment.kt` 是 Classic、M3 Expressive、Apple 三主题共用的评论图标统一。四项均已纳入本次复核，但尚未提交。

## 结论

第一批优化有明确收益，应保留：提交记录中的模拟器 A/B 显示 MAIN 静态播放 janky 从 `14.66%` 降至 `0.19%`，源码变化也与结果吻合——Android 不再做无消费者的全屏 backdrop 捕获、MAIN 不再绘制被 mesh 遮住的 80dp 模糊封面、mesh 移除实时 44dp 全屏 blur。

第二批原提交方向正确，但在 HEAD 上仍残留一个无条件无限彩虹动画，会以 60/120Hz 读取动画状态。当前未提交修复已把它收敛到 `isCrossfading == true` 的分支：普通播放不创建、不读取 infinite transition，退出 Crossfade 只做一次 300ms 回白过渡。代码和双端编译已通过，仍需 120Hz 真机用重组计数/Perfetto确认根作用域不再随 vsync 唤醒。

另有一项切页风险：为了让 MAIN 不再承担 80dp 模糊，HEAD 会在进入歌词/队列页的 300ms Crossfade 内重新创建 `AsyncImage + blur`。当前未提交修复改成优先复用 MAIN pager 已解码并写入 `screenData.bitmap` 的封面，仅在位图尚未就绪或停留在歌词/队列页切歌时走 URL 兜底。模拟器录屏覆盖 `LYRICS -> MAIN -> QUEUE`，未见黑帧或封面占位闪回；冷缓存和内存回收场景仍待真机压测。

**审查决定：代码复核通过，真机性能验收待完成。** 原 Block 项均已有针对性修复，双端编译、debug 安装和模拟器可见回归通过；在没有 90/120Hz 真机重组计数、gfxinfo/Perfetto 数据前，不把“卡顿已完全消失”写成已证实结论。

## 状态总览

| 编号 | 优先级 | 状态 | 结论 |
|---|---:|---|---|
| AM-PERF-CR-01 | P1 | **代码已修复，待真机计数** | infinite transition 只在 Crossfade 期间存在，普通播放不再读取逐帧 hue |
| AM-PERF-CR-02 | P2 | **已修复，模拟器通过** | 歌词/队列背景优先复用已解码 bitmap，URL 仅作无位图/切歌兜底；切页录屏无黑帧 |
| AM-PERF-CR-03 | P1 | **已修复并有 A/B** | Android 无消费者的 `layerBackdrop` 全屏捕获已移除 |
| AM-PERF-CR-04 | P1 | **已修复并有 A/B** | MAIN 被遮挡的 80dp 模糊封面不再常驻绘制 |
| AM-PERF-CR-05 | P1 | **已修复并有 A/B** | mesh 从实时 44dp RenderEffect blur 改为 64x64 预平滑纹理直接拉伸 |
| AM-PERF-CR-06 | P2 | **已修复** | Apple pager 改用 `artworkPageKeys`，队列重排不再按下标重建页面 |
| AM-PERF-CR-07 | P2 | **已修复，边界待真机** | 富同步歌词扫光最多约 30fps，timeline 在 effect 内收集，不再重组歌词条 |
| AM-PERF-CR-08 | P2 | **已修复** | Apple 歌词 blur 从所有可见非当前行收敛到最多两个邻行 |
| AM-UI-CR-01 | P2 | **已修复，模拟器通过** | Apple 三枚播放控制在全宽 Row 内居中，左右间距实测相等 |
| PLAYER-UI-CR-01 | P3 | **已修复，模拟器通过** | 改用 Material Symbols Rounded `forum` 双气泡，Classic/M3/Apple 共用且与旧单气泡明显不同 |
| PLAYER-UI-CR-02 | P3 | **已修复，用户验收通过** | Classic 评论入口由底部操作行中间移到收藏按钮右侧；播客无收藏时仍保留行尾评论入口 |

## Findings 对照表

| Before | After | Why |
|---|---|---|
| `NowPlayingScreen.kt:519-530` 无条件创建并读取 `rainbowHue` | 只在 `isCrossfading == true` 时组合彩虹动画；非 crossfade 使用不读动画状态的静态白色 | timeline 的 20Hz 根刷新虽已删除，但彩虹动画仍以 60/120Hz 让根作用域失效，抵消状态拆分收益 |
| `NowPlayingContentAppleMusic.kt:221-233` 用 `if (showBlurredBackdrop)` 创建/销毁完整背景 | 保留已解码封面状态，只条件启停昂贵的模糊绘制；优先复用 `screenData.bitmap`，URL 加载仅兜底 | MAIN 到歌词/队列切换不应在 300ms Crossfade 期间重新解码并创建全屏 80dp blur |

## AM-PERF-CR-01 无条件彩虹动画仍按屏幕刷新率驱动根组合【P1·代码已修复】

位置：

- `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/screen/player/NowPlayingScreen.kt:519`
- 同文件 `:652`（`NowPlayingContentState` 构造）
- 同文件 `:1018`（三主题分发根）

HEAD 代码先无条件创建 `rememberInfiniteTransition`，再在组合阶段读取 `rainbowHue`：

```kotlin
val infiniteTransition = rememberInfiniteTransition(label = "crossfadeRainbow")
val rainbowHue by infiniteTransition.animateFloat(/* 0f -> 360f, infinite */)
val rainbowColor = hsvToColor(rainbowHue, 1f, 1f)

val sliderTrackColor by animateColorAsState(
    targetValue = if (isCrossfading) rainbowColor else Color.White,
)
```

`isCrossfading == false` 只会让最终 target 选择白色，不会阻止 `rainbowHue` 每帧变化，也不会阻止读取该状态的 restart scope 持续失效。该作用域后面仍构造 `NowPlayingContentState`、`NowPlayingContentActions` 并进入主题分发，所以第二批原提交没有真正获得“静止播放时根树无高频组合”的状态。

当前未提交修复：

1. `isCrossfading == false` 时 `crossfadeTargetColor` 直接为 `Color.White`，分支内不存在 infinite transition，也不读取动画状态。
2. `isCrossfading == true` 时才创建 hue 循环；`animateColorAsState(tween(300))` 只负责进入/退出时的颜色衔接。
3. Android/JVM 双端编译通过。剩余验证边界是 120Hz 真机的 Compose recomposition count 或 Perfetto：应确认普通播放时 `NowPlayingScreenContent` 不再随 vsync 重组，仅进度条/时间行保留 20Hz 状态消费。

## AM-PERF-CR-02 切到歌词/队列时重建重型模糊背景【P2·已修复，模拟器通过】

位置：

- `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/screen/player/content/NowPlayingContentAppleMusic.kt:221`
- 同文件 `:232`
- 同文件 `:834`（`AsyncImage`）
- 同文件 `:852`（80dp blur modifier，当前行号以 HEAD 为准）

第一批通过以下门控正确消除了 MAIN 下方不可见的模糊封面：

```kotlin
val showBlurredBackdrop = viewState != AppleMusicView.MAIN || videoBackdropOnMain

if (showBlurredBackdrop) {
    AppleMusicArtworkBackdrop(...)
}
```

但这个 `if` 会让 `AppleMusicArtworkBackdrop` 在 MAIN 完整离开组合：内部 `backdropUrl`、`AsyncImage` painter 和 80dp blur 节点均被销毁。用户切到歌词或队列时，它们与 300ms 页面 Crossfade 同时重建。内存缓存命中时风险较低；缓存未命中、图片尺寸缓存键不一致或系统回收缓存后，首帧只能看到外层黑底，随后才加载模糊封面。

当前未提交修复没有恢复 MAIN 常驻全屏 blur，而是拆开了“封面数据存活”和“昂贵绘制存活”：

1. MAIN pager 已通过 `actions.onArtworkBitmap` 写入 `screenData.bitmap`，歌词/队列背景优先用 Compose `Image` 直接复用该位图。
2. 保留 `thumbnailURL` 的 `AsyncImage` 作为位图尚未就绪、或停留在歌词/队列页切歌时的兜底；新曲的 `NowPlayingScreenData` 默认位图为 null，不会误用上一首封面。
3. 仍只在 `showBlurredBackdrop` 时创建全屏 80dp blur 绘制节点，MAIN 静态收益没有被回退。
4. API 36 模拟器录屏覆盖 `LYRICS -> MAIN -> QUEUE`，300ms 切换过程没有出现全黑帧、灰占位或封面重新淡入。尚未覆盖系统主动回收 bitmap 后的冷加载和真机快速连点压力场景。

## 已确认可保留的优化

### 1. Android backdrop 捕获平台门控

`panelBackdrop` 与 `layerBackdrop` 现在只在 Desktop 创建。Android 只有普通 grabber，没有任何玻璃按钮消费该背景，移除捕获不改变视觉和交互，属于纯收益。

### 2. MAIN 移除被 mesh 遮挡的 80dp 模糊封面

静态 MAIN 的 mesh 是不透明全屏底板，原模糊封面在其后不可见。取消该层后 A/B 从 `14.66%` janky 降至 `0.19%`，是本批最明确的收益。AM-PERF-CR-02 要求的是保留数据热态，不是把该 blur 恢复成 MAIN 常驻绘制。

### 3. mesh 取消全屏实时 blur

mesh 已先从 6x6 控制网格通过 smoothstep 重采样到 64x64，再由 GPU 双线性拉伸。提交记录的像素检查显示行间最大色差 `1/255`，没有格子或接缝；移除 `Modifier.blur(44.dp)` 不破坏基本视觉口径。

mesh 切歌交叉淡化由 900ms 缩短至 300ms，也减少了两张全屏纹理并存时间，且仍保留颜色切换的连续性。

### 4. pager 稳定 key

Apple pager 现在与 Spotify/M3 共用 `state.artworkPageKeys`。物理洗牌和队列重排后，相同歌曲页面可以按 `videoId + 出现序号` 复用，不再因 index 变化销毁封面、视频或图片请求。

### 5. timeline 高频状态下沉

`NowPlayingContentState` 已删除 `timelineState` 快照和 `sliderValue`；进度流下沉到三主题的进度条、时间行、折叠工具条和字幕 actual wrapper。歌词行索引在 `LaunchedEffect` 内 collect，只在行号实际变化时更新 Compose state。结构方向正确，待 AM-PERF-CR-01 修复后才能完整释放收益。

### 6. 富同步歌词扫光限制到约 30fps

`AppleMusicLyricStrip` 在 effect 内直接收 timeline，frame clock 仍负责插值，但仅当距上次写入至少 33ms 时更新 playhead。状态由 `drawWithContent` 读取，不会让整条歌词进入每帧重组。暂停、非富同步或离开 MAIN 后不会保留高刷新率绘制循环。

### 7. 歌词 blur 最多保留两个邻行

原实现对所有可见非当前行分别创建 RenderEffect blur，并在换行时同时动画。现在仅 `distanceFromCurrent == +/-1` 的两行保留一档轻模糊，更远歌词只做 alpha 衰减；当前行锐利、滚动中禁 blur 的既有语义不变。

## 同轮 UI 一致性复核

### AM-UI-CR-01 Apple 播放三键居中【P2·已修复】

`AppleMusicTransportRow` 的内部 `Row` 原来是 wrap-content；`Arrangement.spacedBy(..., Alignment.CenterHorizontally)` 只能排列子项，不能把整个三键组放到父容器中心。未提交修复给该 Row 增加 `Modifier.fillMaxWidth()`，不改变按钮尺寸和点击区。

API 36、1080px 宽模拟器的语义坐标：上一首中心约 `345px`，播放中心约 `540px`，下一首中心约 `735px`；左右中心距均为 `195px`，播放键与屏幕中心误差约 `0.5px`。该项代码和可见布局均通过。

### PLAYER-UI-CR-01 三主题评论按钮统一【P3·已修复，模拟器通过】

第一版 WIP 尝试把共享 `SimpIcons.Comment` 从经典 Material `chat_bubble_outline` 换成 Material Symbols Rounded `chat_bubble` 默认轮廓，但两者在 24dp 下近乎同形，模拟器肉眼不可分辨，因此判定修改无效。重新定稿改用 Material Symbols Rounded `forum` 24px 双气泡：前后两层气泡直接表达公开评论和回复串，与旧单气泡有结构性差异；官方 960 网格按 1:40 精确转换为 Compose 24x24 `ImageVector`。

调用点逐一确认：

- Classic：`NowPlayingContentSpotify.kt`。
- M3 Expressive：`NowPlayingContentM3Expressive.kt`。
- Apple：`AppleMusicShared.kt`。

三处均引用同一个 `SimpIcons.Comment`，没有主题私有副本，因此一次替换覆盖 Classic、M3 Expressive、Apple。重新定稿仍保留原有按钮尺寸、点击区、显示门控和评论 sheet 行为，只替换共享图形。API 36 模拟器的 Apple 播放页确认双气泡轮廓完整、24dp 下可清楚辨认且与旧单气泡差异明显；Classic/M3 由共享调用链与 Android 编译确认。

### PLAYER-UI-CR-02 Classic 评论按钮移到收藏右侧【P3·已修复，用户验收通过】

Classic 原先把网易评论入口单独放在播放控制下方操作行的中间槽，与收藏按钮分离。现已从该操作行移除，接入共用的 `NowPlayingTrackInfoRow` 歌曲互动区：普通网易歌曲按“收藏、评论”从左到右排列，评论按钮位于收藏按钮右侧；非网易歌曲不显示评论按钮。

播客节目没有收藏语义，但原实现支持播客评论。移动时保留了这条能力：网易播客只显示评论按钮并占据信息行尾，不因缺少收藏按钮而丢失入口。图标、评论 sheet 回调、网易来源门控均复用原实现；同时把硬编码的 `Comments` content description 改为现有本地化 `comments` 字符串。Android 编译与安装通过，用户完成可见验收并确认布局可接受。

## 验证结果与边界

已验证：

- `git diff --check`：通过。
- `./gradlew :composeApp:compileAndroidMain :composeApp:compileKotlinJvm --no-daemon`：`BUILD SUCCESSFUL`。
- `./gradlew :androidApp:installDebug --no-daemon`：`BUILD SUCCESSFUL`，debug APK 成功安装到 API 36 模拟器。
- Android/JVM 的 `MediaPlayerViewWithSubtitle` expect/actual 已同步改为接收 `StateFlow<TimeLine>`。
- 提交记录包含60Hz模拟器 MAIN静态、封面滑动、歌词页静态 A/B，以及 seek、时间行、歌词推进的人工实测。
- Apple 三枚播放键中心坐标为约 `345/540/735px`，左右间距均为 `195px`。
- Classic/M3/Apple 三处评论按钮均指向同一 `forum` 双气泡向量；Apple 播放页模拟器可见验收通过。
- 切页录屏覆盖 `LYRICS -> MAIN -> QUEUE`，未观察到全黑帧、灰占位或封面重新淡入。
- Apple MAIN 暂停静置 5 秒，`dumpsys gfxinfo ... reset` 后统计 `Total frames rendered: 0`；这说明当前模拟器可见窗口没有空转绘制，但不能替代 Compose 重组计数和 120Hz 真机数据。

尚未覆盖：

- 120Hz真机普通播放时的根组合次数、UI线程和RenderThread占用。
- Crossfade开启/结束时彩虹颜色的实际启停和退出过渡观感。
- MAIN、LYRICS、QUEUE冷缓存、系统回收 bitmap 后及连续快速点击的压力场景。
- Apple动态封面场景下视频Surface、mesh遮罩和顶部blur的叠加成本。
- 30fps逐字扫光在90/120Hz屏幕以及快速说唱歌词上的观感。
- Classic 与 M3 Expressive 评论图标未逐主题截图；共享向量调用链与编译已确认。
- 本轮没有新增自动化性能基准；现有结论依赖源码审查、编译、模拟器录屏和提交记录中的 gfxinfo 数据。

## 复核通过条件

1. 120Hz 真机确认无 Crossfade 时播放器根组合不随 vsync 持续执行；代码条件门控已满足。
2. 真机重复切换 MAIN/LYRICS/QUEUE 时没有黑底闪帧，切页 janky 不显著高于当前页面内静态播放；模拟器可见回归已通过。
3. 120Hz真机上普通静态播放、封面滑动、富同步歌词、动态封面四组分别采集 gfxinfo/Perfetto；报告主线程、RenderThread和janky，而不是只报平均帧率。
4. seek、进度时间、暂停恢复、歌词行推进、随机播放重排和Cast/DLNA输出入口无功能回归。
5. 修复提交继续通过Android/JVM双端编译与`git diff --check`。
