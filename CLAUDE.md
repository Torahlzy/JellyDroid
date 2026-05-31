# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此仓库中工作时提供指导。

## 项目概述

Findroid 是 Jellyfin 的第三方 Android 应用——使用原生 Jetpack Compose 界面浏览和播放电影/剧集。支持两种视频后端：ExoPlayer 和 mpv。

## 构建命令

```bash
# 构建手机版（debug）
./gradlew :app:phone:assembleLibreDebug

# 构建手机版（release）
./gradlew :app:phone:assembleLibreRelease

# 构建 Android App Bundle
./gradlew :app:phone:bundleLibreRelease

# 运行所有检查（lint + ktfmt 验证）
./gradlew check

# 格式化 Kotlin 代码（ktfmt, kotlinLangStyle）
./gradlew ktfmtFormat

# 清理
./gradlew clean
```

**构建变体：** 一种 flavor `libre`，三种 build type（`debug`、`release`、`staging`）。staging 继承 `release`，application ID 后缀为 `.staging`。项目中**没有单元测试或插桩测试**——`check` 仅验证 ktfmt 格式。

所有子项目均通过根 `build.gradle.kts` 配置了 ktfmt 的 `kotlinLangStyle()` 格式化。

**注意：** 修改代码后，不需要运行 `./gradlew check`。该命令仅验证 ktfmt 格式，项目中没有单元测试。

## 架构

### 模块依赖关系

```
:app:phone              ← 手机应用入口（Jetpack Compose UI + 导航）
:app:tv                 ← Android TV 应用（规划中）
  ├── :core             ← 共享 ViewModel、DI 装配、下载器、后台任务
  ├── :data             ← API 客户端、仓库、Room 数据库、DTO 模型
  ├── :player:core      ← 播放器领域模型（PlayerItem、Track、TrickplayInfo）
  ├── :player:local     ← 本地播放器（PlaylistManager、MPVPlayer）
  ├── :setup            ← 引导流程（添加服务器、登录、选择用户）
  ├── :modes:film       ← 影视模式 ViewModel（首页、库、电影、剧集、搜索等）
  └── :settings         ← 偏好设置系统（AppPreferences + 设置界面）
```

`:core` 依赖 `:data`、`:player:core`、`:settings`。所有模块均使用 Hilt 进行依赖注入。

### 架构模式

**MVVM + 单向数据流。** 每个页面包含：
- 一个 `ViewModel`，暴露 `StateFlow<XxxState>` 并处理 `XxxAction` 密封类。
- 一个 `@Composable` Screen（位于 `:app:phone`），观察状态并分发动作。
- State/Action/Event 文件与 ViewModel 同层放置（`🤰film` 或 `:setup` 中）。

以电影页为例：
- `MovieState.kt` / `MovieAction.kt` 在 `🤰film`
- `MovieViewModel.kt` 在 `🤰film`
- `MovieScreen.kt` 在 `:app:phone`（Compose UI）

### API 层

项目使用 **org.jellyfin.sdk:jellyfin-core**（非手写 HTTP）。参见 [doc/DEVELOPMENT.md](doc/DEVELOPMENT.md) 和 [doc/jellyfinapi/Jellyfin_API.md](doc/jellyfinapi/Jellyfin_API.md)。

- `JellyfinApi`（单例，位于 `:data`）封装 SDK 的 `createApi()`，暴露各子 API（`itemsApi`、`userLibraryApi`、`showsApi` 等）。
- `JellyfinRepository` 接口定义所有数据操作；`JellyfinRepositoryImpl` 调用 SDK 方法。
- `JellyfinRepositoryOfflineImpl` 是离线模式的替代实现——从 Room 数据库读取而非网络。
- `RepositoryModule` 根据 `AppPreferences.offlineMode` 在这两者之间切换。
- `ItemsPagingSource` 封装 `getItems()`，实现 Android Paging 3 的偏移分页。

### 数据库（Room）

