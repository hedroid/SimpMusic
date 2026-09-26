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

不输入查询（列表未过滤）不触发；过滤词命中很多行（内容超过一屏）也不触发。

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
