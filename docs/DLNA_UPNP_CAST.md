# Google Cast 与 DLNA/UPnP AV 投屏

## 范围

Android Full 构建在原有 Google Cast 基础上增加 DLNA/UPnP AV MediaRenderer 支持。入口仍是播放页原有的 `MediaRouteButton`，没有另做 Compose 设备选择弹窗；AndroidX MediaRouter 的原生选择/控制对话框负责 Wi-Fi 图标、搜索态、空态、确认按钮和已连接控制界面。

`cast-empty` 只提供同签名空实现，因此 FOSS 构建仍不显示投屏入口，也不引入 Google Play Services 或实际 DLNA 实现。

## 发现与列表合并

`cast/DlnaMediaRouteProvider.kt` 注册一个进程级 `MediaRouteProvider`，并把下面两类 category 放进同一个 selector：

- Google Cast 默认接收器 category；
- SimpMusic 的 DLNA category。

MediaRouter 因此继续显示原生对话框：Google Cast 设备由 Play Services 发布，DLNA 设备由 SimpMusic provider 发布，扫到哪类就显示哪类，两者可以同时出现。

DLNA 扫描只在 MediaRouter 请求 DLNA category 时运行。`DlnaDiscovery` 的流程是：

1. 从 `ConnectivityManager` 枚举 Wi-Fi 和 Ethernet `Network`，避免把请求错误发到蜂窝或 VPN 默认出口；没有可用枚举结果时才回退默认网络。
2. 持有非引用计数的 Wi-Fi multicast lock，在每个局域网分别绑定 UDP socket。
3. 对 `MediaRenderer:1`、`AVTransport:1` 和 `ssdp:all` 各发三轮 M-SEARCH，接收窗口默认 4.5 秒。
4. 在收到 SSDP `LOCATION` 的同一个 `Network` 上拉取设备描述 XML。
5. 优先接受标准 `MediaRenderer`；设备类型是厂商私有值时，只要暴露可用的 `AVTransport` service 也接受。这是小米等智能音箱兼容的关键。
6. 按 UDN 去重并发布到 MediaRouter；存在 `RenderingControl` 时声明可变音量。

需要的 Android 权限是 `INTERNET`、`ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE` 和 `CHANGE_WIFI_MULTICAST_STATE`。发现依赖同一可达的组播局域网；访客网络隔离、AP isolation、路由器屏蔽 SSDP 或设备休眠都会造成搜索不到。

## 播放交接

协议层与播放层通过 `DlnaSessionBridge` 解耦。用户选择 DLNA route 后：

1. 先结束可能仍在活动的 Google Cast session；
2. `DlnaHandoffManager` 记录本地队列下标、进度和播放状态；
3. 复用 `CastStreamResolver` 把当前媒体解析成接收器可直接访问的 URL；
4. 通过 `SetAVTransportURI` 发送 URL 和 DIDL-Lite 标题、歌手、专辑、封面、MIME 元数据；
5. 需要时先 `Seek`，再按原状态 `Play`；
6. 每秒轮询 `GetPositionInfo` 和 `GetTransportInfo`，更新应用内进度/状态，并在确认播放结束后切到下一首；
7. 取消 route 时向接收器发 `Stop`，按最后已知位置恢复本地播放。

`CrossfadeExoPlayerAdapter` 不再直接依赖某一种远端 `Player`，而是通过 `RemotePlaybackController` 统一路由播放、暂停、停止、进度、音量和播放状态。远端 owner 区分 `GOOGLE_CAST` 与 `DLNA`；旧协议迟到的断开回调不能关闭后来接管的新协议 session。

DLNA 当前按单曲交接和应用端续播，不向接收器写入原生播放队列。接收器还必须能从自身网络访问解析出的 HTTP(S) 媒体 URL；只在手机本地可访问、需要未携带凭据或被接收器 TLS/格式能力拒绝的 URL 仍可能发现成功但播放失败。

## 厂商兼容处理

### Android XML 解析器

桌面 JVM 接受 `DocumentBuilderFactory.isXIncludeAware = false`，部分 Android 内置解析器却会对这个禁用操作本身抛 `UnsupportedOperationException`。安全解析配置中的 XInclude 和实体展开 setter 因此使用 best-effort，DOCTYPE 与外部实体 feature 仍继续尝试关闭。不能直接调用 XInclude setter，否则真机上每一份设备描述都会在读取前被丢弃。

### LEBO / HappyCast 控制地址

实测超4电视的设备描述符合 `MediaRenderer:1`，但 `controlURL` 使用：

```text
_urn:schemas-upnp-org:service:AVTransport_control
```

冒号会让 `java.net.URI.resolve` 把它误判为非法 scheme。对 `_urn:` 前缀做限定兼容，将其视为 `URLBase` 根路径后，实际 endpoint 可正常响应 UPnP SOAP；不要对所有非法 URI 做无条件拼接，以免掩盖真正损坏的描述。

## 验证记录（2026-09-28）

真机为 Samsung SM-S9380，与接收器处于同一 Wi-Fi：

- 原生“投放到”对话框同时显示 `小爱音箱-6474`；
- 同时显示 `超4 X43 Pro-e683(lebo)`，设备描述标识 manufacturer=`LEBO`、model=`HappyCast`；
- 超4电视的兼容后 AVTransport endpoint 对 `GetTransportInfo` 返回 HTTP 200；
- 原来的空态/Wi-Fi 图标/确认逻辑由 MediaRouter 原生 UI 保留。

验证命令：

```bash
./gradlew :cast:testDebugUnitTest :cast:lintDebug :androidApp:assembleDebug
./gradlew :cast:testDebugUnitTest :cast:lintDebug :androidApp:assembleRelease \
  -x :androidApp:uploadSentryProguardMappingsRelease
git diff --check
git -C core diff --check
```

单元测试覆盖 SSDP header 大小写、标准/URLBase 相对控制地址、私有设备类型按 AVTransport capability 接受、HappyCast `_urn:` 地址、DIDL XML 转义和 UPnP 时间转换。发现列表已真机验证；本轮没有让音箱或电视实际出声，播放链路仍需按设备格式/TLS 能力做端到端抽样。