`:data` 中的单一数据库 `ServerDatabase`（版本 8）。存储内容：
- `Server` / `ServerAddress` / `User` — 连接信息
- `FindroidMovieDto` / `FindroidShowDto` / `FindroidSeasonDto` / `FindroidEpisodeDto` — 已下载媒体
- `FindroidSourceDto` / `FindroidMediaStreamDto` — 媒体源元数据
- `FindroidUserDataDto` — 播放位置、收藏、已播放状态（支持离线回传）
- `FindroidTrickplayInfoDto` / `FindroidSegmentDto` — 缩略图预览和片头/片尾分段

### 偏好设置

`:settings` 模块。`AppPreferences` 封装 `SharedPreferences`，通过偏好键枚举提供类型化的 get/set。供设置界面和整个应用中的配置使用。

### 导航

使用 Jetpack Navigation Compose 的类型安全导航。路由为 `@Serializable` 数据对象/类，定义在 `NavigationRoot.kt` 中。底部栏有 3 个标签：首页、媒体（库）、下载。`safeNavigate` 辅助方法确保仅在生命周期为 RESUMED 时才导航。

### 媒体项类型层次

`FindroidItem`（密封接口）→ `FindroidMovie`、`FindroidShow`、`FindroidSeason`、`FindroidEpisode`、`FindroidBoxSet`、`FindroidCollection`、`FindroidFolder`、`FindroidPerson`。SDK 的 `BaseItemDto` 对象通过扩展函数映射（`toFindroidItem()`、`toFindroidMovie()` 等）。

### 核心应用类

- **`BaseApplication`** — `@HiltAndroidApp` 入口。配置 Timber（debug 日志）、Android 12 以下的深色模式、动态取色、Coil 图片加载器（SVG 支持、磁盘缓存），并调度后台任务（`SyncWorker`、`MpvCleanupWorker`）。
- **`MainActivity`** — 单 Activity 宿主。读取 `MainViewModel` 状态决定启动路由（有服务器？有用户？→ 首页/欢迎/用户选择）。向 Compose 树提供 `LocalOfflineMode` CompositionLocal。
- **`BasePlayerActivity`** / **`PlayerActivity`** — 视频播放 Activity。使用 Media3 `MediaSession` 进行媒体会话集成。

### 离线模式与下载

应用具备完整的离线支持。`LocalOfflineMode` 是一个 `CompositionLocal`，用于隐藏仅联网可用的功能（如媒体标签页）。仓库层（`RepositoryModule`）在注入时切换：

- **`JellyfinRepositoryImpl`** → 通过网络调用 Jellyfin SDK API
- **`JellyfinRepositoryOfflineImpl`** → 仅从 Room 数据库读取

`Downloader` / `DownloaderImpl`（位于 `:core`）处理媒体项及其资源下载到本地存储。`ImagesDownloaderWorker` 在后台下载封面图。`SyncWorker` 在网络恢复时将离线用户数据（播放位置、收藏）回传到服务器。

### 播放器架构

两种后端，均可从 `:player:local` 使用：

- **ExoPlayer**（Media3）— 默认。FFmpeg 扩展提供音频编解码支持。
- **mpv** — 通过 `dev.jdtech.mpv:libmpv`。`MPVPlayer` 封装 mpv C 库。用户可启用软件解码。

`PlaylistManager` 管理播放队列。`PlayerViewModel` 暴露播放器状态并接收 `PlayerAction` 命令。播放器领域模型（`PlayerItem`、`Track`、`PlayerChapter`、`TrickplayInfo`）位于 `:player:core`，使其与后端无关。

### 后台任务

均在 `BaseApplication` 中通过 WorkManager 调度：

| Worker | 用途 |
|---|---|
| `SyncWorker` | 将离线用户数据变更推送到服务器 |
| `ImagesDownloaderWorker` | 为离线项目预下载封面图 |
| `MpvCleanupWorker` | 清理过期的 mpv 文件（设备空闲且电量充足时运行） |
