# 歌单内搜索白屏修复记录（2026-09-26）

> 症状：歌单详情页内搜索（头图上的搜索按钮）时，页面出现"下半部白屏"。
> 排查定案为**两个互相独立的失败模式**，均已修复。**两者均为上游（maxrave-dev/SimpMusic）自带的 bug**，
> fork（feat/netease-source 分支）只是继承，合并解决无偏差——两个修复都可直接回馈上游。
>
> | 模式 | 症状 | 归属 | 修复 |
> |---|---|---|---|
> | A：过滤态高度塌缩 | 搜索过滤后内容终点之下整片白（确定性 100%） | 上游 | 主仓 `31da652e`（`fillMaxSize()`） |
> | B：blur 层未就绪白带 | 搜索条正下方全宽纯白横带（偶发，可自愈） | 上游（haze 2.0 迁移） | 主仓 `1f088579`（垫不透明底色） |

---

## 模式 A：过滤态下半屏白屏（主修复）

### 复现步骤

1. 进入任意歌单详情页（如网易红心歌单）；
2. 点击头图右上角的搜索按钮，打开页内搜索条；
3. 输入一个**只匹配少量歌曲**的查询词（如"慕夏"），列表被过滤到 1-2 行；
4. 结果行/页脚（EndOfPage）之下直到底部导航栏之间**整片白屏**。

不输入查询（列表未过滤）不触发；过滤词命中很多行（内容超过一屏）按同一测量规则也不应触发
（内容高于视口时被钳制在视口高）——此条为机制推论，未单独实测。

### 根因

页面的深色沉浸底色（palette 派生 `mutedPaletteBg`）画在 **LazyColumn 自身**的 modifier 上，且只约束了宽度：

```kotlin
LazyColumn(
    modifier = Modifier
        .fillMaxWidth()          // ← 只有宽度约束
        .background(mutedPaletteBg)   // ← 页面底色挂在列表自身
        .hazeSource(hazeState),
    ...
)
```

搜索过滤使内容变短时，该 LazyColumn 在一次重测中被测成**内容高度**（而非视口高度），
`.background()` 的绘制区域随之缩水，内容终点之下的区域没有任何深色层覆盖，
透出浅色主题的窗口底色（#FAFAFA）＝"下半部白屏"。

**为什么 LazyColumn 会 wrap**：这是 Compose 的标准测量行为而非异常——约束链传到它时
minHeight=0（Crossfade 内部 Box 松化约束），LazyColumn 在"内容短于 maxHeight 且无最小高度
约束"时按内容高度上报；内容超出一屏时被钳在视口高（所以长列表看起来"正常铺满"）。
上游缺的就是一处强制最小高度，`fillMaxSize()`（min=max）正好补上。

**布局探针实锤**（onGloballyPositioned 打点）：

```
LazyColumn size=1080 x 2424 (query='', filtered=506)   ← 未过滤：满屏
LazyColumn size=1080 x 2424 (query='慕夏', filtered=1)  ← 过滤瞬间仍满屏
LazyColumn size=1080 x 1250 (query='慕夏', filtered=1)  ← ~360ms 后塌缩
```

塌缩值 1250px 与内容高度精确吻合：搜索条占位 spacer 345 + 结果行 ~130 + EndOfPage 280dp(=735px)。
（触发时序：开搜索 → `getFullTracks` 重拉全量 tracks 换新实例 → 重组合 → LazyColumn 重测为内容高度。）

### 二分排除项

- 移除搜索条覆盖层的 `hazeBlur` → 白区纹丝不动（排除 haze 层）；
- 收起输入法键盘 → 白区仍在（排除 IME inset）；
- 关闭搜索条（列表恢复全量）→ 白区消失（确认与过滤态绑定）。

### 修复

```kotlin
.fillMaxWidth()  →  .fillMaxSize()
```

钉满视口高度：短内容时底色仍然全屏。对长列表零行为差异（未过滤时本来就是这个高度）。
**同病推平（8728d3dc）**：AlbumScreen 与 LocalPlaylistScreen 的 LazyColumn 是同款
`fillMaxWidth().background(mutedPaletteBg)` 病根，已一并修为 `fillMaxSize`。专辑页可在短专辑
“头图+行+页脚不足一屏”时触发；LocalPlaylistScreen 的搜索态本身已有全屏且带
`mutedPaletteBg` 的 sibling `Column` 覆盖，所以这笔推平对它主要防的是**短本地歌单普通态**漏底，
不是其搜索白屏的必要修复。

### 验证

- 过滤态：探针全程 2424，白区消失（仅剩浅色底部导航栏，属正常浅色主题 chrome）；
- 未过滤列表、关闭搜索条后恢复均正常；
- 真机待验（同路径走一遍）。

---

## 模式 B：搜索条 blur 白带

### 复现步骤

1. 进入歌单详情页，点头图搜索按钮打开搜索条（**无需输入查询**）；
2. 观察搜索条正下方：失败时出现全宽纯白横带（实测 y~355-475，约 120px 高）；
3. 特征：**纯 #FFFFFF**（区别于浅色主题正常底 #FAFAFA）、钉在视口不随列表滚动、完全静止无 shimmer；
4. 滚动一下列表即自愈（haze source 重绘）。

