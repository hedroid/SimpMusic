# 隐藏 / 下线功能清单（fork 相对上游的 UI 收缩）

> 盘点日期：2026-09-20。本 fork 在网易源适配过程中，出于"不需要 / 未完成 / 语义已变"的原因，
> 隐藏或下线了若干上游功能入口。**区分三种处置**：
>
> - **隐藏（管线保留）**：UI 入口用常量门控或条件注释关掉，DataStore 键、ViewModel 状态、
>   底层管线全部保留编译，改一个开关/删一段注释即可恢复；
> - **下线（路由保留）**：入口从导航面移除，但 enum/独立路由仍在，深度链接或代码可直达；
> - **删除**：组件/表/字符串已物理删除，恢复需回溯 git 历史。
>
> 改动相关代码前先查本表，避免"修一个以为还在的功能"。恢复任何一项时同步更新本文档。

## 一、设置页隐藏项（管线保留）

| 功能 | 隐藏方式 | 位置 | 时间/提交 | 恢复方法 |
| --- | --- | --- | --- | --- |
| Discord 集成整个设置区（登录行 + Rich Presence 开关） | `SHOW_DISCORD_SETTINGS = false` | `SettingScreen.kt`（常量区） | 2026-09-20, d00110e8 | 常量改 `true`。登录路由/VM 状态/RPC sender/kizzy 模块全保留 |
| "备份已下载数据"开关（下载缓存并入备份 zip） | `SHOW_BACKUP_DOWNLOADED_SETTINGS = false` | `SettingScreen.kt`（常量区，紧随 Discord 门控） | 2026-09-20 | 常量改 `true`。DataStore 键（默认关）/`setBackupDownloaded`/备份管线分支全保留 |
| "主歌词提供商"设置项 | 入口删除（无门控，恢复看 TODO） | `SettingScreen.kt`（已无引用）；唯一选择处 = 播放页三点菜单，且仅 YT 歌显示（网易歌走官方专线） | 2026-09-15, 9829c5b2 | 跨源歌词供应商（QQ/酷狗…）做好后，入口放回**播放页菜单**，设置页不恢复（决策见 AGENTS.md 跨源歌词 TODO） |
| neteaseAutoSwitch（网易灰歌自动切 YT 源） | SettingItem 注释掉 | `SettingScreen.kt:1431` 附近 | 2026-09-15（切源统一入口轮） | M9 灰歌回退实现后恢复此 SettingItem；VM 状态与 setter 均保留 |

## 二、库页 chip 下线（路由保留）

**背景**：收藏体系云端化终稿（2026-09-20）把库页 chips 重组为
`[YT 歌单 / 您的网易云 / 排行榜 / Wrapped / 下载管理]`（`LibraryScreen.kt` 的 `topLevelLibraryChips`），
以下 enum 从 chip 行移除，**独立路由仍在**（`LibraryScreen.kt:205-214` 可直达）：

| chip | 现状 |
| --- | --- |
| YOUR_LIBRARY（您的库聚合页） | 持久化选中落在它上面会弹回新默认（`LibraryViewModel.kt:196`），登出回落同理 |
| LOCAL_PLAYLIST（本地歌单） | **暂无 UI 入口**（用户点名下线；数据仍在库中） |
| FAVORITE_PLAYLIST（收藏歌曲/红心聚合） | **暂无 UI 入口**；红心本身仍可在播放页/迷你条操作，云端同步不受影响 |
| FAVORITE_PODCAST | 随"您的库"一并下线 |

**衍生影响（2026-09-20 已处理）**：本地歌单/收藏歌曲无入口后，**备份是这批本地数据的唯一带出通道**，
"备份"按钮文案已改为明示包含它们（`backup_description`："备份设置、本地歌单、收藏歌曲、曲库与听歌记录"）。

## 三、播放器 / 菜单级移除

| 功能 | 处置 | 位置 | 说明 |
| --- | --- | --- | --- |
| 详情卡"相似歌曲"入口 | 删除（字符串 `similar_songs` 保留备用） | 原 NowPlaying 详情卡数据行 | 因 YTM 描述卡无此元素、破坏双源一致性（2026-09-15）。电台功能仍从三点菜单"收听电台"进入；将来做独立相似歌曲页（复用歌单页布局），不回详情卡 |
| 网易艺人页 Popular"更多"按钮 | 隐藏（browseId 置 null） | `core` NeteaseRepository songs 映射 | 4e8752c3（2026-09-17）：top-50 一次给全，本就无更多页 |
| 网易歌"加到歌单"弹窗的 YouTube 分区 | 按源隐藏 | `ModalBottomSheet.kt`（`visibleYouTubePlaylists` 对网易歌置空） | d1e076e0（2026-09-14） |

## 四、条件可见（gate，非隐藏）

这些是**按状态显示**的正常逻辑，列在这里防止误判为丢失：

- Mix（混合）tab：跟当前源登录态（`App.kt` `showMixForYouTab`），"YT 未登录 + 选 YT"不露出空 YT mixes 页。
- 收藏三件套（红心/加歌单/关注/退出按钮）：未登录 enabled 置灰（收藏云端化终稿 gate 清单）。
- 网易歌下导出按钮文案为"下载音频文件"（YT 歌为"下载视频文件"）、分享 URL 按源分流——**差异化文案，非隐藏**。

## 五、已删除（物理移除，恢复需回溯 git）

- 收藏体系三版中间方案（单按钮+叹号 / 两端不一致角标+方向弹窗 / 云朵按钮）——组件、表、strings 删干净（终稿=收藏全面云端化，2026-09-20）。
- 同步类设置（同步债方向等）——全删，DataStore 键留无消费；toast 文案中性化。
- 云心手动按钮、YT"加入已喜欢"按钮及契约层 likeStatus 等字段——终稿删除（播放页只剩单一红心）。
- "Slowed + Reverb"一键预设——绕过播放页 speed/pitch 锁被要求移除（见 CLAUDE.md 音效节）。

## 维护规则

1. 新增任何"隐藏"处置，**必须**在本表登记（方式/位置/恢复方法），设置项隐藏优先用
   `SHOW_XXX = false` 常量门控（Discord / 备份已下载数据同款），并在 `SettingScreen.kt` 常量处留注释。
2. 恢复某项时，同步删除本表对应行或在"现状"里更新。
3. CLAUDE.md 的 Auto-Update Rule 照常适用；本表是其"UI 收缩"维度的明细账。
