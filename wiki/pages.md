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

**最后更新：2026-11-13**

---

## 目录

- [维护与更新机制](#维护与更新机制)
- [页面总览](#页面总览)
- [影视页面（film）](#影视页面film)
- [JavDB 过盾页面（javdb）](#javdb-过盾页面javdb)
- [设置页面（settings）](#设置页面settings)
- [引导页面（setup）](#引导页面setup)
- [播放页面（player）](#播放页面player)
- [搜索组件（非独立页面）](#搜索组件非独立页面)
- [页面共性约定](#页面共性约定)

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
3. 更新开头的「最后更新」日期。
4. 检查全文无行号引用、无失效路径（见下方「校验方法」）。

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

无输出即表示文档中的所有路径均有效。此外，`PHONE/NavigationRoot.kt` 中 `composable<...>` 注册块当前为 21 个（另有 1 个播放 Activity）；下表按「页面」列出，同一路由承载多个页面时会有多行（如 `MetadataBrowseRoute` 对应 3 个列表页），因此行数可以多于注册块数。

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
| 主标签 | 设置 | `SettingsRoute` | `PHONE/presentation/settings/SettingsScreen.kt` | `SETTINGS/presentation/settings/SettingsViewModel.kt` |
| 内容 | 下载 | `DownloadsRoute` | `PHONE/presentation/film/DownloadsScreen.kt` | `FILM/presentation/downloads/DownloadsViewModel.kt` |
| 内容 | 媒体库详情 | `LibraryRoute` | `PHONE/presentation/film/LibraryScreen.kt` | `FILM/presentation/library/LibraryViewModel.kt` |
| 内容 | 合集详情 | `CollectionRoute` | `PHONE/presentation/film/CollectionScreen.kt` | `FILM/presentation/collection/CollectionViewModel.kt` |
| 内容 | 收藏 | `FavoritesRoute` | `PHONE/presentation/film/FavoritesScreen.kt` | `FILM/presentation/favorites/FavoritesViewModel.kt` |
| 内容 | 电影详情 | `MovieRoute` | `PHONE/presentation/film/MovieScreen.kt` | `FILM/presentation/movie/MovieViewModel.kt` |
| 内容 | 剧集详情 | `ShowRoute` | `PHONE/presentation/film/ShowScreen.kt` | `FILM/presentation/show/ShowViewModel.kt` |
| 内容 | 季详情 | `SeasonRoute` | `PHONE/presentation/film/SeasonScreen.kt` | `FILM/presentation/season/SeasonViewModel.kt` |
| 内容 | 分集详情 | `EpisodeRoute` | `PHONE/presentation/film/EpisodeScreen.kt` | `FILM/presentation/episode/EpisodeViewModel.kt` |
| 内容 | 类别 / 制片公司列表 | `MetadataBrowseRoute` | `PHONE/presentation/film/NamedItemListScreen.kt` | `FILM/presentation/nameditem/NamedItemListViewModel.kt` |
| 内容 | 人物列表（演员 / 导演 / 编剧） | `MetadataBrowseRoute` | `PHONE/presentation/film/PersonListScreen.kt` | `FILM/presentation/personlist/PersonListViewModel.kt` |
| 内容 | 标签 / 分级 / 年份列表 | `MetadataBrowseRoute` | `PHONE/presentation/film/FilterValueListScreen.kt` | `FILM/presentation/filtervalue/FilterValueListViewModel.kt` |
| 内容 | 元数据筛选结果 | `ItemFilterRoute` | `PHONE/presentation/film/ItemFilterScreen.kt` | `FILM/presentation/itemfilter/ItemFilterViewModel.kt` |
| 设置 | 设置 | `SettingsRoute` | `PHONE/presentation/settings/SettingsScreen.kt` | `SETTINGS/presentation/settings/SettingsViewModel.kt` |
| 设置 | 用户与服务器 | `AccountsRoute` | `PHONE/presentation/settings/AccountsScreen.kt` | `SETUP/presentation/accounts/AccountsViewModel.kt` |
| 设置 | 缓存 | `CacheRoute` | `PHONE/presentation/settings/cache/CacheScreen.kt` | `CORE/presentation/cache/CacheViewModel.kt` |
| 设置 | 文件编辑 | `SettingsFileEditRoute` | `PHONE/presentation/settings/SettingsFileEditScreen.kt` | `SETTINGS/presentation/settings/SettingsFileEditViewModel.kt` |
| 设置 | 关于 | `AboutRoute` | `PHONE/presentation/settings/AboutScreen.kt` | 无 |
| 工具 | JavDB 过盾 | `JavDbUnblockRoute` | `PHONE/presentation/javdb/JavDbUnblockScreen.kt` | `PHONE/presentation/javdb/JavDbUnblockViewModel.kt` |
| 引导 | 欢迎 | `WelcomeRoute` | `PHONE/presentation/setup/welcome/WelcomeScreen.kt` | 无 |
| 引导 | 服务器列表 | `ServersRoute` | `PHONE/presentation/setup/servers/ServersScreen.kt` | `SETUP/presentation/servers/ServersViewModel.kt` |
| 引导 | 添加服务器 | `AddServerRoute` | `PHONE/presentation/setup/addserver/AddServerScreen.kt` | `SETUP/presentation/addserver/AddServerViewModel.kt` |
| 引导 | 服务器地址 | `ServerAddressesRoute` | `PHONE/presentation/setup/addresses/ServerAddressesScreen.kt` | `SETUP/presentation/addresses/ServerAddressesViewModel.kt` |
| 引导 | 用户列表 | `UsersRoute` | `PHONE/presentation/setup/users/UsersScreen.kt` | `SETUP/presentation/users/UsersViewModel.kt` |
| 引导 | 登录 | `LoginRoute` | `PHONE/presentation/setup/login/LoginScreen.kt` | `SETUP/presentation/login/LoginViewModel.kt` |
| 播放 | 视频播放 | 无（Activity，非 NavHost 路由） | `PHONE/PlayerActivity.kt` | `PLAYER/presentation/PlayerViewModel.kt` |

**底部导航标签**（`PHONE/NavigationRoot.kt`）：联网时为首页 `HomeRoute`、媒体 `MediaRoute`、设置 `SettingsRoute`；离线模式下（`LocalOfflineMode == true`）首页与媒体页都要读服务器，底栏改为「下载」`DownloadsRoute` + 「设置」`SettingsRoute` 两个标签。联网时下载页的入口在媒体页的「已下载」入口（`PHONE/presentation/film/components/MediaEntryCard.kt`，与「我喜欢」并排平分一行宽度）。

**启动路由**（`PHONE/NavigationRoot.kt`，已登录时进入当前模式的落地标签 `landingTab.route`：联网为 `HomeRoute`、离线为 `DownloadsRoute`）：

```kotlin
hasServers && hasCurrentServer && hasCurrentUser -> landingTab.route
hasServers && hasCurrentServer -> UsersRoute
hasServers -> ServersRoute
else -> WelcomeRoute
```

---

## 影视页面（film）

### HomeScreen — 首页

- **职责**：聚合首页。顶部为 `HomeHeader`（服务器名与服务器切换入口，名称直接显示，不执行尺寸展开动画）；下方按 section 渲染推荐轮播、继续观看、接下来、各媒体库最新，加载失败时在内容区提供重试入口。
- **路由**：`HomeRoute`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/film/HomeScreen.kt`；section 组装在 `HomeScreen.kt`。
- **ViewModel / State / Action**：`FILM/presentation/home/HomeViewModel.kt`、`FILM/presentation/home/HomeState.kt`、`FILM/presentation/home/HomeAction.kt`。
- **State 字段**：`server`、`suggestionsSection`、`resumeSection`、`nextUpSection`、`views`、`isLoading`、`error`。
- **主要方法 / Action**：`loadData(refreshSuggestions)`；`OnItemClick`（可带共享元素来源 `heroKey`）、`OnLibraryClick`、`OnRetryClick`、`OnRefresh`、`OnManageServers`。
- **数据来源**：`getSuggestions()`、`getResumeItems()`、`getNextUp()`、`getUserViews()` + `getLatestMedia(view.id)`（`HomeViewModel.kt`）；各 section 有内容时自动展示，为空则不渲染，不再提供单独开关。banner（推荐轮播）只在首次进入与下拉刷新 / 切换服务器（`OnRefresh`）时拉取，从详情页返回首页不重新请求，避免轮播被重建；返回首页若共享元素转场仍在执行，等转场结束后再刷新其它分区，继续观看条目的增删与排序变化使用 LazyRow 条目动画。
- **入口**：启动路由 / 底部「首页」标签 / 各详情页的 `navigateHome`。离线模式下页面不可达（首页数据全来自服务器），落地页与 `navigateHome` 的目标改为下载页。

### MediaScreen — 媒体库总览

- **职责**：展示全部媒体库网格；顶部内嵌搜索栏（`FilmSearchBar`），网格末尾依次是「我喜欢」与「已下载」入口（两卡并排、各占一半行宽，组件为 `MediaEntryCard`）与浏览入口区块。媒体库卡片为横图（`BANNER_ASPECT_RATIO`），列数固定为手机 2 列、折叠屏展开（宽度 ≥ 600dp）4 列——横图卡片列数翻倍时宽度与高度都减半；合集库（`CollectionType.BoxSets`）服务器上不提供封面，卡片改用通用图标 `CoreR.drawable.ic_collection` 占位。
- **路由**：`MediaRoute`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/film/MediaScreen.kt`；搜索栏挂载于 `MediaScreen.kt`。
- **ViewModel / State / Action**：`FILM/presentation/media/MediaViewModel.kt`、`FILM/presentation/media/MediaState.kt`、`FILM/presentation/media/MediaAction.kt`。
- **State 字段**：`libraries`、`isLoading`、`error`。
- **主要方法 / Action**：`loadData()`；`OnItemClick`、`OnFavoritesClick`、`OnDownloadsClick`、`OnRetryClick`。
- **入口**：底部「媒体」标签（离线模式不显示）。
- **浏览入口**：网格末尾追加 `PHONE/presentation/film/components/MediaBrowseSection.kt`——按 nfo 已入库的维度（类别 / 标签 / 分级 / 年份 / 演员 / 导演 / 编剧 / 制片公司，枚举为 `data/src/main/java/dev/jdtech/jellyfin/models/MetadataFacet.kt`）列出入口，固定每行两个、随列表一起滚动，点击进入 `MetadataBrowseRoute`。

### DownloadsScreen — 下载

- **职责**：展示本地已下载条目，分组网格呈现；空列表时提示「无下载」，加载失败可重试；点击条目时为详情路由设置 `useLocalData = true`，无需开启全局离线模式即可读取本地详情。右上角「保存到下载文件夹」支持选择已完成的电影、分集及不同视频版本，复制到手机公共 `Download/JellyDroid` 目录，显示所选文件大小、后台保存进度及成功 / 失败 / 取消数量。
- **路由**：`DownloadsRoute`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/film/DownloadsScreen.kt`。
- **ViewModel / State / Action**：`FILM/presentation/downloads/DownloadsViewModel.kt`、`FILM/presentation/downloads/DownloadsState.kt`、`FILM/presentation/downloads/DownloadsAction.kt`。
- **State 字段**：`sections`、`isLoading`、`error`；保存相关状态为 `showExportDialog`、`exportItems`、`selectedSourceIds`、`isLoadingExportItems`、`isStartingExport`、`exportState`、`exportError`。
- **主要方法 / Action**：`loadItems()`（内部调用 `repository.getDownloads()`）、`onAction()`；`OnItemClick`、`OpenExportDialog`、`CloseExportDialog`、`ToggleExportItem`、`ToggleSelectAll`、`ExportSelected`、`CancelExport`、`StoragePermissionDenied`、`Retry`。
- **保存到公共目录**：弹窗为 `PHONE/presentation/film/components/DownloadExportDialog.kt`；业务接口与实现为 `core/src/main/java/dev/jdtech/jellyfin/utils/DownloadExporter.kt`、`core/src/main/java/dev/jdtech/jellyfin/utils/DownloadExporterImpl.kt`，从当前服务器的离线数据库读取已完成且文件存在的来源，不请求服务器。`core/src/main/java/dev/jdtech/jellyfin/work/DownloadExportWorker.kt` 使用 WorkManager 串行复制视频；Android 10 及以上通过 MediaStore 下载集合写入，Android 9 由 Screen 请求存储权限后写入公共目录。保留原始文件名与扩展名，同名自动另存，不覆盖既有文件；只复制视频文件，原有离线文件与播放记录不变，公共副本卸载后仍保留。关闭弹窗或离开页面后继续保存，可重新打开查看结果；停止保存只取消剩余任务，保留已完成的副本，未完成副本会清理。进程中断后，任务重跑会先按任务 id 清理上次未发布的副本，再检查源文件与剩余空间并重新复制；清理失败会显示保存失败，避免继续创建残留副本。
- **详情数据来源**：`FILM/presentation/di/DetailRepositoryModule.kt` 根据路由保存在 `SavedStateHandle` 中的 `useLocalData` 参数，为电影 / 剧集 / 季 / 分集的 ViewModel 注入带 `FILM/presentation/di/DetailRepository.kt` 限定符的仓库；下载入口使用既有 `JellyfinRepositoryOfflineImpl`，元数据、播放源、下一集和季 / 分集列表均从 Room 读取。该参数沿剧集 → 季 → 分集及返回所属季 / 剧集的路径传递，进程重建后保留；本地详情范围内的 `LocalOfflineMode` 同时启用，隐藏服务器编辑入口，删除下载后返回上一页。播放时通过 Intent extra `useLocalData` 继续传入播放器，播放列表与进度记录同样使用本地仓库。其他入口仍按全局离线设置选择仓库。
- **入口**：媒体页的「已下载」入口（`PHONE/presentation/film/components/MediaEntryCard.kt`，与「我喜欢」并排平分一行宽度）；离线模式下本页同时是底栏「下载」标签的根页面、启动落地页与各详情页 `navigateHome` 的目标，此时（`DownloadsScreen` 的 `isTabRoot`）列表底部额外留出底栏高度，避免最后一行被浮起的底栏压住（`CollectionGrid` 的 `bottomContentPadding`）。

### LibraryScreen — 媒体库详情

- **职责**：单个媒体库的内容列表，支持分页加载、排序切换（含随机排序）与封面显示模式切换（右上角 `SortByDialog` 同时承载两项设置），条目点击进入详情。排序与封面模式偏好与「收藏」「合集详情」页共用，任一处改动都会在下次进入其他页面时生效。
- **路由**：`LibraryRoute(libraryId, libraryName, libraryType)`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/film/LibraryScreen.kt`；设置弹窗为 `PHONE/presentation/film/components/SortByDialog.kt`。
- **ViewModel / State / Action**：`FILM/presentation/library/LibraryViewModel.kt`、`FILM/presentation/library/LibraryState.kt`、`FILM/presentation/library/LibraryAction.kt`。
- **State 字段**：`items`（PagingData Flow）、`sortBy`、`sortOrder`、`coverMode`（`CoverDisplayMode`，默认竖图）、`isLoading`、`error`。
- **加载生命周期**：由 ViewModel 的 `setup(parentId, libraryType)` 首次触发分页加载，同一实例重复 setup 为幂等；进程重建后新实例重新加载，不依赖界面保存的首次加载标记。
- **主要方法 / Action**：`setup(parentId, libraryType)`、`loadItems()`、`onAction()`；`OnItemClick`、`OnBackClick`、`ChangeSorting`、`ChangeCoverMode`。
- **封面显示模式**：偏好存 `AppPreferences.libraryCoverMode`（`pref_library_cover_mode`）。横图走 `Direction.HORIZONTAL`，优先取 `backdrop`（电影 / 剧集 / 合集 / 媒体库；分集取 16:9 剧照 `primary`），宽高比沿用首页 `BANNER_ASPECT_RATIO`，列数按可用宽度自适应（窄屏一列、宽屏两列）；竖图走 `Direction.VERTICAL` 取 `primary`、多列网格；某类图片缺失时由 `ItemPoster` 用另一类临时补位。
- **合集列表封面**：合集（`FindroidBoxSet`）在服务器上没有图片，由 `JellyfinRepositoryImpl` 的私有方法 `getBoxSetCoverImages()` 取合集内条目的图片兜底（并行但限并发、按服务器地址 + 合集 id 在进程内缓存，`getItems()` 内调用，单次试探失败不影响列表加载）；该方法不进 `JellyfinRepository` 接口（离线模式没有合集数据）。合集卡片（`ItemCard`，条目为 `FindroidBoxSet`）封面右上角叠加 `BoxSetBadge`（复用 `ic_collection`）与普通影片区分。
- **随机排序**：排序菜单的「随机」由 `data/src/main/java/dev/jdtech/jellyfin/repository/ItemsPagingSource.kt` 在客户端实现——服务端随机排序一次取回一批（上限 `RANDOM_SNAPSHOT_SIZE = 500`）后本地打乱，作为本次会话的固定顺序、之后只做本地切片，避免偏移分页被服务端重新洗牌导致重复与遗漏。可选排序项由 `SortBy.selectableValues` 提供（`data/src/main/java/dev/jdtech/jellyfin/models/SortBy.kt`），排除仅供内部使用的 `SERIES_DATE_PLAYED`；随机排序下 `SortByDialog` 禁用排序顺序按钮。
- **入口**：首页媒体库卡片；点击 `FindroidCollection` / `FindroidFolder` 条目（`PHONE/NavigationRoot.kt`）。

### CollectionScreen — 合集详情

- **职责**：合集（BoxSet）详情页，按电影 / 剧集 / 单集分组展示合集内条目；右上角提供排序与封面显示设置，与媒体库列表共用同一套偏好（`AppPreferences.sortBy` / `sortOrder` / `libraryCoverMode`），切换后媒体库列表与收藏页下次进入即生效。
- **路由**：`CollectionRoute(collectionId, collectionName)`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/film/CollectionScreen.kt`；条目网格为 `PHONE/presentation/film/components/CollectionGrid.kt`，设置弹窗为 `PHONE/presentation/film/components/SortByDialog.kt`。
- **ViewModel / State / Action**：`FILM/presentation/collection/CollectionViewModel.kt`、`FILM/presentation/collection/CollectionState.kt`、`FILM/presentation/collection/CollectionAction.kt`；分组逻辑共用 `FILM/presentation/collection/CollectionSections.kt` 的 `buildCollectionSections()`（收藏页与下载页同一实现）。
- **State 字段**：`sections`、`sortBy`、`sortOrder`、`coverMode`、`isLoading`、`error`。
- **主要方法 / Action**：`loadItems(parentId)`（`repository.getItems(parentId, sortBy, sortOrder)`）、`onAction()`；`OnItemClick`、`OnItemDetails`、`OnBackClick`、`ChangeSorting`（更新 State、写入共享偏好并重新取数，失败回滚）、`ChangeCoverMode`（更新 State 并写入共享偏好，不重新取数）。
- **封面显示模式**：首次进入读取共享偏好，因此默认外观与媒体库电影列表一致——横图模式走 `Direction.HORIZONTAL` + `BANNER_ASPECT_RATIO`、列宽按可用宽度自适应，竖图模式保留分集 16:9 剧照、其余条目用竖图。
- **入口**：点击 `FindroidBoxSet` 条目（`PHONE/NavigationRoot.kt`）。

### FavoritesScreen — 收藏

- **职责**：读取收藏条目，按电影 / 剧集 / 单集分组展示；右上角提供排序与封面显示设置，与媒体库列表共用同一套偏好（`AppPreferences.sortBy` / `sortOrder` / `libraryCoverMode`），在此切换后媒体库列表下次进入即生效。
- **路由**：`FavoritesRoute`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/film/FavoritesScreen.kt`；复用 `PHONE/presentation/film/CollectionScreen.kt` 的 `CollectionScreenLayout`（顶栏固定展示设置入口），设置弹窗为 `PHONE/presentation/film/components/SortByDialog.kt`。
- **ViewModel**：`FILM/presentation/favorites/FavoritesViewModel.kt`；分组逻辑复用 `FILM/presentation/collection/CollectionSections.kt` 的 `buildCollectionSections()`。
- **State / Action**：**复用** `FILM/presentation/collection/CollectionState.kt` 与 `FILM/presentation/collection/CollectionAction.kt`（无独立 State/Action）；其中排序与封面模式字段由收藏页与合集页填充。
- **State 字段**：`sections`、`sortBy`、`sortOrder`、`coverMode`、`isLoading`、`error`。
- **主要方法 / Action**：`loadItems()`（`repository.getFavoriteItems(sortBy, sortOrder)`，排序由服务端完成）、`onAction()`；`OnItemClick`、`OnBackClick`（导航由界面处理）、`ChangeSorting`（更新 State、写入共享偏好并重新取数）、`ChangeCoverMode`（更新 State 并写入共享偏好，不重新取数）。
- **封面显示模式**：横图模式（`CollectionGrid.coverMode`）所有条目走 `Direction.HORIZONTAL` + `BANNER_ASPECT_RATIO`、列宽按可用宽度自适应；竖图模式保留分集原有的 16:9 剧照形态，其余条目用竖图。
- **入口**：媒体库页的「收藏」入口（`PHONE/NavigationRoot.kt`）。

### MovieScreen — 电影详情

- **职责**：加载影片元数据 / 视频信息 / 演员，演员长按菜单支持获取头像与复制完整演员名，提供播放、预告、标记已看、收藏、下载、跳转演员、点击类型 / 标签跳到对应筛选结果页，以及「更多」菜单（编辑封面 / 编辑 nfo / 下载字幕 / 删除全部 / 抓取网址），下载字幕弹窗可把已下好的候选带进播放器做同屏对照（带上上次播放的来源下标，避免在另一个版本上开面板），并处理离线模式（离线时类型 / 标签不可点，也不显示「更多」菜单）。信息行的类型 / 标签取值由 `PHONE/presentation/film/components/InfoText.kt` 渲染，点击经 `MovieAction.NavigateToGenre` / `NavigateToTag` → `PHONE/NavigationRoot.kt` 的 `navigateToMetadataFacet` 跳转 `ItemFilterRoute`。
- **加载错误**：加载失败停止等待动画，使用 `PHONE/presentation/film/components/DetailLoadError.kt` 显示错误详情和重试入口；重试调用本页原有 load 方法，已有内容继续保留，返回操作可用。
- **路由**：`MovieRoute(movieId, heroKey?, useLocalData = false)`，定义并注册于 `PHONE/NavigationRoot.kt`；`heroKey` 记录点击来源，普通列表为空时使用默认卡片 key；下载入口携带 `useLocalData = true` 读取本地详情与播放源。
- **进场动画**：列表封面与详情页头部之间是共享元素 morph（普通卡片 key 为 `movie-hero-<movieId>`，首页 Banner / 继续观看 / 接下来 / 最新添加分别使用带来源的 key，避免同一电影同时出现在多个分区时互相抢占；经 `PHONE/presentation/utils/SharedTransition.kt` 的 `heroSharedBounds` 建立，两个转场作用域由 `PHONE/NavigationRoot.kt` 的 `SharedTransitionNavHost` / `SharedElementHost` 经 CompositionLocal 下发）；加载态为居中转圈，内容区块等进场转场（morph）结束后才组合并让标题 / 信息行 / 按钮栏 / 简介 / 演员行依次入场（`AppearingSection`），避免转场中途组合整页内容抢帧卡顿；详情页进入为淡入 + 轻微放大、返回反向收缩，转场参数在 `PHONE/NavigationRoot.kt`（全局 token 镜像在 `PHONE/presentation/theme/Motion.kt`）。
- **评论区（外网短评）**：最下方为 `MovieCommentsSection.kt`，支持加载、失败重试、JavDB 登录态提示。数据访问与磁盘缓存位于 `CORE/comment/`；开关与「评论使用代理」来自设置页「下载与网络 → 评论」，本地代理来自同页独立的「代理」组，缓存空间由「缓存」统一管理。访客可见条数受站点账号权益限制，评论失败不影响详情页其它内容。失败原因是被站点拦截（Cloudflare，`ScraperException.SiteBlocked`，State 字段 `isCommentsSiteBlocked`）时额外提供「打开网页过盾」按钮，经 `NavigationRoot.kt` 的 `navigateToUnblock` 跳转 `JavDbUnblockRoute`。
- **Screen**：`PHONE/presentation/film/MovieScreen.kt`；弹窗与小组件主要在 `PHONE/presentation/film/components/` 下，跨域共用的「抓取网址」弹窗在 `PHONE/presentation/components/`，代理连通性测试组件在 `PHONE/presentation/proxy/`：
  - `ItemPlayButton.kt`：封面上的悬浮播放按钮。未滚动时浮在封面中央并跟随内容一起滚动，点击直接播放（`MovieAction.Play(startFromBeginning = false)`，与按钮栏的播放一致）；封面滑出超过一半后收起，用弹簧动画飞到顶栏「回到首页」按钮后方（尺寸与收起锚点按 `ItemTopBar` 的按钮对齐）。悬浮层铺满全屏但不消费触摸，只有按钮圆形区域可点，`headerHeightPx` 由 `MovieScreen.kt` 测量封面后传入。
  - `FilePathText.kt`：副标题下方的文件路径，本地优先于服务器、各自去重拼接、可点击展开；服务端路径用 `ic_cloud`、本地已下载用 `ic_folder`。
  - `PlaybackSourceDialog.kt`：条目有多个来源（被识别成多个版本）时点播放先选版本，选中索引经 Intent extra `mediaSourceIndex` 交给播放器。
  - `MoreMenuDialog.kt`（离线模式不显示）提供「编辑封面」「编辑 nfo」「下载字幕」三个入口；「删除全部」不放进菜单列表，而是用错误色置于标题行右上角（低频破坏性操作统一放右上角），点击后由 `DeleteItemWithFilesDialog.kt` 再确认（连服务器视频文件一起删，确认按钮为错误色，提示中「视频文件」用错误色强调）。按钮行左下角是「抓取网址」配置入口（打开 `PHONE/presentation/components/ScraperUrlsDialog.kt`，设置页「下载与网络」的「抓取网址」独立卡片也有同一入口）。弹窗切换由 `MovieScreen.kt` 内的私有枚举 `MoreMenuDialogState` 控制（`rememberSaveable` 保存，旋转不丢），表单数据在弹窗打开时加载，结果以 Toast 提示，「删除全部」成功后返回上一页。
  - `PHONE/presentation/components/ScraperUrlsDialog.kt`：「抓取网址」弹窗，详情页与设置页共用；传入的网址 map（`MovieState.scrapeSiteUrls` / `SettingsState.scrapeSiteUrls`）来自 `SETTINGS/domain/ScrapeSiteUrlsProvider.kt`（实现见 `CORE/scraper/ScraperUrlProviderImpl.kt`，运行时配置优先、未配置回退打包配置、字幕站取默认地址），因此 debug 包打进 BuildConfig 的地址打开弹窗即可见。按 key 顺序为每个站点渲染一个输入框；站点清单以 `CORE/scraper/ScraperSite.kt` 的枚举为唯一来源、字幕站附在末尾，设置页与详情页共用同一份，站点 key（`ScraperSite.key`：jav321 / javbus / javdb，以及字幕站 `subtitlecat`）作为标签与存储 key，保存分别经 `MovieAction.SaveScrapeUrls` / `SettingsAction.SaveScrapeUrls` 落到 `AppPreferences.scrapeSiteUrl(key)`；字幕站偏好默认值已填 `https://subtitlecat.com`，打开弹窗即可见、无需手动填写，可改成镜像域名（须与官方站路径结构一致），`CORE/subtitle/SubtitlecatScraper.kt` 拼搜索 URL 时读的就是它（`AppPreferences.subtitleSiteBaseUrl()`）；带防呆提示——网址归一化后解析不出 host、不是 http(s) 站点根地址（带其它 scheme 或路径 / 查询参数）、或 nfo 站点的 host 不含对应站点 key（可能贴错站点）时显示「可能不匹配」，但**不阻止保存**（字幕站不做 host 含 key 校验，填镜像域名不会误报；校验只针对用户本次输入，已回填的打包地址不参与，避免打开就一片误报）；防呆与保存共用同一套 scheme 补全规则，避免提示通过却把 URL 拼坏，保存时没写 http / https 的按 https 处理。nfo 站点留空表示清除自定义网址（回退到打包配置；release / staging 的打包配置为空，站点即被跳过），字幕站留空表示恢复内置默认地址。nfo 站点的实际取址逻辑在 `CORE/scraper/ScraperUrlProvider.kt` + `ScraperUrlProviderImpl.kt`（运行时配置优先于打包配置），文字 / 封面 / 头像抓取共用；字幕站不经过它，直接在 `CORE/subtitle/SubtitlecatScraper.kt` 里读偏好。
  - `EditItemImagesDialog.kt`：封面编辑弹窗。上面用缩略图网格列出服务器上「当前服务器使用」的图片（带类型名与同类序号，点击任意一张可全屏查看），服务器上没有任何图片时该区块留空；右上角「清理」二次确认后删光服务器上的图片；左下角「抓取」按番号抓横图 / 竖图，右下角「关闭」（关闭会中断进行中的抓取并丢掉已抓到的图片）。抓取结果不另开弹窗，直接列在同一片网格里：配了地址的站点逐行展示，失败的给出原因（单站失败不影响其它站点），成功的列出抓到的横图 / 竖图（可全屏查看）并在行末给一个「上传」按钮（上传前先删服务器上同类型的旧图，再写入新图），另外在单张图片的全屏查看界面底部也有「上传」，只传当前查看的这一张，方便横图 / 竖图分开传；没有站点配置地址时给出提示并禁用「抓取」。
  - `ImageThumbnail.kt`：可全屏查看的图片缩略图（点击打开 `ImageViewerDialog.kt`）与图片类型文案，供「当前服务器使用」与抓取结果共用；可选地把「单张上传」入口透传给全屏查看界面。
  - `ImageViewerDialog.kt`：全屏查看单张图片，模型同时接受服务器图片地址与本地抓取图片字节；点击关闭，双指缩放、放大后可拖动；调用方给出上传回调时，底部多一个「上传」按钮（只传当前这一张，上传中换成转圈），用于抓取结果里的单张图片。
  - `EditItemMetadataDialog.kt`：编辑 nfo 字段、逐站点并发抓取、切换来源与翻译；支持整份或逐字段恢复服务器原值。**长期不变式：抓取结果的空字段不得覆盖服务器现有内容**（合并见 `MovieViewModel.mergeWithCurrent()`）。演员 / 导演按表单整体替换，编剧等其它人员保留；保存前校验标题、数值与日期。
  - `ScrapeActorImageDialog.kt`：「获取头像」弹窗，与人物列表页共用 `ActorImageScrapeViewModel`；演员长按菜单另有「复制演员名」。抓取支持并发搜索、逐站点展示结果、代理设置与取消，上传头像写入 Person 条目并覆盖原头像，需要服务器管理员权限。具体站点规则见 `CORE/scraper/ActorImageScraperImpl.kt`，站点不匹配或占位图不得上传。
  - `TranslateSettingsDialog.kt`：读写本机 OpenAI 兼容接口设置，可选翻译标题、简介、分类 / 标签及抓取后自动翻译；「翻译现有内容」只处理当前表单。实现位于 `CORE/translator/`：标题 / 简介由模型翻译，分类 / 标签优先使用内置词典与缓存、模型兜底；部分失败保留已成功的译文与原文，不覆盖用户未确认的服务器数据。
  - `ScrapeKeywordDialog.kt` / `ScrapeProgressDialog.kt`：nfo 抓取关键词与进度入口，预填识别出的番号，支持代理、逐站点结果和取消；取消后仍保留已成功结果。封面抓取不走此进度弹窗。代理输入弹窗 `ScrapeProxyDialog.kt`（详情页、字幕下载、演员头像共用）与设置页「本地代理设置」弹窗都带「测试」按钮：测的是输入框里尚未保存的地址，解析规则与抓取链路共用 `CORE/scraper/ScraperProxy.kt`，由 `CORE/scraper/ProxyConnectionTester.kt` 经抓取专用 OkHttp 客户端探测公网连通性，结果（耗时 / 格式错误 / 失败原因）由 `PHONE/presentation/proxy/ProxyTestSection.kt` 展示，状态在 `PHONE/presentation/proxy/ProxyTestViewModel.kt` / `ProxyTestState.kt`，输入变化即清空旧结论。
  - `SubtitleDownloadDialog.kt`：「下载字幕」弹窗，一个弹窗三个阶段（关键词 + 代理入口 / 搜索中 / 候选列表），关键词与代理分别复用 `defaultScrapeKeyword` / `scrapeProxy`。搜索与上传的状态机在 `CORE/subtitle/SubtitleDownloadController.kt`（`SubtitleDownloadState` / `SubtitleDownloadEvent`，详情页与播放页各注入一份、互不干扰），本弹窗与播放页菜单里的「下载字幕」共用同一套 UI：从字幕站（默认 subtitlecat，地址在「更多 → 抓取网址」或设置页里配置，已预填默认网址）搜索前 5 个候选并读完每页可用语言，**先按简体 > 繁体 > 英文定语言**（有简体就绝不退到繁体），再在该语言内按「字幕最后时间点与视频时长之差」挑最贴合的一份（容差为时长的 2%，限 1~3 分钟，因为片尾长时间无台词时字幕最后时间点会早于视频结束），下载后经 `JellyfinRepository.uploadSubtitle` 上传服务器；临时文件落在 `cacheDir/subtitle_temp`，每次使用前清理 3 天前的文件。列表底部另有「播放视频时对比」入口（`MovieAction.PrepareSubtitleCompare` → `SubtitleDownloadEvent.CompareReady`），把已下好的候选落盘并带 Intent extra `openSubtitleCompare` 进播放器，见「字幕对照模式」。
  - 抓取实现集中在 `CORE/scraper/`，文字、封面与头像抓取共用站点解析和 HTTP 基础设施。站点地址优先取运行时配置，未配置则回退打包配置；封面按真实比例归类横图 / 竖图。Cookie、UA 和具体站点兼容规则以该目录源码为准，不在页面索引重复维护。
- **ViewModel / State / Action / Event**：`FILM/presentation/movie/MovieViewModel.kt`、`FILM/presentation/movie/MovieState.kt`、`FILM/presentation/movie/MovieAction.kt`、`FILM/presentation/movie/MovieEvent.kt`；另使用 `CORE/presentation/downloader/DownloaderViewModel.kt` 与共享的 `FILM/presentation/actorimage/ActorImageScrapeViewModel.kt`（State / Action / Event 同层：`ActorImageScrapeState.kt` / `ActorImageScrapeAction.kt` / `ActorImageScrapeEvent.kt`，与人物列表页共用同一个 ViewModel（各页面一份实例），编排「获取头像」弹窗的抓取 / 上传 / 代理；人物 Id + 名字 + 是否已有头像由界面传入，上传成功后各自负责刷新自己的列表；抓取整体异常（非单站失败）时会给尚无结果的站点补一条失败原因，避免这些行一直停在转圈状态）。
- **State 分组**：影片 / 播放源与演职员；封面和 nfo 的加载、编辑与错误；元数据 / 图片抓取的站点、结果、来源选择、代理与翻译；评论开关、关键词、列表、加载 / 错误及 JavDB 登录态；字幕下载整份状态（`subtitleDownload`，镜像自 `CORE/subtitle/SubtitleDownloadState.kt`）；派生本地 / 远程路径与文件名。完整字段以 `MovieState.kt` 为准。
- **下载生命周期**：电影与分集共用 `CORE/presentation/downloader/DownloaderViewModel.kt`；已接受的下载准备独立于详情页，返回后继续交接后台任务。主动取消会停止准备并等待清理，重新进入详情页后取消或删除同样会先停止遗留准备。
- **主要方法 / Action**：加载影片 / 图片 / nfo；播放、标记、导航、元数据与图片抓取 / 上传 / 删除、翻译、抓取网址、`ReloadComments`、字幕下载与上传（`DownloadSubtitle` / `CancelSubtitleDownload`）与进播放器对比（`PrepareSubtitleCompare`）。成功 / 失败通过 `MovieEvent` 反馈，包括 `MetadataUpdated`、`MetadataUnchanged`、`MetadataTranslateFailed`、`SubtitleUploaded`、`SubtitleCompareReady` 等；完整集合以 `MovieAction.kt` / `MovieEvent.kt` 为准。
- **入口**：任意列表页点击 `FindroidMovie` 条目（`PHONE/NavigationRoot.kt`），如首页、媒体库、下载、收藏、合集、剧集 / 季 / 人物作品列表页。

### ShowScreen — 剧集详情

- **职责**：加载剧集、下一集（NextUp）、季列表与演员，提供播放与标记等操作。
- **加载错误**：加载失败停止等待动画，使用 `PHONE/presentation/film/components/DetailLoadError.kt` 显示错误详情和重试入口；重试调用本页原有 load 方法，已有内容继续保留，返回操作可用。
- **路由**：`ShowRoute(showId, useLocalData = false)`，定义并注册于 `PHONE/NavigationRoot.kt`；下载入口的本地数据参数继续传给季 / 分集详情。
- **Screen**：`PHONE/presentation/film/ShowScreen.kt`。
- **ViewModel / State / Action**：`FILM/presentation/show/ShowViewModel.kt`、`FILM/presentation/show/ShowState.kt`、`FILM/presentation/show/ShowAction.kt`。
- **State 字段**：`show`、`nextUp`、`seasons`、`actors`、`director`、`writers`、`error`。
- **主要方法 / Action**：`loadShow(showId)`、`onAction()`；`Play`、`PlayTrailer`、`MarkAsPlayed`、`UnmarkAsPlayed`、`MarkAsFavorite`、`UnmarkAsFavorite`、`OnBackClick`、`OnHomeClick`、`NavigateToItem`、`NavigateToPerson`。
- **入口**：点击 `FindroidShow` 条目（`PHONE/NavigationRoot.kt`）；季页的 `navigateToSeries`（`PHONE/NavigationRoot.kt`）。

### SeasonScreen — 季详情

- **职责**：加载季信息与其分集列表，提供播放、标记、跳转条目或所属剧集。
- **加载错误**：加载失败停止等待动画，使用 `PHONE/presentation/film/components/DetailLoadError.kt` 显示错误详情和重试入口；重试调用本页原有 load 方法，已有内容继续保留，返回操作可用。
- **路由**：`SeasonRoute(seasonId, useLocalData = false)`，定义并注册于 `PHONE/NavigationRoot.kt`；进入分集或返回所属剧集时保留本地数据参数。
- **Screen**：`PHONE/presentation/film/SeasonScreen.kt`。
- **ViewModel / State / Action**：`FILM/presentation/season/SeasonViewModel.kt`、`FILM/presentation/season/SeasonState.kt`、`FILM/presentation/season/SeasonAction.kt`。
- **State 字段**：`season`、`episodes`、`error`。
- **主要方法 / Action**：`loadSeason(seasonId)`、`onAction()`；`Play`、`MarkAsPlayed`、`UnmarkAsPlayed`、`MarkAsFavorite`、`UnmarkAsFavorite`、`OnBackClick`、`OnHomeClick`、`NavigateToItem`、`NavigateToSeries`。
- **入口**：点击 `FindroidSeason` 条目（`PHONE/NavigationRoot.kt`）；分集页的 `navigateToSeason`（`PHONE/NavigationRoot.kt`）。

### EpisodeScreen — 分集详情

- **职责**：加载分集元数据 / 视频信息 / 演员，提供播放、标记已看、收藏、下载、跳转演员与所属季。
- **加载错误**：加载失败停止等待动画，使用 `PHONE/presentation/film/components/DetailLoadError.kt` 显示错误详情和重试入口；重试调用本页原有 load 方法，已有内容继续保留，返回操作可用。
- **路由**：`EpisodeRoute(episodeId, useLocalData = false)`，定义并注册于 `PHONE/NavigationRoot.kt`；返回所属季时保留本地数据参数。
- **Screen**：`PHONE/presentation/film/EpisodeScreen.kt`。
- **ViewModel / State / Action**：`FILM/presentation/episode/EpisodeViewModel.kt`、`FILM/presentation/episode/EpisodeState.kt`、`FILM/presentation/episode/EpisodeAction.kt`；另使用 `CORE/presentation/downloader/DownloaderViewModel.kt`，下载生命周期与电影详情相同。
- **State 字段**：`episode`、`videoMetadata`、`actors`、`displayExtraInfo`、`error`。
- **主要方法 / Action**：`loadEpisode(episodeId)`、`onAction()`；`Play`、`MarkAsPlayed`、`UnmarkAsPlayed`、`MarkAsFavorite`、`UnmarkAsFavorite`、`OnBackClick`、`OnHomeClick`、`NavigateToPerson`、`NavigateToSeason`。
- **入口**：点击 `FindroidEpisode` 条目（`PHONE/NavigationRoot.kt`）。

### NamedItemListScreen — 类别 / 制片公司列表

- **职责**：列出全库的类别或制片公司，顶部提供本地过滤输入，点击某项进入该值下的筛选结果页。
- **路由**：`MetadataBrowseRoute`（`facet` 为 `MetadataFacet.GENRE` / `MetadataFacet.STUDIO`），定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/film/NamedItemListScreen.kt`。
- **ViewModel / State / Action**：`FILM/presentation/nameditem/NamedItemListViewModel.kt`、`FILM/presentation/nameditem/NamedItemListState.kt`、`FILM/presentation/nameditem/NamedItemListAction.kt`。
- **State 字段**：`facet`、`items`、`searchQuery`、`isLoading`、`error`；派生属性 `visibleItems`（本地过滤结果）。
- **主要方法 / Action**：`loadItems(facet)`、`onAction()`；`OnItemClick`、`OnSearchQueryChange`、`Retry`、`OnBackClick`。
- **入口**：我的媒体页底部的浏览入口区块。

### PersonListScreen — 人物列表

- **职责**：按人物类型列出演员 / 导演 / 编剧，顶部搜索走服务端（输入防抖）；人物没有独立详情页，点击某人直接进入按该人物筛选的作品列表（`ItemFilterRoute`，facet 随当前列表的维度）。演员维度（`MetadataFacet.ACTOR`）且非离线模式时，长按人物卡片（`PersonGridCard`）弹出「获取头像 / 复制演员名」小菜单，点击「复制演员名」将完整姓名复制到剪贴板并关闭菜单，成功后显示 Toast 提示；点击「获取头像」打开与电影详情页共用的 `ScrapeActorImageDialog`（编排见 `FILM/presentation/actorimage/ActorImageScrapeViewModel.kt`，与 `MovieScreen.kt` 共用同一个 ViewModel）；上传成功后 Toast 提示并复用 `Retry` 重取列表让新头像显示出来。
- **路由**：`MetadataBrowseRoute`（`facet` 为 `MetadataFacet.ACTOR` / `MetadataFacet.DIRECTOR` / `MetadataFacet.WRITER`），定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/film/PersonListScreen.kt`。
- **ViewModel / State / Action**：`FILM/presentation/personlist/PersonListViewModel.kt`、`FILM/presentation/personlist/PersonListState.kt`、`FILM/presentation/personlist/PersonListAction.kt`；另使用共享的 `FILM/presentation/actorimage/ActorImageScrapeViewModel.kt`。
- **State 字段**：`persons`、`searchQuery`、`isLoading`、`canLoadMore`、`error`；`PersonListState.PAGE_SIZE` 为首屏数量，同时也是「加载更多」的步长。
- **主要方法 / Action**：`loadPersons(facet)`、`onAction()`；`OnPersonClick`、`Search`、`OnLoadMore`、`Retry`、`OnBackClick`。
- **伪分页**：服务端人物接口既不支持偏移分页也不支持排序，`OnLoadMore` 只是把 `limit` 加大后整体重取，因此页码越大响应体越大。
- **入口**：我的媒体页底部的浏览入口区块；点击某人跳到 `ItemFilterRoute`。

### FilterValueListScreen — 标签 / 分级 / 年份列表

- **职责**：列出服务端给出的可用标签、分级或年份，顶部本地过滤，点击某个值进入该值下的筛选结果页。
- **路由**：`MetadataBrowseRoute`（`facet` 为 `MetadataFacet.TAG` / `MetadataFacet.OFFICIAL_RATING` / `MetadataFacet.YEAR`），定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/film/FilterValueListScreen.kt`。
- **ViewModel / State / Action**：`FILM/presentation/filtervalue/FilterValueListViewModel.kt`、`FILM/presentation/filtervalue/FilterValueListState.kt`、`FILM/presentation/filtervalue/FilterValueListAction.kt`。
- **State 字段**：`facet`、`values`、`searchQuery`、`isLoading`、`error`；派生属性 `visibleValues`。
- **主要方法 / Action**：`loadValues(facet)`、`onAction()`；`OnValueClick`、`OnSearchQueryChange`、`Retry`、`OnBackClick`。
- **入口**：我的媒体页底部的浏览入口区块。

### ItemFilterScreen — 元数据筛选结果

- **职责**：按单个元数据值筛选影片与剧集并分页展示，右上角弹窗同时承载排序与封面显示模式。
- **路由**：`ItemFilterRoute(facet, key, title)`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/film/ItemFilterScreen.kt`。
- **ViewModel / State / Action**：`FILM/presentation/itemfilter/ItemFilterViewModel.kt`、`FILM/presentation/itemfilter/ItemFilterState.kt`、`FILM/presentation/itemfilter/ItemFilterAction.kt`。
- **State 字段**：`items`（PagingData Flow）、`facet`、`sortBy`、`sortOrder`、`coverMode`、`error`；列表的加载中 / 出错状态由分页流的 `loadState` 承载（界面渲染同一套）。
- **主要方法 / Action**：`loadItems(facet, key)`、`onAction()`；`OnItemClick`、`OnBackClick`、`ChangeSorting`、`ChangeCoverMode`。
- **筛选参数**：类别、制片公司、人物按实体 id 过滤，标签、分级、年份按文本值过滤；类别额外兼容按名称过滤（从电影详情页点击类型跳过来时 key 是名称而不是 id，`ItemFilterViewModel.toFilterArgs` 里 key 不是合法 UUID 时退回按名称过滤，对应 `getItems` / `getItemsPaging` 的 `genres` 参数）；范围固定为 `MOVIE` + `SERIES`、递归、全库。
- **入口**：三个浏览列表页与人物列表页点击具体值 / 某人；电影 / 剧集 / 分集详情页点击演员（`PHONE/NavigationRoot.kt` 中 `navigateToPerson` 的处理）；电影详情页点击类型 / 标签（`PHONE/NavigationRoot.kt` 中 `navigateToMetadataFacet` 的处理）。

---

## JavDB 过盾页面（javdb）

### JavDbUnblockScreen — JavDB 过盾

- **职责**：JavDB 抓取被 Cloudflare 拦截（`ScraperException.SiteBlocked`）时，在内置 WebView 里打开站点首页，由用户手动完成人机验证。WebView 按设置里的抓取代理（`AppPreferences.scrapeProxy`）经 `androidx.webkit` 的 `ProxyController` 配置同样的出口（与抓取请求对齐 IP，cf_clearance 才对抓取有效）；页面每次加载完成时把 WebView 的 Cookie（含 cf_clearance）经 `CORE/scraper/ScraperCookieJar.importCookies` 搬进抓取 Cookie 罐、并把 WebView 的 UA 存进 `AppPreferences.javdbWebViewUserAgent`（后续 JavDB 抓取请求复用同一 UA，见 `CORE/scraper/ScraperHttp.kt` 的 `withUserAgentOverride`）。网页内可后退时返回键先退网页；右上角「完成」返回上一页，之后在详情页点「重试」重新加载短评。
- **路由**：`JavDbUnblockRoute`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/javdb/JavDbUnblockScreen.kt`（WebView 经 `AndroidView` 承载，无 State / Action 文件）。
- **ViewModel**：`PHONE/presentation/javdb/JavDbUnblockViewModel.kt`，暴露 `startUrl`、`proxyAddress` 与 `onPageFinished(url, cookieHeader, userAgent)`。
- **入口**：电影详情页评论区加载失败且被站点拦截时的「打开网页过盾」按钮（`PHONE/presentation/film/components/MovieCommentsSection.kt`）。

#### 方案备忘：网页版登录（未实现）

现有登录走 `CORE/scraper/JavDbSessionImpl.kt` 的图形验证码流程（取登录页 token → 取 rucaptcha 验证码图 → POST 提交），三步都经过 Cloudflare，依赖过盾拿到的 `cf_clearance` 与 UA 覆盖（`CORE/scraper/ScraperHttp.kt` 的 `withUserAgentOverride`）。**如果下次登录仍然失败**（验证码取图或提交被 403，或验证码频繁识别不过），考虑改为在过盾页的 WebView 里直接登录，思路：

1. 用户在 `JavDbUnblockScreen` 的 WebView 里完成站点登录（站点自己的人机验证在页面里处理，不依赖 App 内的图形验证码流程）。
2. 「完成」时按当前登录态分流：
   - **App 未登录**：把 WebView 的会话 Cookie（`_jdb_session` 等）也导入 `ScraperCookieJar`，再请求一次 profile 页解析账号名 / VIP 回填 `AppPreferences`（`javdbLoggedInUser` / `javdbVip`，解析逻辑可复用 `JavDbSessionImpl` 现成的实现），让设置页与评论区的登录态立即生效；
   - **App 已登录**：维持现状，只导入 Cloudflare 的 Cookie（`cf` / `__cf` 前缀，规则已实现于 `JavDbUnblockViewModel.onPageFinished`），避免 WebView 的匿名会话顶掉已登录会话——之前全量导入导致「会话失效被清光」的教训即源于此。
3. 改动范围集中在 `JavDbUnblockViewModel` 与 `JavDbSession`（`:settings` 接口加一个「从现有会话 Cookie 校验并回填账号状态」的方法 + `:core` 实现），约百行；前置的 UA 覆盖与 Cookie 同步机制均已就绪。

---

## 设置页面（settings）

### SettingsScreen — 设置主页

- **职责**：按 `indexes` 展示分组偏好并处理嵌套导航。首页七个入口按「语言」「用户与服务器」「播放器」「下载与网络」「缓存」「界面」「关于」排列，语言置顶便于首次使用切换。界面页仅保留跟随系统 / 浅色 / 深色主题；动态色彩在系统支持时自动启用。下载与网络页管理下载约束、连接超时、「评论」组（开启外网评论、评论使用代理、站点账号登录）、独立的「抓取网址」卡片与独立的「代理」组（本地代理设置；代理是 nfo / 封面 / 头像 / 字幕 / 评论共用的全局设置，不受评论开关禁用），缓存进入专属页面。「抓取网址」入口只作配置入口、不显示网址，说明为「配置抓取图片、nfo、字幕的站点。」；点开后与详情页「更多」菜单共用 `PHONE/presentation/components/ScraperUrlsDialog.kt`，弹窗回填各站点实际生效地址（运行时配置优先，未配置回退打包配置，见 `SETTINGS/domain/ScrapeSiteUrlsProvider.kt` 与 `CORE/scraper/ScraperUrlProviderImpl.kt`），保存走 `SettingsAction.SaveScrapeUrls`；防呆只针对用户本次改动，不误报回填的打包地址。「本地代理设置」弹窗带「测试」按钮（见电影详情页一节）。动作型偏好只有声明了空态文案的账号行才显示「未登录」。文本输入和登录组件位于 `PHONE/presentation/settings/components/`；站点账号登录弹窗由设置页承载。播放器固定记忆手动调节的亮度，不再提供独立开关。
- **路由**：`SettingsRoute(indexes: IntArray)`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/settings/SettingsScreen.kt`。
- **ViewModel / State / Action / Event**：`SETTINGS/presentation/settings/SettingsViewModel.kt`、`SETTINGS/presentation/settings/SettingsState.kt`、`SETTINGS/presentation/settings/SettingsAction.kt`、`SETTINGS/presentation/settings/SettingsEvent.kt`。
- **State 字段**：`isLoading`、`preferenceGroups`，站点登录态（`javdbLogin` / `javdbAccount` / `javdbLoggedInUser`），以及「抓取网址」弹窗的 `showScrapeUrlsDialog` / `scrapeSiteUrls`。
- **主要方法 / Action**：`loadPreferences(indexes)`、`onAction()`；`OnBackClick`、`OnUpdate`、站点登录相关 Action 与 `OpenScrapeUrls` / `SaveScrapeUrls` / `CloseScrapeUrls`。
- **Event**：`NavigateToUsers`、`NavigateToServers`、`NavigateToAbout`、`NavigateToSettings`、`NavigateToSettingsFileEdit`、`UpdateTheme`、`LaunchIntent`。
- **入口**：底部「设置」标签（`PHONE/NavigationRoot.kt`）。

### AccountsScreen — 用户与服务器

- **职责**：同页展示已保存服务器与当前服务器的用户，离线模式开关固定在头部常驻（不随列表滚动）。服务器卡片按「名称 / 当前地址 / 管理地址 + 移除」三行排布。服务器和用户可切换、添加或移除，地址编辑与登录沿用既有页面；「添加账号」进入登录页。添加服务器后回到本页，页面恢复时刷新列表与连接状态；新用户登录成功后重启界面。移除只影响本地记录，必须确认；身份切换或切换离线模式后重启界面以释放旧服务器 / 用户的页面状态。
- **路由**：`AccountsRoute`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/settings/AccountsScreen.kt`。
- **ViewModel / State / Action / Event**：`SETUP/presentation/accounts/AccountsViewModel.kt`、`SETUP/presentation/accounts/AccountsState.kt`、`SETUP/presentation/accounts/AccountsAction.kt`、`SETUP/presentation/accounts/AccountsEvent.kt`。首帧通过 `SETUP/domain/SetupRepository.kt` 的 `getCachedAccounts()` 复用已加载列表，首次进入时复用 `data/src/main/java/dev/jdtech/jellyfin/api/JellyfinApi.kt` 启动恢复的当前服务器、地址与账号快照（`SETUP/domain/AccountsSnapshot.kt`）；后台 `getAccounts()` 从本地 Room 补齐并更新缓存。离线模式值在构造时同步读取；刷新保留已有内容，相同数据由 StateFlow 去重，列表标题与卡片使用固定 key。公共用户另行异步加载，失败保留旧列表并显示错误；重复选择当前服务器或用户不触发切换。切换服务器时取消旧请求，迟到结果不回填。本页不再依赖 `SETTINGS/presentation/settings/SettingsViewModel.kt`。
- **入口**：设置首页「用户与服务器」。站点账号登录已移至设置页「下载与网络 → 评论」，登录 / 退出会作废评论缓存，站点会话实现见 `CORE/scraper/JavDbSessionImpl.kt`。

### CacheScreen — 缓存空间管理

- **职责**：显示本地视频、在线图片缓存与各类共享缓存的实际磁盘占用，分别设置两个配额并按类别确认清理。在线图片缓存由 Coil 自带磁盘缓存管理，独立配额默认 1024 MB，由框架按自身 LRU 淘汰，配额改动在下次启动生效；共享配额默认 1024 MB，覆盖评论、字幕临时文件、播放器、翻译、网页缓存与离线封面 / 预览图，仍按最久没用先删整体回收。评论固定保鲜 30 天。
- **路由**：`CacheRoute`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/settings/cache/CacheScreen.kt`。
- **ViewModel / State / Action**：`CORE/presentation/cache/CacheViewModel.kt`、`CORE/presentation/cache/CacheState.kt`、`CORE/presentation/cache/CacheAction.kt`。
- **存储实现**：`data/src/main/java/dev/jdtech/jellyfin/cache/CacheManager.kt`、`data/src/main/java/dev/jdtech/jellyfin/cache/FileCacheManager.kt`；DI 见 `core/src/main/java/dev/jdtech/jellyfin/di/CacheModule.kt`。图片磁盘缓存的目录与上限配置、旧版自建图片缓存目录的一次性清理见 `PHONE/BaseApplication.kt`。
- **边界**：所有存储卷的 `downloads` 目录只统计，不回收，包含下载中临时文件与配套字幕。`cacheDir/image_cache` 由图片框架自管，只统计占用、不参与共享回收；`filesDir/images` 与 `filesDir/trickplay` 归入共享配额下的「离线封面与预览图」，清掉后离线可能缺图。使用中的字幕、离线封面写入与 mpv 缓存暂时保护，释放后合并触发后台回收；因此活动文件可能使共享占用暂时超过配额。配置、数据库、Cookie 和登录状态不参与回收。WebView 不再持久缓存新的响应，保留登录所需的 Cookie / DOM 状态。
- **入口**：设置首页「缓存」。

### SettingsFileEditScreen — 设置文件编辑

- **职责**：在 IO 线程读取并原子保存应用 `filesDir` 下的文本文件；读写失败通过 Snackbar 反馈，保存失败保留编辑内容。加载成功前与保存期间禁用编辑和保存。
- **路由**：`SettingsFileEditRoute(filePath)`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/settings/SettingsFileEditScreen.kt`。
- **ViewModel / State / Action**：`SETTINGS/presentation/settings/SettingsFileEditViewModel.kt`、`SETTINGS/presentation/settings/SettingsFileEditState.kt`、`SETTINGS/presentation/settings/SettingsFileEditAction.kt`。
- **State 字段**：`initialText`、`isLoading`、`isLoaded`、`isSaving`、`error`。
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

- **职责**：自动发现局域网服务器，或手动输入地址连接；发现列表按服务器 id 去重，每次以不可变快照通知 UI。
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

- **职责**：视频播放界面（传统 View + Media3 `PlayerView` / ExoPlayer），展示标题、章节，提供跳过片头 / 片尾、PiP、手势，以及右上角菜单（字幕 / 字幕设置 / 下载字幕 / 播放速度 / 音轨 / 截取当前画面设为横屏封面）。字幕外观（字号 / 颜色 / 位置 / 偏移）的加载与应用委托给 `PHONE/presentation/player/subtitle/SubtitleAdjustManager.kt`，字幕搜索 / 上传与多字幕同屏对照分别见「字幕下载」与「字幕对照模式」。
- **路由**：**无 NavHost 路由**，为独立 `Activity`（`class PlayerActivity : BasePlayerActivity()`）；Intent extra `useLocalData` 沿用下载详情的本地数据来源，播放器初次创建、系统恢复和 `onNewIntent` 都据此选择仓库；本地播放时隐藏服务器封面修改与字幕上传入口，跳过字幕关键词联网查询。Intent extra `openSubtitleCompare` 表示开播后直接打开字幕对照面板（详情页「播放视频时对比」使用，读一次即清除；同一影片且数据来源相同时只开面板、不重开视频，否则进度会被重置）。
- **Screen**：`PHONE/PlayerActivity.kt`；其中 `override val viewModel: PlayerViewModel by viewModels()`。菜单里的「截取当前画面设为横屏封面」由 `PlayerActivity.captureBackdrop()` 先经 `PlayerViewModel.hasBackdrop()` 确认服务器上没有 `BACKDROP`，再截帧上传（离线模式下不显示该菜单项，同样不显示「下载字幕」）；截帧工具为 `PHONE/utils/VideoFrameCapture.kt`（`SurfaceView.captureFrameAsJpeg()`，主线程 PixelCopy + 后台按视频显示比例裁黑边和 JPEG 压缩，复制前限制位图尺寸；检查与截帧前后校验条目和播放会话，切集即放弃上传）。
- **ViewModel**：`PLAYER/presentation/PlayerViewModel.kt`。
- **State 字段**（`UiState`，`PlayerViewModel.kt`）：`currentItemTitle`、`currentSegment`、`currentSkipButtonStringRes`、`currentTrickplay`、`currentChapters`、`fileLoaded`；事件流 `eventsChannelFlow`（`PlayerEvents`）。
- **主要方法**：`initializePlayer(itemId, itemKind, startFromBeginning, mediaSourceIndex, useLocalData)`（`mediaSourceIndex` 由详情页「选择播放版本」经同名 Intent extra 传入，为 `null` 时按默认规则选源；`useLocalData` 选定本次播放及进度记录的仓库，并传入 `PLAYER/domain/PlaylistManager.kt`，重新开播时取消旧初始化、清理旧队列与来源缓存）、`updatePlaybackProgress()`、`updateCurrentSegment()`、`reloadCurrentItemWithExternalSubtitle(uri, label, language)`（ExoPlayer 后端重取服务器外挂字幕，用 `setMediaItems` 重建播放列表挂上刚上传的字幕；请求前固定条目、来源与播放会话，重建前读取最新位置和播放状态；使用唯一 id 的本地副本并解除 TEXT 禁用 / 旧覆盖，轨道出现后明确选中新字幕，仅忽略重建本身的同条目切换）、`switchToTrack()`、`selectSpeed()`、`skipSegment()`、`seekToNextChapter()`、`seekToPreviousChapter()`、`isLastChapter()`、`hasBackdrop(itemId)`、`setBackdrop(itemId, imageBytes)`；属性 `currentItemId`（当前播放条目的服务器 id，未开始播放时为 `null`，是字幕上传 / 侧载的依据）。
- **辅助弹窗**（非页面）：`PHONE/presentation/player/PlayerMenuDialogFragment.kt`（右上角菜单，横屏下一行正方形格子、无标题行，汇总字幕 / 字幕设置 / 下载字幕 / 播放速度 / 音轨入口；没有可用字幕轨时「选择字幕轨」「字幕设置」置灰，并把截图设封面、打开下载字幕弹窗的动作回调给宿主 `PlayerActivity`；网格 UI 见 `PHONE/presentation/player/PlayerMenuContent.kt`）、`PHONE/presentation/player/SpeedSelectionDialogFragment.kt`、`PHONE/presentation/player/TrackSelectionDialogFragment.kt`，均有无参构造，通过 `activityViewModels()` 共享宿主的 `PlayerViewModel`；选轨类型放入 arguments，菜单动作经 `PlayerMenuDialogFragment.Host` 从恢复后的宿主取得。
- **入口**：各详情页的播放动作（`MovieAction.Play`、`EpisodeAction.Play` 等）。

### 字幕调节模式（播放器内组件，非独立页面）

- **职责**：播放器内的字幕外观调节模式——调整字号（±5%，范围 50%–200%）、字体透明度（进度条）、垂直拖动字幕位置、字幕进度偏移（±0.3s / ±1s / ±5s / ±20s 档位 + 小输入框）；「应用」保存为全局设置，「重置」恢复全部预览默认值并把进度偏移清零，「关闭」（含调节模式下的系统返回键）丢弃修改。调节模式开着时覆盖层消费全部触摸，播放器手势不会穿透。字幕进度偏移按条目 id 记录并实时保存（下次播放同视频自动恢复），其余设置「应用」后才持久化。字号与位置按**屏幕高度（px）**分开记录（折叠屏两种尺寸互不影响），透明度全局一份。
- **无路由 / 无 Screen / 无 ViewModel**：由 `PlayerActivity` 直接挂载的组件实现——
  - `PHONE/presentation/player/subtitle/SubtitleAdjustManager.kt`：状态与编排（State 数据类 + `MutableStateFlow`），监听视频尺寸变化计算默认位置（黑边放得下一行字幕时整条落进黑边、顶边贴视频底边；放不下时一半盖画面一半盖黑边、底边至少 15dp），把预览改动实时应用到两个后端，「应用 / 重置 / 关闭」的持久化语义也在这里。
  - `PHONE/presentation/player/subtitle/SubtitleAdjustOverlay.kt`：调节模式的全屏 Compose 覆盖层（挂载于 `activity_player.xml` 的 `ComposeView`），使用深色主题，按「设置卡 + 拖动手柄」整列高度计算垂直偏移，并避让系统安全区域与键盘、限制面板宽高；拖动时单独绘制字幕底边参考线，带文字提示的胶囊手柄排在设置卡右下方外侧，按住拖动改变字幕底边距。
  - `PHONE/presentation/player/subtitle/SubtitleAdjustPanel.kt`：圆角悬浮设置卡（非弹窗），依附当前字幕区域左侧、整列底边贴着字幕文字上方并随拖动跟随，调整时可同时看到字幕效果；垂直偏移由 `SubtitleAdjustOverlay.kt` 计算后经 modifier 传入。「外观」分区展示字号步进与透明度滑块，「字幕进度」分区展示偏移输入框和分两行排列的全部档位；标题栏可重置，底部固定关闭 / 应用，仅中间内容在高度不足时滚动。键盘弹出期间收起标题、分区切换和拖动手柄，为偏移输入框与底部操作保留空间。
  - `PHONE/presentation/player/subtitle/DelayedSubtitleView.kt`：字幕容器（内含真实 media3 `SubtitleView`；`SubtitleView` 是 final 不能继承），布局里刻意不放 `exo_subtitles` 让 `PlayerView` 跳过内部渲染，由 `SubtitleAdjustManager` 监听 `onCues` 转发进来、为每次 cue 变化分别排队延迟 `setCues` 实现 ExoPlayer 后端的字幕延后；seek、切集、偏移改变和视图移除时清理旧回调，短句不会被后续空 cue 提前取消；负偏移（提前）ExoPlayer 做不到，按 0 处理并提示。`isSuppressed` 为 true 时丢弃 cue 并清空画面（字幕对照模式打开时用）。
- **后端差异**：ExoPlayer 走 `SubtitleView`（`setFractionalTextSize` / `CaptionStyleCompat` / `setBottomPaddingFraction` / 延迟 `setCues`）；mpv 由 `PLAYER/mpv/MPVPlayer.kt` 的 `setSubtitleScale` / `setSubtitleOpacity` / `setSubtitleBottomOffset` / `setSubtitleDelay` 写 libmpv 属性（`sub-scale` / `sub-color` / `sub-border-color` / `sub-pos` / `sub-delay`，字体色与包边色共用同一 alpha），内嵌 ASS 字幕的字号 / 颜色调整可能不生效，应用时弹 toast 提示。隐藏 / 恢复真实字幕轨与侧载新字幕也分两套：ExoPlayer 侧用 `DelayedSubtitleView.isSuppressed` 与 `setMediaItems` 重建播放列表（`PlayerViewModel.reloadCurrentItemWithExternalSubtitle`），mpv 侧写 `sub-visibility` 与 `sub-add <path> select <title> <lang>`（`MPVPlayer.setSubtitleVisible` / `MPVPlayer.addExternalSubtitle`）。
- **持久化**：`SETTINGS/domain/models/SubtitleAppearance.kt`（外观 JSON 模型，目前仅字体不透明度）+ `SETTINGS/domain/AppPreferences.kt` 的 `subtitleAppearance` / `subtitleScales` / `subtitlePositions` / `subtitleOffsets` 四个偏好（后三个为 JSON map）。
- **入口**：播放器菜单「字幕设置」（`PHONE/presentation/player/PlayerMenuDialogFragment.kt`）；锁屏状态下控制层不可见，自然不可达。

### 字幕对照模式（播放器内组件，非独立页面）

- **职责**：把下载好的多份候选字幕**同屏**叠在画面上逐条对照，最多 5 行，每行「序号 + 时长差 + 当前这句 + 应用」；文本随播放位置刷新，点「应用」二次确认后上传服务器并立即切换为当前字幕轨。为什么不用播放器自身的字幕轨：ExoPlayer 的一个 `TextRenderer` 只渲染一个 group，mpv 也只有 `sid` + `secondary-sid`，都撑不住 5 条，因此由 App 自己解析字幕、按播放位置取句。
- **无路由 / 无 Screen / 无 ViewModel**：由 `PlayerActivity` 装配并接线，实现细节收拢在编排类里——
  - `PHONE/presentation/player/subtitle/SubtitlePlaybackCoordinator.kt`：播放页「下载字幕（入口 B）+ 对照面板」的编排——关键词与面板入口先等待连接恢复，随后处理弹窗与面板状态、状态机事件（上传后侧载切轨 / 失败提示 / 快照就绪开面板）、面板开关、目标条目与关键词跟随切集、对照快照生命周期（`release()`）、画中画收放。由 `PlayerActivity` 手动构造（非 Hilt 单例，依赖作用域与播放器随页面生灭），Activity 只留装配与生命周期接线；弹窗宿主为 `PHONE/presentation/player/subtitle/PlayerSubtitleDownloadDialogHost.kt`（与详情页共用 `SubtitleDownloadDialog`）。
  - `PHONE/presentation/player/subtitle/SubtitleCompareManager.kt`：状态（`CompareState` / `CompareRow`）与取句；播放页每 150ms 调一次 `refresh()`，按「当前播放位置 − 已保存的字幕进度偏移」二分取句，行号与次序**固定不重排**（避免用户点错）。加载完成后先校验播放目标与加载代次，再发布面板状态；初次同条目媒体切入导致会话换代时重读，切条目或换账号则放弃。面板打开时通过 `SubtitleAdjustManager.setRealSubtitleVisible(false)` 关掉真实字幕轨，否则画面会堆成 6 行。
  - `PHONE/presentation/player/subtitle/SubtitleCompareOverlay.kt`：全屏 Compose 覆盖层（`activity_player.xml` 的 `subtitle_compare_overlay`），每行独立 30% 黑蒙层圆角块（不整片压暗画面）、正文 18sp 最多两行、面板内 `fontScale` 锁 1、「关闭」固定在右上角且不参与行流。
  - `CORE/subtitle/SubtitleCompareCache.kt`：快照落盘 `cacheDir/subtitle_compare/<itemId>/`（`manifest.json` + 按候选 `pageUrl` hash 命名的文件，使用唯一临时文件再 rename）；`@Singleton`，`updates` 流让播放页在候选边下边比时增量补行；读取、追加、侧载与清理共用条目锁，批次校验在锁内完成；新一轮对比先清理再登记关联，首次快照准备期间到达的候选也会补齐，旧批次不能复活快照。
  - `CORE/subtitle/SubtitleCueParser.kt`：解析 SRT / VTT 为按时间排序的 `SubtitleCue`，编码按 UTF-8 → GBK → Big5 严格解码兜底；时间戳正则复用 `CORE/subtitle/SubtitleTiming.kt`（Android 的 ICU 正则里花括号必须写成 `[{]` / `[}]`，否则类初始化直接抛异常）。
- **两个入口**：A 详情页下载字幕弹窗底部的「播放视频时对比」（落盘后带 Intent extra `openSubtitleCompare` 进播放器）；B 播放页菜单「下载字幕」打开同一个弹窗后再点「播放视频时对比」。已在播放同一部影片时只开面板、不重开视频。
- **目标约束**：候选固定绑定服务器、用户、条目与下载代次；上传、侧载和切轨同时校验播放会话，切集或重新开播清理候选与异步任务，旧事件不能应用到新播放。
- **生命周期**：进画中画隐藏面板并恢复真实字幕轨，退出小窗后重新打开（缓存保留）；关闭面板、应用成功、播放器内部切集、退出播放页都会删除对应条目的快照。侧载字幕在切集后继续保留，支持切回上一集；退出播放页时回收本次会话经过的所有条目的侧载文件与保护租约。上传成功（含一期弹窗里的「上传」）会立即侧载到当前播放器并切轨，不必等服务器重新索引。

---

## 搜索组件（非独立页面）

搜索**不是独立页面**，`PHONE/NavigationRoot.kt` 中没有搜索路由。

- **UI 组件**：`PHONE/presentation/film/components/SearchBar.kt`（`FilmSearchBar`）。
- **挂载位置**：媒体库页内（`MediaScreen.kt`；`searchViewModel = hiltViewModel()` 见 `MediaScreen.kt`）。
- **ViewModel / State / Action**：`FILM/presentation/search/SearchViewModel.kt`、`FILM/presentation/search/SearchState.kt`、`FILM/presentation/search/SearchAction.kt`。
- **State 字段**：`items`、`loading`；**Action**：`Search(query)`、`OnItemClick`。
- **触发方式**：点击媒体库页内的搜索栏展开搜索；展开状态通过 `MediaScreen` 的 `onSearchExpand` 回传至 `PHONE/NavigationRoot.kt` 的 `searchExpanded`，同时隐藏底部导航。

---

## 页面共性约定

- **最近任务隐私**：`PHONE/BaseApplication.kt` 注册 `PHONE/privacy/RecentsPrivacyLifecycleCallbacks.kt`，在各 Activity 创建完成后统一禁用最近任务截图，并将任务卡片背景设为纯黑，覆盖主界面与独立播放器。仅 Android 13 及以上生效；Android 12 及以下保持系统原有预览行为。所有版本均保留前台截图和录屏能力，不使用 `FLAG_SECURE`，也不在退后台时临时添加遮罩。

- **MVVM + 单向数据流**：`ViewModel` 暴露 `StateFlow<XxxState>`，Screen 通过 `collectAsStateWithLifecycle()` 观察；用户交互分发 `XxxAction` 给 `onAction()`。
- **State / Action 复用**：`DownloadsViewModel` 与 `FavoritesViewModel` 复用 `FILM/presentation/collection/CollectionState.kt` + `FILM/presentation/collection/CollectionAction.kt`，**没有**独立的 `DownloadsState` / `FavoritesState`。
- **无 ViewModel 的页面**：`WelcomeScreen`（仅 `WelcomeAction`，Screen 内联处理）、`AboutScreen`（纯静态 UI）。
- **无 `onAction` 的 ViewModel**：`DownloadsViewModel`。
- **导航**：统一使用类型安全路由；`navigateToItem(navController, item)`（`PHONE/NavigationRoot.kt`）按 `FindroidItem` 子类型分发到具体详情页；`safeNavigate` / `safePopBackStack` 仅在生命周期为 `RESUMED` 时执行导航（`PHONE/NavigationRoot.kt`）。
- **共享元素转场**：NavHost 由 `PHONE/NavigationRoot.kt` 的 `SharedTransitionNavHost` 包裹（内含 `SharedTransitionLayout` 与 M3 全局转场）；需要参与共享元素的目的地（首页 / 媒体库 / 下载 / 媒体库详情 / 合集 / 收藏 / 筛选结果 / 电影详情）在 `composable` 块内用 `SharedElementHost` 下发转场作用域，列表侧封面经 `PHONE/presentation/utils/SharedTransition.kt` 的 `heroSharedBounds` 标记。
- **离线模式**：由 `LocalOfflineMode` CompositionLocal 控制（`PHONE/presentation/utils/`），离线时隐藏「媒体」标签与仅联网功能。
- **UI 规范**：遵循 Android Material Design（Material 3），优先使用 Material3 通用控件（`AlertDialog`、`Dialog` + `Card`、`TextButton`、`OutlinedTextField`、`LazyVerticalGrid` 等），不手搓等效自定义控件；弹窗中的删除、清空这类低频且不可逆的操作统一放弹窗右上角（与标题同行）并用醒目样式提示，执行前二次确认。参考 `MoreMenuDialog.kt`、`EditItemImagesDialog.kt`、`EditItemMetadataDialog.kt`。