**提高触发率**：搜索条打开 + 点输入框把键盘弹出（IME inset 变化逼 blur 层重新布局）；冷启动后首次打开；覆盖安装后首次使用。

### 根因

上游 2.2.0 升级 haze 2.0 时，把歌单页搜索覆盖层的 `hazeEffect` 迁移为 `hazeBlur`：

```kotlin
Box(Modifier
    .fillMaxWidth()
    .onGloballyPositioned { searchBarHeightPx = it.size.height }
    .hazeBlur(HazeInput.Sources(hazeState), barBlurStyle(mutedPaletteBg, 0.55f))  // ← 上游迁移
)
```

haze 2.0 的 blur 层在覆盖层刚出现（AnimatedVisibility 进入/IME inset 变化）时尚未就绪，
会把该区域渲染成未初始化的纯白。属上游 haze 2.0 迁移自带的回归（见下方归属考证）。

### 修复

在 `hazeBlur` 之前垫一层不透明 palette 底色：

```kotlin
.background(mutedPaletteBg)                                    // 兜底：blur 失灵最多退化成实色条
.hazeBlur(HazeInput.Sources(hazeState), barBlurStyle(mutedPaletteBg, 0.55f))
```

blur 正常时视觉几乎无差（底色与模糊 tint 同源）；blur 失灵时不再漏白。

### 验证

开关搜索条数轮 + 冷启动后首开 + 键盘弹出/收起组合路径，搜索条区域无白带。
已知残余：本修复保住的是 Box 本体区域，blur 层向 Box 之下的偶发延伸（IME 打开时见过一次）
在叠加模式 A 修复后的全量测试中未再出现；同页另两处 hazeBlur（常态顶栏/多选栏）未加兜底，
再报白可同法修。

---

## CR 影响评估与后续建议

### 修复 A：`fillMaxSize`（31da652e + 8728d3dc）

**结论：收益明确，回归风险很低，建议保留。**

- **未过滤/长列表零差异**：原本已按满屏高度测量；LazyColumn 仍在固定视口内虚拟化和滚动，
  不会因为 `fillMaxSize` 限制内部内容高度，滚动、分页、顶栏隐藏逻辑不变。
- **短内容触摸区域扩大**：空白处现在落在 LazyColumn 上。PlaylistScreen/AlbumScreen 下层只有背景，
  无实际交互影响。LocalPlaylistScreen 是小例外：列表整体挂有重排 `pointerInput`；仅在显式进入
  “Change order”模式后，长按新增空白区会启动手势识别，但 `onDragStart` 找不到对应 item，
  因而不会换位、写库或自动滚动，最多消费这一次无效长按拖动，可接受。
- **性能边界**：短页面的背景和 `hazeSource` 绘制区域会扩大到一个完整视口，但仍是有界单屏；
  长列表此前已是相同大小，没有增加新的最坏开销。
- **理论边界**：将来若出现依赖“列表高度=内容高度”的父布局或定位逻辑，需要重新检查；当前三个页面
  均不存在此依赖。

### 修复 B：搜索条材质兜底（1f088579）

**结论：功能与性能风险低；是否保持完全不透明底色，取决于真机视觉验收。**

#### 当前方案的影响

- `.background(mutedPaletteBg)` 只新增一个搜索条大小的纯色绘制，不新增状态订阅、协程或 blur layer，
  性能成本可忽略。
- 正常 blur 就绪时，`barBlurStyle` 本身也以同一个 `mutedPaletteBg` 作为 `backgroundColor`，
  所以真实视觉差异预计小于“纯玻璃直接变实底”；明显差异主要出现在 Haze 输出透明或未初始化的阶段。
- palette 未生成时，`toImmersiveBackground()` 回退黑色；搜索条可能在亚秒级内显示深色兜底，
  但页面主体同期也使用相同背景色，不会形成异色白块。
- 当前已知残余仍成立：Box 内的背景不能保证覆盖 blur 向边界外的偶发延伸；同页常态顶栏和多选栏
  两处 `hazeBlur` 尚未加兜底。没有复现证据前不应机械铺满，以免无必要地加重所有玻璃层。

#### 业内做法

成熟的玻璃材质通常按以下层次组成，而不是只依赖 blur：

```text
材质底色 / scrim
        ↓
背景模糊
        ↓
半透明 tint / 对比度修正
        ↓
文字与控件
```

