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
| M3 | 搜索切源 | ❌ 未开始（core 端点已备好） |
| M4 | 网易主页 | ✅ 独立屏：账户卡/每日推荐(滤雷达)/私人雷达(曲目三行网格)/雷达歌单(5卡)/精品歌单/推荐新歌(96dp紧凑卡三行)/榜单区块/五组分类(网页catalog目录,3行网格) |
| M5 | 分类页 + 混合 | ✅ 分类页（NeteaseTagScreen 两列网格，网页歌单广场同源）；❌ 混合页私人FM |
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

## 调试备忘（环境）

- 崩溃日志渠道：logcat tag `CustomActivityOnCrash`（CaC 接管了默认 FATAL 输出）。
- 模拟器菜单连招：`input swipe FAB同点2000ms` → `input tap 736 2009`（网易）/`771 1883`（YT）。
- DataStore pb 一律不做外部改写（键长变化会损坏解析）；改设置走 app 自己的 UI。
- 磁盘：单次 debug 构建 ≈2GB，满了先清项目 build 目录与 ~/.gradle 旧版本缓存。
- 冒烟探针：`/tmp/netease_cookies.json`（jvmTest 读取，不进仓库）驱动 `NewEndpointsProbe`。
