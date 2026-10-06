# Findroid 手机端主要页面说明（代码生成）

## 关于本文件

本文件是 `:app:phone`（Findroid 手机端）**主要页面**的索引与说明，内容依据以下代码生成：

- 路由表与页面装配：`app/phone/src/main/java/dev/jdtech/jellyfin/NavigationRoot.kt`
- 各页面的 Compose UI：`app/phone/src/main/java/dev/jdtech/jellyfin/presentation/**`
- 各页面的 ViewModel / State / Action：`modes/film`、`setup`、`settings`、`player/local` 模块

### 路径缩写

正文与表格中的文件路径统一使用下列前缀缩写，便于书写与机械展开：

| 缩写 | 展开为（相对仓库根目录） |
|---|---|
| `PHONE/` | `app/phone/src/main/java/dev/jdtech/jellyfin/` |
| `CORE/` | `core/src/main/java/dev/jdtech/jellyfin/core/` |
| `FILM/` | `modes/film/src/main/java/dev/jdtech/jellyfin/film/` |
| `SETUP/` | `setup/src/main/java/dev/jdtech/jellyfin/setup/` |
| `SETTINGS/` | `settings/src/main/java/dev/jdtech/jellyfin/settings/` |
| `PLAYER/` | `player/local/src/main/java/dev/jdtech/jellyfin/player/local/` |

### 阅读约定

- **正文中的代码引用只写文件路径，不写行号**。行号会随代码漂移而失准，定位一律以「路径 + 符号（类 / 函数名）」为准。
- 所有 Compose 页面遵循同一模式：`ViewModel` 暴露 `StateFlow<XxxState>` 并提供 `onAction(XxxAction)`，Screen 观察状态并分发动作。详见文末「页面共性约定」。
- 各页面 Screen 目前均无 KDoc，本文职责由 `@Composable` 参数 + ViewModel 的 State 字段 / 方法归纳得出。

**最后更新：2026-10-07**

---

## 目录