Blur 是视觉增强项，不是唯一背景；模糊不可用、尚未准备好或用户要求减少透明度时，应退化成稳定的
材质色，而不是透出窗口底色。当前“底板 + blur”的方向符合这一做法，问题只在于完全不透明底板可能
略重。参考：[Apple Materials](https://developer.apple.com/design/human-interface-guidelines/materials)、
[Haze 2.0 Blur usage](https://chrisbanes.github.io/haze/2.0.0/blur/usage/)。

#### 替代/精修方案优先级

1. **生产优先：保留当前不透明底板。** 最稳，保证首帧失败也不会漏白；先以真机观感决定是否需要
   继续调轻。
2. **视觉精修：高不透明度材质底板。** 若真机明显偏实，可依次对比
   `mutedPaletteBg.copy(alpha = 0.85f / 0.90f / 0.95f)`；建议从 `0.88f~0.92f` 区间起测。
   透明度越低越通透，但首帧兜底能力也越弱；若任一轮仍出现白闪，恢复 `alpha = 1f`。
3. **约束越界：`clipToBounds()` / `expandLayerBounds = false`。** 只用于处理 blur 向搜索条下方延伸，
   不能修复 Box 内部的首帧未就绪。关闭 layer expansion 会改变边缘采样，必须检查搜索条下缘是否出现
   硬切；Haze 官方也建议以实际视觉和性能测试决定是否关闭。
4. **语义 fallback：`fallbackColorEffect(...)`。** 可补作平台不支持/禁用 blur 时的正式 scrim；
   但本次白带很可能发生在正常 blur renderer 已选中、只是首帧尚无有效输入的阶段，fallback 未必会触发，
   因而不能未经实测就替代外层背景。
5. **不采用：显式 `KeepLastFrame`。** `HazeInput.Sources` 默认已是该策略；新建的搜索条 effect node
   首次挂载时没有上一帧可保留，重复声明不会解决首次白闪。
6. **不推荐：常驻隐藏 blur 节点预热。** 它能避免每次搜索时冷挂载，但隐藏期间仍可能维护 source/effect，
   页面滚动又会持续改变输入；对低频临时搜索条不值得长期支付渲染和交互复杂度。
7. **根治方向：反馈 Haze 上游。** 最理想的行为是新 effect node 未拿到有效 source 时先画
   `fallbackColorEffect`/材质 scrim，首个有效输入到达后无缝切 blur，而不是输出白色中间帧。
   应用层没有公开的 blur-ready 信号，“延迟一帧显示”只是时序猜测，不应作为正式修复。

#### 真机验收矩阵

1. 冷启动、palette 尚未生成时首次打开搜索；
2. palette 已生成后反复开关搜索，比较玻璃通透度；
3. 输入法弹出/收起，检查搜索条本体和下缘；
4. 列表滚动期间打开/关闭搜索，检查白带、硬边和掉帧；
5. 至少覆盖一台 Android 12+（RenderEffect）和项目仍支持的较老设备 fallback 路径。

决策口径：若真机只表现为“略实一点”，保留当前方案，用小视觉代价换掉偶发纯白故障；若玻璃质感
明显受损，再尝试高不透明度底板和边界裁剪。上游修复首帧初始化后，才移除应用层 workaround。

---

## 归属考证：为什么说两个都是上游的

1. **歌单内搜索功能本身是上游的**：commit `0327267d` "feat: search inside playlist"（maxrave-dev，2025-11-16，在 origin/dev 上）。
2. **模式 A 的病根写法是上游的**：`upstream/dev` 的 PlaylistScreen（366-371 行）就是
   `.fillMaxWidth().background(mutedPaletteBg).hazeSource(...)`，与 fork 合并前逐字同构。
3. **模式 B 的迁移是上游做的**：hazeEffect→hazeBlur 系上游 2.2.0（`0e8bf2fb`）自做；
   已比对 `upstream/dev` 现行代码，三处 `hazeBlur(HazeInput.Sources(...), barBlurStyle(...))`
   与 fork 逐字一致，`barBlurStyle` 亦上游自有（extension/UIExt.kt）。
4. fork 的 2026-09-25 上游合并（`675d89d2`）在这几行上没有引入偏差（冲突解决与上游一致）。

结论：上游在浅色主题下同样必现这两个问题；两个修复（各一行）可直接回馈上游。

---

## 通用认知（防再犯）

- **"白"要分两种**：纯 `#FFFFFF` = 渲染层失灵（bug）；`#FAFAFA` = 浅色主题正常底色。
- **浅色主题下的结构性陷阱**：app 跟随系统，深色页面靠 ForceDarkContent/palette 沉浸实现——
  任何"高度跟随内容"的底色层（画在 LazyColumn/自身 wrap 的容器上）都会在短内容时露出浅色窗口底。
  给列表页加底色时，容器必须钉满视口（fillMaxSize 或等价约束）。
- **排查工具箱**（本次用的）：逐行像素带扫描（PIL）定位硬切边界与颜色纯度；
  `onGloballyPositioned` 布局探针拿容器实测尺寸；二分构建（移除嫌疑层看症状是否消失）；
  ADBKeyboard 广播输入中文（`adb shell am broadcast -a ADB_INPUT_TEXT --es msg "慕夏"`，
  需 `ime set com.android.adbkeyboard/.AdbIME`；Compose 输入框对 `input text`/逐键 keyevent 均不可靠）。

## 相关提交

- `1f088579` fix(playlist): 搜索条 hazeBlur 白带防御（模式 B）
- `31da652e` fix(playlist): 过滤态下半屏白屏——fillMaxSize 钉满视口高（模式 A，含完整二分证据）
- `8728d3dc` fix(playlist): 同病推平——AlbumScreen/LocalPlaylistScreen 一并 fillMaxSize