- [维护与更新机制](#维护与更新机制)
- [页面总览](#页面总览)
- [影视页面（film）](#影视页面film)
- [设置页面（settings）](#设置页面settings)
- [引导页面（setup）](#引导页面setup)
- [播放页面（player）](#播放页面player)
- [搜索组件（非独立页面）](#搜索组件非独立页面)
- [页面共性约定](#页面共性约定)
- [变更记录](#变更记录)

---

## 维护与更新机制

### 何时需要更新本文件

满足任一条件即需同步更新：

1. 新增 / 删除 / 重命名页面（Screen、ViewModel、State、Action 文件增删）。
2. 新增 / 修改 / 删除导航路由（`PHONE/NavigationRoot.kt` 中的 `@Serializable` 路由类，或 `composable<...>` 注册块）。
3. 页面职责发生变化（新增 section、更换数据来源、改变入口页面等）。
4. 页面的 State 字段或 Action 集合出现较大调整。

### 更新步骤

1. 先以 `PHONE/NavigationRoot.kt` 的路由类定义与 `NavHost` 注册块为准，核对「页面总览」表。
2. 定位对应页面条目，更新：职责、路由、Screen / ViewModel / State / Action 路径、State 字段、主要 Action、入口。
3. 在文末「变更记录」追加一行（日期 + 摘要）。
4. 更新开头的「最后更新」日期。
5. 检查全文无行号引用（见下方「校验方法」）。

### 校验方法

在仓库根目录执行以下命令，可校验文档中引用的所有 `.kt` 路径是否真实存在（自动展开路径缩写）：

```powershell
$prefix = @{
  'PHONE/'    = 'app/phone/src/main/java/dev/jdtech/jellyfin/'
  'CORE/'     = 'core/src/main/java/dev/jdtech/jellyfin/core/'
  'FILM/'     = 'modes/film/src/main/java/dev/jdtech/jellyfin/film/'
  'SETUP/'    = 'setup/src/main/java/dev/jdtech/jellyfin/setup/'
  'SETTINGS/' = 'settings/src/main/java/dev/jdtech/jellyfin/settings/'
  'PLAYER/'   = 'player/local/src/main/java/dev/jdtech/jellyfin/player/local/'
}
Select-String -Path wiki/pages.md -Pattern '\b(PHONE|CORE|FILM|SETUP|SETTINGS|PLAYER)/[a-zA-Z0-9_/\.\-]+\.kt' -AllMatches -CaseSensitive |
  ForEach-Object { $_.Matches.Value } | Sort-Object -Unique |
  ForEach-Object {
    $p = $_
    foreach ($k in $prefix.Keys) { if ($p.StartsWith($k)) { $p = $p.Replace($k, $prefix[$k]) } }
    if (-not (Test-Path $p)) { "缺失: $_" }
  }
```

无输出即表示文档中的所有路径均有效。此外，页面数量应与 `PHONE/NavigationRoot.kt` 中 `composable<...>` 注册块数量一致（当前为 20 个注册块 + 1 个播放 Activity）。

校验文档中不含行号引用（无输出为通过）：

```powershell
Select-String -Path wiki/pages.md -Pattern '\.kt:\d+' -AllMatches
```

---

## 页面总览

| 分组 | 页面 | 路由 | Screen（Compose UI） | ViewModel |
|---|---|---|---|---|
| 主标签 | 首页 | `HomeRoute` | `PHONE/presentation/film/HomeScreen.kt` | `FILM/presentation/home/HomeViewModel.kt` |
| 主标签 | 媒体库 | `MediaRoute` | `PHONE/presentation/film/MediaScreen.kt` | `FILM/presentation/media/MediaViewModel.kt` |
| 主标签 | 下载 | `DownloadsRoute` | `PHONE/presentation/film/DownloadsScreen.kt` | `FILM/presentation/downloads/DownloadsViewModel.kt` |
| 内容 | 媒体库详情 | `LibraryRoute` | `PHONE/presentation/film/LibraryScreen.kt` | `FILM/presentation/library/LibraryViewModel.kt` |
| 内容 | 合集详情 | `CollectionRoute` | `PHONE/presentation/film/CollectionScreen.kt` | `FILM/presentation/collection/CollectionViewModel.kt` |
| 内容 | 收藏 | `FavoritesRoute` | `PHONE/presentation/film/FavoritesScreen.kt` | `FILM/presentation/favorites/FavoritesViewModel.kt` |
| 内容 | 电影详情 | `MovieRoute` | `PHONE/presentation/film/MovieScreen.kt` | `FILM/presentation/movie/MovieViewModel.kt` |
| 内容 | 剧集详情 | `ShowRoute` | `PHONE/presentation/film/ShowScreen.kt` | `FILM/presentation/show/ShowViewModel.kt` |
| 内容 | 季详情 | `SeasonRoute` | `PHONE/presentation/film/SeasonScreen.kt` | `FILM/presentation/season/SeasonViewModel.kt` |
| 内容 | 分集详情 | `EpisodeRoute` | `PHONE/presentation/film/EpisodeScreen.kt` | `FILM/presentation/episode/EpisodeViewModel.kt` |
| 内容 | 人物详情 | `PersonRoute` | `PHONE/presentation/film/PersonScreen.kt` | `FILM/presentation/person/PersonViewModel.kt` |
| 设置 | 设置 | `SettingsRoute` | `PHONE/presentation/settings/SettingsScreen.kt` | `SETTINGS/presentation/settings/SettingsViewModel.kt` |
| 设置 | 文件编辑 | `SettingsFileEditRoute` | `PHONE/presentation/settings/SettingsFileEditScreen.kt` | `SETTINGS/presentation/settings/SettingsFileEditViewModel.kt` |
| 设置 | 关于 | `AboutRoute` | `PHONE/presentation/settings/AboutScreen.kt` | 无 |
| 引导 | 欢迎 | `WelcomeRoute` | `PHONE/presentation/setup/welcome/WelcomeScreen.kt` | 无 |
| 引导 | 服务器列表 | `ServersRoute` | `PHONE/presentation/setup/servers/ServersScreen.kt` | `SETUP/presentation/servers/ServersViewModel.kt` |
| 引导 | 添加服务器 | `AddServerRoute` | `PHONE/presentation/setup/addserver/AddServerScreen.kt` | `SETUP/presentation/addserver/AddServerViewModel.kt` |
| 引导 | 服务器地址 | `ServerAddressesRoute` | `PHONE/presentation/setup/addresses/ServerAddressesScreen.kt` | `SETUP/presentation/addresses/ServerAddressesViewModel.kt` |
| 引导 | 用户列表 | `UsersRoute` | `PHONE/presentation/setup/users/UsersScreen.kt` | `SETUP/presentation/users/UsersViewModel.kt` |
| 引导 | 登录 | `LoginRoute` | `PHONE/presentation/setup/login/LoginScreen.kt` | `SETUP/presentation/login/LoginViewModel.kt` |
| 播放 | 视频播放 | 无（Activity，非 NavHost 路由） | `PHONE/PlayerActivity.kt` | `PLAYER/presentation/PlayerViewModel.kt` |

**底部导航标签**（`PHONE/NavigationRoot.kt`）：首页 `HomeRoute`、媒体 `MediaRoute`、下载 `DownloadsRoute`。离线模式下（`LocalOfflineMode == true`）隐藏「媒体」标签（`PHONE/NavigationRoot.kt`）。

**启动路由**（`PHONE/NavigationRoot.kt`）：

```kotlin
hasServers && hasCurrentServer && hasCurrentUser -> HomeRoute
hasServers && hasCurrentServer -> UsersRoute
hasServers -> ServersRoute
else -> WelcomeRoute
```

---

## 影视页面（film）

### HomeScreen — 首页

- **职责**：聚合首页。顶部为 `HomeHeader`（服务器名、搜索、设置、重试）；下方按 section 渲染推荐轮播、继续观看、接下来、各媒体库最新。
- **路由**：`HomeRoute`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/film/HomeScreen.kt`；section 组装在 `HomeScreen.kt`。
- **ViewModel / State / Action**：`FILM/presentation/home/HomeViewModel.kt`、`FILM/presentation/home/HomeState.kt`、`FILM/presentation/home/HomeAction.kt`。
- **State 字段**：`server`、`suggestionsSection`、`resumeSection`、`nextUpSection`、`views`、`isLoading`、`error`。
- **主要方法 / Action**：`loadData()`；`OnItemClick`、`OnLibraryClick`、`OnRetryClick`、`OnSearchClick`、`OnSettingsClick`、`OnManageServers`。
- **数据来源**：`getSuggestions()`、`getResumeItems()`、`getNextUp()`、`getUserViews()` + `getLatestMedia(view.id)`（`HomeViewModel.kt`）；每个 section 分别受 `AppPreferences` 的 `homeSuggestions` / `homeContinueWatching` / `homeNextUp` / `homeLatest` 开关控制，为空则不渲染。
- **入口**：启动路由 / 底部「首页」标签 / 各详情页的 `navigateHome`。

### MediaScreen — 媒体库总览

- **职责**：展示「收藏」入口与全部媒体库网格；顶部内嵌搜索栏（`FilmSearchBar`）。媒体库卡片为横图（`BANNER_ASPECT_RATIO`）；合集库（`CollectionType.BoxSets`）服务器上不提供封面，卡片改用通用图标 `CoreR.drawable.ic_collection` 占位。
- **路由**：`MediaRoute`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/film/MediaScreen.kt`；搜索栏挂载于 `MediaScreen.kt`。
- **ViewModel / State / Action**：`FILM/presentation/media/MediaViewModel.kt`、`FILM/presentation/media/MediaState.kt`、`FILM/presentation/media/MediaAction.kt`。
- **State 字段**：`libraries`、`isLoading`、`error`。
- **主要方法 / Action**：`loadData()`；`OnItemClick`、`OnFavoritesClick`、`OnRetryClick`。
- **入口**：底部「媒体」标签；首页搜索按钮会切到此页并置 `searchExpanded = true`（`PHONE/NavigationRoot.kt`）。

### DownloadsScreen — 下载

- **职责**：展示本地已下载条目，分组网格呈现；空列表时提示「无下载」；点击条目进入对应详情页。
- **路由**：`DownloadsRoute`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/film/DownloadsScreen.kt`。
- **ViewModel**：`FILM/presentation/downloads/DownloadsViewModel.kt`。
- **State / Action**：**复用** `FILM/presentation/collection/CollectionState.kt` 与 `FILM/presentation/collection/CollectionAction.kt`（无独立 State/Action，VM 无 `onAction`）。
- **State 字段**：`sections`、`isLoading`、`error`。
- **主要方法**：`loadItems()`（内部调用 `repository.getDownloads()`）。
- **入口**：底部「下载」标签。

### LibraryScreen — 媒体库详情

- **职责**：单个媒体库的内容列表，支持分页加载、排序切换（含随机排序）与封面显示模式切换（右上角 `SortByDialog` 同时承载两项设置），条目点击进入详情。
- **路由**：`LibraryRoute(libraryId, libraryName, libraryType)`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/film/LibraryScreen.kt`；设置弹窗为 `PHONE/presentation/film/components/SortByDialog.kt`。
- **ViewModel / State / Action**：`FILM/presentation/library/LibraryViewModel.kt`、`FILM/presentation/library/LibraryState.kt`、`FILM/presentation/library/LibraryAction.kt`。
- **State 字段**：`items`（PagingData Flow）、`sortBy`、`sortOrder`、`coverMode`（`CoverDisplayMode`，默认竖图）、`isLoading`、`error`。
- **主要方法 / Action**：`setup(parentId, libraryType)`、`loadItems()`、`onAction()`；`OnItemClick`、`OnBackClick`、`ChangeSorting`、`ChangeCoverMode`。
- **封面显示模式**：偏好持久化在 `AppPreferences.libraryCoverMode`（`pref_library_cover_mode`）；横图模式走 `Direction.HORIZONTAL`，电影 / 剧集 / 合集 / 媒体库等优先取自身 `backdrop`（分集优先取 16:9 剧照 `primary`），宽高比沿用首页 `BANNER_ASPECT_RATIO`，列数按可用宽度自适应（窄屏一列、折叠屏展开等宽屏两列）；竖图走 `Direction.VERTICAL` 取 `primary`、多列网格；某一类图片缺失时由 `ItemPoster` 用另一类临时补位。
- **合集列表封面**：合集（`FindroidBoxSet`）在服务器上没有图片，`JellyfinRepositoryImpl` 内的私有方法 `getBoxSetCoverImages()` 取合集内部条目的图片作为封面（并行补齐并限制并发、按服务器地址 + 合集 id 在进程内缓存），补齐在 `JellyfinRepositoryImpl.getItems()` 内完成，单次试探失败不影响列表加载。该方法不进 `JellyfinRepository` 接口（离线模式没有合集数据，无需为其写空实现）。合集卡片（`ItemCard`，条目为 `FindroidBoxSet`）封面右上角叠加合集角标 `BoxSetBadge`（复用 `ic_collection`），用于和普通影片区分。
- **随机排序**：排序菜单的「随机」由 `data/src/main/java/dev/jdtech/jellyfin/repository/ItemsPagingSource.kt` 在客户端实现——以服务端随机排序一次取回一批（上限 `RANDOM_SNAPSHOT_SIZE = 500`）并本地打乱，作为本次会话的固定顺序，之后各页只做本地切片；不沿用偏移量分页，避免服务端每次请求重新洗牌造成的重复与遗漏。菜单可选排序项由 `SortBy.selectableValues` 提供（`data/src/main/java/dev/jdtech/jellyfin/models/SortBy.kt`），已排除仅供内部使用的 `SERIES_DATE_PLAYED`；随机排序下 `SortByDialog` 会禁用排序顺序按钮（结果与顺序无关）。
- **入口**：首页媒体库卡片；点击 `FindroidCollection` / `FindroidFolder` 条目（`PHONE/NavigationRoot.kt`）。

### CollectionScreen — 合集详情

- **职责**：合集（BoxSet）详情页，按电影 / 剧集 / 单集分组展示合集内条目。
- **路由**：`CollectionRoute(collectionId, collectionName)`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/film/CollectionScreen.kt`。
- **ViewModel / State / Action**：`FILM/presentation/collection/CollectionViewModel.kt`、`FILM/presentation/collection/CollectionState.kt`、`FILM/presentation/collection/CollectionAction.kt`。
- **State 字段**：`sections`、`isLoading`、`error`。
- **主要方法 / Action**：`loadItems(parentId)`；`OnItemClick`、`OnItemDetails`、`OnBackClick`。
- **入口**：点击 `FindroidBoxSet` 条目（`PHONE/NavigationRoot.kt`）。

### FavoritesScreen — 收藏

- **职责**：读取收藏条目，按电影 / 剧集 / 单集分组展示。
- **路由**：`FavoritesRoute`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/film/FavoritesScreen.kt`。
- **ViewModel**：`FILM/presentation/favorites/FavoritesViewModel.kt`。
- **State / Action**：**复用** `FILM/presentation/collection/CollectionState.kt` 与 `FILM/presentation/collection/CollectionAction.kt`（无独立 State/Action）。
- **State 字段**：`sections`、`isLoading`、`error`。
- **主要方法**：`loadItems()`（`repository.getFavoriteItems()`）。
- **入口**：媒体库页的「收藏」入口（`PHONE/NavigationRoot.kt`）。

### MovieScreen — 电影详情

- **职责**：加载影片元数据 / 视频信息 / 演员，提供播放、预告、标记已看、收藏、下载、删除服务器文件（封面 / nfo / 条目本身）、跳转演员，并处理离线模式。
- **路由**：`MovieRoute(movieId)`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/film/MovieScreen.kt`；副标题下方的文件存放路径由 `PHONE/presentation/film/components/FilePathText.kt` 渲染（默认 1 行，截断时可点击展开 / 收起），已下载时先显示本地路径、再显示服务器路径（各自按来源去重后拼接，可能多行），并按来源区分前置图标——服务端路径用 `ic_cloud`（云）、本地已下载路径用 `ic_folder`（文件夹）；条目在服务器上有多个来源（同一影片被识别成多个版本）时，点播放先弹「选择播放版本」→ `PHONE/presentation/film/components/PlaybackSourceDialog.kt`（列出每个来源，云 / 文件夹图标区分，选中后按索引启动播放器）；按钮行末尾的「更多」按钮（离线模式不显示）打开 `PHONE/presentation/film/components/MoreMenuDialog.kt`，三个入口各自再弹一次确认：「删除封面」→ `PHONE/presentation/film/components/DeleteItemImagesDialog.kt`（列出服务器图片，缩略图 + 类型名，默认全选、可逐张取消）、「编辑 nfo」→ `PHONE/presentation/film/components/EditItemMetadataDialog.kt`（用服务器元数据回填 12 个可编辑字段：标题、原名、简介、类型、标签、制片公司、制片国家、宣传语、分级、制作年份、首播日期、社区评分；多值字段用逗号分隔，底部为「清空」/「确认」，标题是唯一必填项、为空时提示必填并禁用确认，数值 / 日期不合法同样禁用确认，加载失败在弹窗内说明原因）、「删除全部」→ `PHONE/presentation/film/components/DeleteItemWithFilesDialog.kt`（会连服务器上的视频文件一起删，确认按钮为错误色）；弹窗切换由 `MovieScreen.kt` 内的私有枚举 `MoreMenuDialogState` 控制（`rememberSaveable` 保存，旋转屏幕不丢失），表单数据由 `LaunchedEffect(moreDialog)` 在弹窗打开时加载（弹窗状态被恢复时也能补上，不会一直转圈），结果以 Toast 提示，「删除全部」成功后返回上一页。
- **ViewModel / State / Action / Event**：`FILM/presentation/movie/MovieViewModel.kt`、`FILM/presentation/movie/MovieState.kt`、`FILM/presentation/movie/MovieAction.kt`、`FILM/presentation/movie/MovieEvent.kt`；另使用 `CORE/presentation/downloader/DownloaderViewModel.kt`。
- **State 字段**：`movie`、`videoMetadata`、`actors`、`director`、`writers`、`displayExtraInfo`、`itemImages`（服务器图片列表，元素为 `FindroidItemImage`）、`isLoadingItemImages`、`itemImagesError`（图片列表加载失败原因，非空时弹窗内提示失败，不再额外弹 Toast）、`itemMetadata`（服务器上可编辑的元数据，元素为 `ItemMetadataEdit`，为 `null` 表示尚未加载）、`isLoadingItemMetadata`、`itemMetadataError`（元数据加载失败原因，同样只在弹窗内提示）、`error`、`playbackSources`（可选播放来源，元素为 `FindroidSource`，由 `getMediaSources()` 获取以保证与播放端顺序一致，为空表示尚未加载）；派生属性 `localFilePath`、`remoteFilePath`（分别取 `playbackSources` 中 `LOCAL` / `REMOTE` 来源的路径，各自去重后拼接，无有效路径时为 `null`）。
- **主要方法 / Action**：`loadMovie(movieId)`、`loadItemImages()`、`loadItemMetadata()`、`onAction()`；`Play`、`PlayTrailer`、`MarkAsPlayed`、`UnmarkAsPlayed`、`MarkAsFavorite`、`UnmarkAsFavorite`、`OnBackClick`、`OnHomeClick`、`NavigateToPerson`、`DeleteItemImages(images)`、`UpdateItemMetadata(metadata)`、`DeleteItemWithFiles`；事件 `ItemImagesDeleted`、`MetadataUpdated`、`ItemDeleted`、`ItemImagesDeleteFailed`、`MetadataUpdateFailed`、`ItemDeleteFailed`。
- **入口**：任意列表页点击 `FindroidMovie` 条目（`PHONE/NavigationRoot.kt`），如首页、媒体库、下载、收藏、合集、剧集 / 季 / 人物页。

### ShowScreen — 剧集详情

- **职责**：加载剧集、下一集（NextUp）、季列表与演员，提供播放与标记等操作。
- **路由**：`ShowRoute(showId)`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/film/ShowScreen.kt`。
- **ViewModel / State / Action**：`FILM/presentation/show/ShowViewModel.kt`、`FILM/presentation/show/ShowState.kt`、`FILM/presentation/show/ShowAction.kt`。
- **State 字段**：`show`、`nextUp`、`seasons`、`actors`、`director`、`writers`、`error`。
- **主要方法 / Action**：`loadShow(showId)`、`onAction()`；`Play`、`PlayTrailer`、`MarkAsPlayed`、`UnmarkAsPlayed`、`MarkAsFavorite`、`UnmarkAsFavorite`、`OnBackClick`、`OnHomeClick`、`NavigateToItem`、`NavigateToPerson`。
- **入口**：点击 `FindroidShow` 条目（`PHONE/NavigationRoot.kt`）；季页的 `navigateToSeries`（`PHONE/NavigationRoot.kt`）。

### SeasonScreen — 季详情

- **职责**：加载季信息与其分集列表，提供播放、标记、跳转条目或所属剧集。
- **路由**：`SeasonRoute(seasonId)`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/film/SeasonScreen.kt`。
- **ViewModel / State / Action**：`FILM/presentation/season/SeasonViewModel.kt`、`FILM/presentation/season/SeasonState.kt`、`FILM/presentation/season/SeasonAction.kt`。
- **State 字段**：`season`、`episodes`、`error`。
- **主要方法 / Action**：`loadSeason(seasonId)`、`onAction()`；`Play`、`MarkAsPlayed`、`UnmarkAsPlayed`、`MarkAsFavorite`、`UnmarkAsFavorite`、`OnBackClick`、`OnHomeClick`、`NavigateToItem`、`NavigateToSeries`。
- **入口**：点击 `FindroidSeason` 条目（`PHONE/NavigationRoot.kt`）；分集页的 `navigateToSeason`（`PHONE/NavigationRoot.kt`）。

### EpisodeScreen — 分集详情

- **职责**：加载分集元数据 / 视频信息 / 演员，提供播放、标记已看、收藏、下载、跳转演员与所属季。
- **路由**：`EpisodeRoute(episodeId)`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/film/EpisodeScreen.kt`。
- **ViewModel / State / Action**：`FILM/presentation/episode/EpisodeViewModel.kt`、`FILM/presentation/episode/EpisodeState.kt`、`FILM/presentation/episode/EpisodeAction.kt`；另使用 `CORE/presentation/downloader/DownloaderViewModel.kt`。
- **State 字段**：`episode`、`videoMetadata`、`actors`、`displayExtraInfo`、`error`。
- **主要方法 / Action**：`loadEpisode(episodeId)`、`onAction()`；`Play`、`MarkAsPlayed`、`UnmarkAsPlayed`、`MarkAsFavorite`、`UnmarkAsFavorite`、`OnBackClick`、`OnHomeClick`、`NavigateToPerson`、`NavigateToSeason`。
- **入口**：点击 `FindroidEpisode` 条目（`PHONE/NavigationRoot.kt`）。

### PersonScreen — 人物详情

- **职责**：加载人物资料及其参演的电影与剧集。
- **路由**：`PersonRoute(personId)`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/film/PersonScreen.kt`。
- **ViewModel / State / Action**：`FILM/presentation/person/PersonViewModel.kt`、`FILM/presentation/person/PersonState.kt`、`FILM/presentation/person/PersonAction.kt`。注意：VM **无 `onAction`**，`PersonAction` 仅含导航动作，由 Screen 直接处理。
- **State 字段**：`person`、`starredInMovies`、`starredInShows`、`error`。
- **主要方法**：`loadPerson(personId)`。
- **入口**：电影 / 剧集 / 分集详情页点击演员（见 `PHONE/NavigationRoot.kt` 中 `NavigateToPerson` 的处理）。

---

## 设置页面（settings）

### SettingsScreen — 设置主页

- **职责**：按 `indexes` 参数展示分组偏好项（开关 / 下拉 / 数字输入 / 文件编辑 / 多选），处理嵌套导航与事件。
- **路由**：`SettingsRoute(indexes: IntArray)`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/settings/SettingsScreen.kt`。
- **ViewModel / State / Action / Event**：`SETTINGS/presentation/settings/SettingsViewModel.kt`、`SETTINGS/presentation/settings/SettingsState.kt`、`SETTINGS/presentation/settings/SettingsAction.kt`、`SETTINGS/presentation/settings/SettingsEvent.kt`。
- **State 字段**：`isLoading`、`preferenceGroups`。
- **主要方法 / Action**：`loadPreferences(indexes, deviceType)`、`onAction()`；`OnBackClick`、`OnUpdate`。
- **Event**：`NavigateToUsers`、`NavigateToServers`、`NavigateToAbout`、`NavigateToSettings`、`NavigateToSettingsFileEdit`、`UpdateTheme`、`LaunchIntent`、`RestartActivity`。
- **入口**：首页设置按钮（`PHONE/NavigationRoot.kt`）。

### SettingsFileEditScreen — 设置文件编辑

- **职责**：读取并保存应用 `filesDir` 下的文本文件。
- **路由**：`SettingsFileEditRoute(filePath)`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/settings/SettingsFileEditScreen.kt`。
- **ViewModel / State / Action**：`SETTINGS/presentation/settings/SettingsFileEditViewModel.kt`、`SETTINGS/presentation/settings/SettingsFileEditState.kt`、`SETTINGS/presentation/settings/SettingsFileEditAction.kt`。
- **State 字段**：`initialText`。
- **主要方法 / Action**：`loadFile(filePath)`、`onAction()`；`OnBackClick`、`OnSave`。
- **入口**：设置页内触发 `NavigateToSettingsFileEdit`。

### AboutScreen — 关于

- **职责**：展示应用 / 开源许可信息与外部链接。
- **路由**：`AboutRoute`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/settings/AboutScreen.kt`，参数仅 `navigateBack: () -> Unit`。
- **ViewModel / State / Action**：**无**（纯静态 UI + `uriHandler` 打开链接）。
- **入口**：设置页内触发 `NavigateToAbout`。

---

## 引导页面（setup）

### WelcomeScreen — 欢迎

- **职责**：引导欢迎页，继续进入服务器列表或打开 Jellyfin 官网。
- **路由**：`WelcomeRoute`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/setup/welcome/WelcomeScreen.kt`。
- **ViewModel / State**：**无 ViewModel**；仅有 `SETUP/presentation/welcome/WelcomeAction.kt`（`OnContinueClick`、`OnLearnMoreClick`），由 Screen 内联处理。
- **入口**：无服务器时的启动路由。

### ServersScreen — 服务器列表

- **职责**：展示已保存服务器及其地址，切换当前服务器 / 地址、删除服务器、进入地址管理或新增服务器。
- **路由**：`ServersRoute`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/setup/servers/ServersScreen.kt`。
- **ViewModel / State / Action / Event**：`SETUP/presentation/servers/ServersViewModel.kt`、`SETUP/presentation/servers/ServersState.kt`、`SETUP/presentation/servers/ServersAction.kt`、`SETUP/presentation/servers/ServersEvent.kt`。
- **State 字段**：`servers`。
- **主要方法 / Action**：`loadServers()`、`onAction()`；`OnServerClick`、`OnAddressClick`、`NavigateToAddresses`、`DeleteServer`、`OnAddClick`、`OnBackClick`。
- **入口**：启动路由、首页「管理服务器」、设置页、用户列表页「切换服务器」。

### AddServerScreen — 添加服务器

- **职责**：自动发现局域网服务器，或手动输入地址连接。
- **路由**：`AddServerRoute`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/setup/addserver/AddServerScreen.kt`。
- **ViewModel / State / Action / Event**：`SETUP/presentation/addserver/AddServerViewModel.kt`、`SETUP/presentation/addserver/AddServerState.kt`、`SETUP/presentation/addserver/AddServerAction.kt`、`SETUP/presentation/addserver/AddServerEvent.kt`。
- **State 字段**：`isLoading`、`discoveredServers`、`error`。
- **主要方法 / Action**：`discoverServers()`、`onAction()`；`OnConnectClick`、`OnBackClick`。
- **入口**：服务器列表页的「+」按钮（`PHONE/NavigationRoot.kt`）。

### ServerAddressesScreen — 服务器地址管理

- **职责**：管理某个服务器的地址列表，支持增删；禁止删除当前地址，新增时校验 `systemId`。
- **路由**：`ServerAddressesRoute(serverId)`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/setup/addresses/ServerAddressesScreen.kt`。
- **ViewModel / State / Action**：`SETUP/presentation/addresses/ServerAddressesViewModel.kt`、`SETUP/presentation/addresses/ServerAddressesState.kt`、`SETUP/presentation/addresses/ServerAddressesAction.kt`。
- **State 字段**：`addresses`。
- **主要方法 / Action**：`loadAddresses(serverId)`、`onAction()`；`OnServerClick`、`AddAddress`、`DeleteAddress`、`OnBackClick`。
- **入口**：服务器列表页触发 `NavigateToAddresses`（`PHONE/NavigationRoot.kt`）。

### UsersScreen — 用户列表

- **职责**：展示服务器已登录用户与公共用户，支持登录、删除用户、切换服务器、新增登录。
- **路由**：`UsersRoute`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/setup/users/UsersScreen.kt`。
- **ViewModel / State / Action / Event**：`SETUP/presentation/users/UsersViewModel.kt`、`SETUP/presentation/users/UsersState.kt`、`SETUP/presentation/users/UsersAction.kt`、`SETUP/presentation/users/UsersEvent.kt`。
- **State 字段**：`users`、`publicUsers`、`serverName`。
- **主要方法 / Action**：`loadUsers()`、`onAction()`；`OnUserClick`、`OnPublicUserClick`、`OnDeleteUser`、`OnChangeServerClick`、`OnAddClick`、`OnBackClick`。
- **入口**：启动路由、设置页、添加服务器成功、登录页「切换服务器」。

### LoginScreen — 登录

- **职责**：用户名 / 密码登录或 Quick Connect 快速连接，展示服务器名与登录免责声明。
- **路由**：`LoginRoute(username: String? = null)`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/setup/login/LoginScreen.kt`。
- **ViewModel / State / Action / Event**：`SETUP/presentation/login/LoginViewModel.kt`、`SETUP/presentation/login/LoginState.kt`、`SETUP/presentation/login/LoginAction.kt`、`SETUP/presentation/login/LoginEvent.kt`。
- **State 字段**：`serverName`、`disclaimer`、`quickConnectEnabled`、`quickConnectCode`、`isLoading`、`error`。
- **主要方法 / Action**：`loadServer()`、`loadDisclaimer()`、`loadQuickConnectEnabled()`、`onAction()`；`OnLoginClick`、`OnChangeServerClick`、`OnQuickConnectClick`、`OnBackClick`。
- **入口**：用户列表页「新增登录」或点击公共用户（`PHONE/NavigationRoot.kt`）。

---

## 播放页面（player）

### PlayerActivity — 视频播放

- **职责**：视频播放界面（传统 View + Media3 `PlayerView` / ExoPlayer），展示标题、章节，提供跳过片头 / 片尾、PiP、手势，以及右上角菜单（字幕 / 倍速 / 音轨 / 截取当前画面设为横屏封面）。
- **路由**：**无 NavHost 路由**，为独立 `Activity`（`class PlayerActivity : BasePlayerActivity()`）。
- **Screen**：`PHONE/PlayerActivity.kt`；其中 `override val viewModel: PlayerViewModel by viewModels()`。菜单里的「截取当前画面设为横屏封面」由 `PlayerActivity.captureBackdrop()` 先经 `PlayerViewModel.hasBackdrop()` 确认服务器上没有 `BACKDROP`，再截帧上传（离线模式下不显示该菜单项）；截帧工具为 `PHONE/utils/VideoFrameCapture.kt`（`SurfaceView.captureFrameAsJpeg()`，PixelCopy + 按视频显示比例居中裁掉黑边 + JPEG 压缩）。
- **ViewModel**：`PLAYER/presentation/PlayerViewModel.kt`。
- **State 字段**（`UiState`，`PlayerViewModel.kt`）：`currentItemTitle`、`currentSegment`、`currentSkipButtonStringRes`、`currentTrickplay`、`currentChapters`、`fileLoaded`；事件流 `eventsChannelFlow`（`PlayerEvents`）。
- **主要方法**：`initializePlayer(itemId, itemKind, startFromBeginning, mediaSourceIndex)`（`mediaSourceIndex` 由详情页「选择播放版本」经 Intent extra `mediaSourceIndex` 传入，为 `null` 时按默认规则选源）、`updatePlaybackProgress()`、`updateCurrentSegment()`、`switchToTrack()`、`selectSpeed()`、`skipSegment()`、`seekToNextChapter()`、`seekToPreviousChapter()`、`isLastChapter()`、`hasBackdrop(itemId)`、`setBackdrop(itemId, imageBytes)`；属性 `currentItemId`（当前播放条目的服务器 id，未开始播放时为 `null`）。
- **辅助弹窗**（非页面）：`PHONE/presentation/player/PlayerMenuDialogFragment.kt`（右上角菜单，汇总字幕 / 倍速 / 音轨入口，并把截图设封面的动作回调给宿主 `PlayerActivity`）、`PHONE/presentation/player/SpeedSelectionDialogFragment.kt`、`PHONE/presentation/player/TrackSelectionDialogFragment.kt`，与 `PlayerActivity` 通过构造参数共享同一 `PlayerViewModel`。
- **入口**：各详情页的播放动作（`MovieAction.Play`、`EpisodeAction.Play` 等）。

---

## 搜索组件（非独立页面）

搜索**不是独立页面**，`PHONE/NavigationRoot.kt` 中没有搜索路由。

- **UI 组件**：`PHONE/presentation/film/components/SearchBar.kt`（`FilmSearchBar`）。
- **挂载位置**：媒体库页内（`MediaScreen.kt`；`searchViewModel = hiltViewModel()` 见 `MediaScreen.kt`）。
- **ViewModel / State / Action**：`FILM/presentation/search/SearchViewModel.kt`、`FILM/presentation/search/SearchState.kt`、`FILM/presentation/search/SearchAction.kt`。
- **State 字段**：`items`、`loading`；**Action**：`Search(query)`、`OnItemClick`。
- **触发方式**：`PHONE/NavigationRoot.kt` 的 `searchExpanded` 状态控制搜索栏展开；首页搜索按钮切到 `MediaRoute` 并置 `searchExpanded = true`（`PHONE/NavigationRoot.kt`）。

---

## 页面共性约定

- **MVVM + 单向数据流**：`ViewModel` 暴露 `StateFlow<XxxState>`，Screen 通过 `collectAsStateWithLifecycle()` 观察；用户交互分发 `XxxAction` 给 `onAction()`。
- **State / Action 复用**：`DownloadsViewModel` 与 `FavoritesViewModel` 复用 `FILM/presentation/collection/CollectionState.kt` + `FILM/presentation/collection/CollectionAction.kt`，**没有**独立的 `DownloadsState` / `FavoritesState`。
- **无 ViewModel 的页面**：`WelcomeScreen`（仅 `WelcomeAction`，Screen 内联处理）、`AboutScreen`（纯静态 UI）。
- **无 `onAction` 的 ViewModel**：`DownloadsViewModel`、`FavoritesViewModel`、`PersonViewModel`。
- **导航**：统一使用类型安全路由；`navigateToItem(navController, item)`（`PHONE/NavigationRoot.kt`）按 `FindroidItem` 子类型分发到具体详情页；`safeNavigate` / `safePopBackStack` 仅在生命周期为 `RESUMED` 时执行导航（`PHONE/NavigationRoot.kt`）。
- **离线模式**：由 `LocalOfflineMode` CompositionLocal 控制（`PHONE/presentation/utils/`），离线时隐藏「媒体」标签与仅联网功能。

---

## 变更记录

| 日期 | 变更摘要 |
|---|---|
| 2026-10-06 | 首次创建。覆盖手机端 21 个页面（film 11、settings 3、setup 6、player 1）与 1 个搜索组件。 |
| 2026-10-06 | 电影详情页副标题下方新增文件存放路径（`FilePathText`，`MovieState.filePath` 派生属性），同步 MovieScreen 条目。 |
| 2026-10-06 | 文件路径改为本地 / 远程分离：`FindroidSource.filePath` 拆为 `remoteFilePath`、`localFilePath`，`MovieState` 改为派生 `localFilePath`、`remoteFilePath`，已下载时两行同时展示。 |
| 2026-10-06 | 媒体库详情页排序弹窗新增封面显示模式（`CoverDisplayMode`，竖图 / 横图），新增偏好 `pref_library_cover_mode`、Action `ChangeCoverMode`，`ItemPoster` 增加横竖图互相补位；同步 LibraryScreen 条目。 |
| 2026-10-06 | 媒体库横图封面改用首页 `BANNER_ASPECT_RATIO`，列数按可用宽度自适应（宽屏每行两个）。 |
| 2026-10-06 | 横图封面选取范围扩展：剧集 / 合集 / 媒体库等非电影条目也优先取 `backdrop`（分集仍优先 16:9 剧照）；全文去除行号引用，并新增「不得写行号」的书写与校验要求。 |
| 2026-10-06 | 合集封面补齐：合集列表（`FindroidBoxSet`）用合集内条目的图片兜底（新增 `JellyfinRepositoryImpl` 内的 `getBoxSetCoverImages()`）；媒体库页的合集库入口新增通用图标 `ic_collection`，`ItemPoster` / `ItemCard` 新增 `placeholderIconRes` 参数。 |
| 2026-10-06 | 合集封面补齐健壮性：合封面试探加并发限流与会话内缓存（键含服务器地址），优先选带 `backdrop` 的候选条目；单次试探失败仅记录日志并保留原图，不再影响整个列表加载。 |
| 2026-10-06 | 合集封面补齐收口：`getBoxSetCoverImages()` 从 `JellyfinRepository` 接口移入 `JellyfinRepositoryImpl` 私有方法（离线实现不再需要空实现）；`MediaScreen` 的合集库占位图标策略提取为 `FindroidCollection.placeholderIconRes()`；`ItemPoster` / `ItemCard` 的 `placeholderIconRes` 补 `@DrawableRes`。 |
| 2026-10-06 | `:data` 新增 `FindroidSource.pickPlaybackSource()` 共享选源规则（本地优先，其次列表首个），`PlaylistManager` 与 `MovieViewModel` 共用，详情页视频元数据不再固定取第一个源。 |
| 2026-10-06 | 合集卡片加类型角标：`ItemCard` 在条目为 `FindroidBoxSet` 时于封面右上角显示新增的 `BoxSetBadge`（复用 `ic_collection`），实际在 LibraryScreen 合集库列表生效（`CollectionGrid` 的 section 只含电影 / 剧集 / 分集，不出现合集，故不受影响）；新增字符串 `collection`（含 zh-rCN / zh-rTW）。 |
| 2026-10-06 | 文档与注释校正：修正上条对 `CollectionGrid` 的影响描述；`JellyfinRepositoryImpl.boxSetCoverCache` 注释补充「无上限、不主动清理、仅随进程存活」的缓存策略说明。 |
| 2026-10-06 | 电影详情页新增「更多」菜单（最终形态）：按钮行末尾的「更多」按钮（离线模式隐藏）打开 `MoreMenuDialog`，三个入口各自二次确认——「删除封面」→ `DeleteItemImagesDialog`（列出服务器图片，缩略图 + 类型名，默认全选、可逐张取消）、「重置 nfo」→ `ResetMetadataDialog`（文件名取不到时提示标题将被清空）、「删除全部」→ `DeleteItemWithFilesDialog`（确认按钮为错误色）；弹窗切换由 `MovieScreen.kt` 内的私有枚举 `MoreMenuDialogState` 控制（`rememberSaveable` 保存，旋转屏幕不丢失）。数据层新增 `JellyfinRepository.getItemImages` / `deleteItemImages` / `clearItemMetadata` / `deleteItem`（离线实现分别返回空列表 / 抛异常；`clearItemMetadata` 为覆盖式更新且保留 `ProviderIds`，读取条目失败即中止；`getItemImages` 校验服务器地址非空），并新增 `FindroidItemImage` 与 `MovieState.serverFileName`（远程路径首行末段）。`MovieAction` 为 `DeleteItemImages(images)` / `ResetItemMetadata` / `DeleteItemWithFiles`，事件为 `ItemImagesDeleted` / `MetadataReset` / `ItemDeleted` / `ItemImagesDeleteFailed` / `MetadataResetFailed` / `ItemDeleteFailed`（按操作分别提示），图片列表加载失败只在弹窗内提示；「删除全部」成功后返回上一页。 |
| 2026-10-06 | 电影详情页「更多」菜单的「重置 nfo」改为「编辑 nfo」：只读确认弹窗 `ResetMetadataDialog` 替换为可编辑弹窗 `EditItemMetadataDialog`，回填标题、原名、简介、类型、标签、制片公司、制片国家、宣传语、分级、制作年份、首播日期、社区评分共 12 个字段，多值字段用逗号分隔，底部提供「清空」/「确认」，标题为唯一必填项（为空时提示「必填」并禁用确认，数值 / 日期不合法同样禁用）。数据层 `JellyfinRepository.clearItemMetadata` 替换为 `getItemMetadata` / `updateItemMetadata`：写回时先按 `METADATA_EDIT_FIELDS` 整份读回条目再只覆盖表单字段，演职员、外部刮削 ID、锁定状态等原样保留（`ItemFields.SETTINGS` 取回 `LockData` / `ForcedSortName` / `PreferredMetadata*`，避免被置空或解锁），离线实现分别返回空表单 / 抛异常；新增领域模型 `ItemMetadataEdit`。`MovieState` 移除已无用的 `serverFileName`、新增 `itemMetadata` / `isLoadingItemMetadata` / `itemMetadataError`，`MovieViewModel` 新增 `loadItemMetadata()`；`MovieAction.ResetItemMetadata` → `UpdateItemMetadata(metadata)`，事件 `MetadataReset` / `MetadataResetFailed` → `MetadataUpdated` / `MetadataUpdateFailed`；文案 `reset_*` 系列替换为 `edit_metadata_*`（含 zh-rCN / zh-rTW）。 |
| 2026-10-06 | 编辑 nfo 收尾：`MoreMenuDialog` 标题由「删除服务器信息」（`delete_server_info`）改为中性的「更多操作」（新键 `more_menu_title`，含 zh-rCN / zh-rTW），与菜单内新增的「编辑 nfo」一致；写回元数据时不再回传演职员——服务端对 `People` 是「传了才更新」，`updateItemMetadata` 显式传 `people = null` 保留原值，`ItemFields.PEOPLE` 随之从 `METADATA_EDIT_FIELDS` 移除。 |
| 2026-10-06 | 下载页去掉卡片封面右下角的详情入口：删除 `InfoBadge`、`DownloadDetailsDialog` 两个组件，`PHONE/presentation/film/components/ItemCard.kt`、`PHONE/presentation/film/components/CollectionGrid.kt`、`PHONE/presentation/film/DownloadsScreen.kt` 移除 `onDetailsClick` / `onItemDetails` 传参链；下载页点击条目仍进入详情页（`DownloadDetailsDialog` 的文件路径信息与电影页 `FilePathText` 重复，已无用）。 |
| 2026-10-06 | 电影详情页文件路径图标按来源区分：`FilePathText` 新增 `isRemote` 参数，服务端路径改用新增的云图标 `ic_cloud`（`:core` drawable，描边风格对齐 `ic_folder`），本地已下载路径仍用文件夹图标。 |
| 2026-10-06 | 电影详情页支持「选择播放版本」：`MovieState` 新增 `playbackSources`（由 `getMediaSources()` 获取，保证与播放端顺序一致），`localFilePath` / `remoteFilePath` 改由它派生并恢复列出全部来源；新增 `PHONE/presentation/film/components/PlaybackSourceDialog.kt`，来源多于一个时点播放先弹窗选择，选中索引经 Intent extra `mediaSourceIndex` 传给 `PlayerActivity` → `PlayerViewModel.initializePlayer(..., mediaSourceIndex)` → `PlaylistManager.getInitialItem`；新增字符串 `select_playback_source`（含 zh-rCN）。 |
| 2026-10-07 | 媒体库详情页排序菜单新增「随机」：`SortBy` 增加 `RANDOM`（服务端 `Random`）与 `isSelectable` 标记，菜单项改由 `SortBy.selectableValues` 提供并排除内部项 `SERIES_DATE_PLAYED`，`sort_by_options` 补齐第 7 项；随机排序由 `ItemsPagingSource` 客户端实现——服务端随机排序一次取回一批（上限 500）后本地打乱并本地分页，避免偏移分页重复。 |
| 2026-10-07 | 随机排序细节收敛：选中随机时 `PHONE/presentation/film/components/SortByDialog.kt` 禁用排序顺序按钮；`ItemsPagingSource` 随机分支复用 `emittedItemIds` 去重兜底；`SortBy.selectableValues` 改为惰性求值。 |
| 2026-10-07 | 修复播放器截图设封面返回 500：Jellyfin 服务端的图片上传接口（`ImageController.SetItemImage`）会对请求体做 Base64 解码（`CryptoStream` + `FromBase64Transform`），SDK 自动生成的 `setItemImage` 发的是原始二进制因而解码失败；`JellyfinRepositoryImpl.setItemImage` 改为发送 Base64 文本（`Content-Type` 保持 `image/jpeg`，服务端据此落盘为 `.jpg`）。 |
| 2026-10-07 | 播放器右上角改为菜单入口：布局 `exo_main_controls.xml` 的字幕 / 倍速 / 音轨三个按钮合并为 `btn_menu`，点击弹出新增的 `PHONE/presentation/player/PlayerMenuDialogFragment.kt`，由它再打开原有的 `TrackSelectionDialogFragment` / `SpeedSelectionDialogFragment`；菜单新增「截取当前画面设为横屏封面」——`PlayerActivity.captureBackdrop()` 先经 `PlayerViewModel.hasBackdrop()` 确认服务器上没有 `BACKDROP`（已有则提示先删除、不上传），再用 `PHONE/utils/VideoFrameCapture.kt` 的 `SurfaceView.captureFrameAsJpeg()`（PixelCopy + 等比缩放为 JPEG）截帧，经 `PlayerViewModel.setBackdrop()` → `JellyfinRepository.setItemImage()` 上传；`:data` 新增 `setItemImage`（离线实现抛异常），新增字符串 `player_controls_menu`、`player_menu_capture_backdrop`、`player_backdrop_already_exists`、`player_backdrop_set_success`、`player_backdrop_set_failed`、`player_backdrop_frame_unavailable`（含 zh-rCN）。 |
| 2026-10-07 | 截图设封面收尾：离线模式（`appPreferences.offlineMode`）下 `PlayerMenuDialogFragment` 不再展示截图入口；截图改用 `PlayerActivity.displayAspectRatio()`（读 `player.videoSize`，折算 90/270 度旋转）在 `VideoFrameCapture.kt` 内居中裁掉 fit 缩放的黑边后再缩放压缩；`captureBackdrop()` / `uploadBackdrop()` 单独放行 `CancellationException`，避免 Activity 销毁时误报失败。 |
