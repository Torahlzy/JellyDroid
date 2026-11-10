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

**最后更新：2026-11-10**

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

- **职责**：聚合首页。顶部为 `HomeHeader`（服务器名、搜索、重试）；下方按 section 渲染推荐轮播、继续观看、接下来、各媒体库最新。
- **路由**：`HomeRoute`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/film/HomeScreen.kt`；section 组装在 `HomeScreen.kt`。
- **ViewModel / State / Action**：`FILM/presentation/home/HomeViewModel.kt`、`FILM/presentation/home/HomeState.kt`、`FILM/presentation/home/HomeAction.kt`。
- **State 字段**：`server`、`suggestionsSection`、`resumeSection`、`nextUpSection`、`views`、`isLoading`、`error`。
- **主要方法 / Action**：`loadData(refreshSuggestions)`；`OnItemClick`（可带共享元素来源 `heroKey`）、`OnLibraryClick`、`OnRetryClick`、`OnRefresh`、`OnSearchClick`、`OnManageServers`。
- **数据来源**：`getSuggestions()`、`getResumeItems()`、`getNextUp()`、`getUserViews()` + `getLatestMedia(view.id)`（`HomeViewModel.kt`）；每个 section 分别受 `AppPreferences` 的 `homeSuggestions` / `homeContinueWatching` / `homeNextUp` / `homeLatest` 开关控制，为空则不渲染。banner（推荐轮播）只在首次进入与下拉刷新 / 切换服务器（`OnRefresh`）时拉取，从详情页返回首页不重新请求，避免轮播被重建；返回首页若共享元素转场仍在执行，等转场结束后再刷新其它分区，继续观看条目的增删与排序变化使用 LazyRow 条目动画。
- **入口**：启动路由 / 底部「首页」标签 / 各详情页的 `navigateHome`。离线模式下页面不可达（首页数据全来自服务器），落地页与 `navigateHome` 的目标改为下载页。

### MediaScreen — 媒体库总览

- **职责**：展示全部媒体库网格；顶部内嵌搜索栏（`FilmSearchBar`），网格末尾依次是「我喜欢」与「已下载」入口（两卡并排、各占一半行宽，组件为 `MediaEntryCard`）与浏览入口区块。媒体库卡片为横图（`BANNER_ASPECT_RATIO`），列数固定为手机 2 列、折叠屏展开（宽度 ≥ 600dp）4 列——横图卡片列数翻倍时宽度与高度都减半；合集库（`CollectionType.BoxSets`）服务器上不提供封面，卡片改用通用图标 `CoreR.drawable.ic_collection` 占位。
- **路由**：`MediaRoute`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/film/MediaScreen.kt`；搜索栏挂载于 `MediaScreen.kt`。
- **ViewModel / State / Action**：`FILM/presentation/media/MediaViewModel.kt`、`FILM/presentation/media/MediaState.kt`、`FILM/presentation/media/MediaAction.kt`。
- **State 字段**：`libraries`、`isLoading`、`error`。
- **主要方法 / Action**：`loadData()`；`OnItemClick`、`OnFavoritesClick`、`OnDownloadsClick`、`OnRetryClick`。
- **入口**：底部「媒体」标签（离线模式不显示）；首页搜索按钮会切到此页并置 `searchExpanded = true`（`PHONE/NavigationRoot.kt`）。
- **浏览入口**：网格末尾追加 `PHONE/presentation/film/components/MediaBrowseSection.kt`——按 nfo 已入库的维度（类别 / 标签 / 分级 / 年份 / 演员 / 导演 / 编剧 / 制片公司，枚举为 `data/src/main/java/dev/jdtech/jellyfin/models/MetadataFacet.kt`）列出入口，固定每行两个、随列表一起滚动，点击进入 `MetadataBrowseRoute`。

### DownloadsScreen — 下载

- **职责**：展示本地已下载条目，分组网格呈现；空列表时提示「无下载」；点击条目进入对应详情页。
- **路由**：`DownloadsRoute`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/film/DownloadsScreen.kt`。
- **ViewModel**：`FILM/presentation/downloads/DownloadsViewModel.kt`。
- **State / Action**：**复用** `FILM/presentation/collection/CollectionState.kt` 与 `FILM/presentation/collection/CollectionAction.kt`（无独立 State/Action，VM 无 `onAction`）。
- **State 字段**：`sections`、`isLoading`、`error`。
- **主要方法**：`loadItems()`（内部调用 `repository.getDownloads()`）。
- **入口**：媒体页的「已下载」入口（`PHONE/presentation/film/components/MediaEntryCard.kt`，与「我喜欢」并排平分一行宽度）；离线模式下本页同时是底栏「下载」标签的根页面、启动落地页与各详情页 `navigateHome` 的目标，此时（`DownloadsScreen` 的 `isTabRoot`）列表底部额外留出底栏高度，避免最后一行被浮起的底栏压住（`CollectionGrid` 的 `bottomContentPadding`）。

### LibraryScreen — 媒体库详情

- **职责**：单个媒体库的内容列表，支持分页加载、排序切换（含随机排序）与封面显示模式切换（右上角 `SortByDialog` 同时承载两项设置），条目点击进入详情。排序与封面模式偏好与「收藏」「合集详情」页共用，任一处改动都会在下次进入其他页面时生效。
- **路由**：`LibraryRoute(libraryId, libraryName, libraryType)`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/film/LibraryScreen.kt`；设置弹窗为 `PHONE/presentation/film/components/SortByDialog.kt`。
- **ViewModel / State / Action**：`FILM/presentation/library/LibraryViewModel.kt`、`FILM/presentation/library/LibraryState.kt`、`FILM/presentation/library/LibraryAction.kt`。
- **State 字段**：`items`（PagingData Flow）、`sortBy`、`sortOrder`、`coverMode`（`CoverDisplayMode`，默认竖图）、`isLoading`、`error`。
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
- **State / Action**：**复用** `FILM/presentation/collection/CollectionState.kt` 与 `FILM/presentation/collection/CollectionAction.kt`（无独立 State/Action）；其中排序与封面模式字段由收藏页与合集页填充，下载页保持默认值。
- **State 字段**：`sections`、`sortBy`、`sortOrder`、`coverMode`、`isLoading`、`error`。
- **主要方法 / Action**：`loadItems()`（`repository.getFavoriteItems(sortBy, sortOrder)`，排序由服务端完成）、`onAction()`；`OnItemClick`、`OnBackClick`（导航由界面处理）、`ChangeSorting`（更新 State、写入共享偏好并重新取数）、`ChangeCoverMode`（更新 State 并写入共享偏好，不重新取数）。
- **封面显示模式**：横图模式（`CollectionGrid.coverMode`）所有条目走 `Direction.HORIZONTAL` + `BANNER_ASPECT_RATIO`、列宽按可用宽度自适应；竖图模式保留分集原有的 16:9 剧照形态，其余条目用竖图。
- **入口**：媒体库页的「收藏」入口（`PHONE/NavigationRoot.kt`）。

### MovieScreen — 电影详情

- **职责**：加载影片元数据 / 视频信息 / 演员，演员长按菜单支持获取头像与复制完整演员名，提供播放、预告、标记已看、收藏、下载、跳转演员、点击类型 / 标签跳到对应筛选结果页，以及「更多」菜单（编辑封面 / 编辑 nfo / 下载字幕 / 删除全部 / 抓取网址），下载字幕弹窗可把已下好的候选带进播放器做同屏对照（带上上次播放的来源下标，避免在另一个版本上开面板），并处理离线模式（离线时类型 / 标签不可点，也不显示「更多」菜单）。信息行的类型 / 标签取值由 `PHONE/presentation/film/components/InfoText.kt` 渲染，点击经 `MovieAction.NavigateToGenre` / `NavigateToTag` → `PHONE/NavigationRoot.kt` 的 `navigateToMetadataFacet` 跳转 `ItemFilterRoute`。
- **路由**：`MovieRoute(movieId, heroKey?)`，定义并注册于 `PHONE/NavigationRoot.kt`；`heroKey` 记录点击来源，普通列表为空时使用默认卡片 key。
- **进场动画**：列表封面与详情页头部之间是共享元素 morph（普通卡片 key 为 `movie-hero-<movieId>`，首页 Banner / 继续观看 / 接下来 / 最新添加分别使用带来源的 key，避免同一电影同时出现在多个分区时互相抢占；经 `PHONE/presentation/utils/SharedTransition.kt` 的 `heroSharedBounds` 建立，两个转场作用域由 `PHONE/NavigationRoot.kt` 的 `SharedTransitionNavHost` / `SharedElementHost` 经 CompositionLocal 下发）；加载态为居中转圈，内容区块等进场转场（morph）结束后才组合并让标题 / 信息行 / 按钮栏 / 简介 / 演员行依次入场（`AppearingSection`），避免转场中途组合整页内容抢帧卡顿；详情页进入为淡入 + 轻微放大、返回反向收缩，转场参数在 `PHONE/NavigationRoot.kt`（全局 token 镜像在 `PHONE/presentation/theme/Motion.kt`）。
- **评论区（外网短评）**：最下方为 `MovieCommentsSection.kt`，支持加载、失败重试、JavDB 登录态提示。数据访问与磁盘缓存位于 `CORE/comment/`；开关、代理和缓存策略来自设置页「网络 → 外网」。访客可见条数受站点账号权益限制，评论失败不影响详情页其它内容。失败原因是被站点拦截（Cloudflare，`ScraperException.SiteBlocked`，State 字段 `isCommentsSiteBlocked`）时额外提供「打开网页过盾」按钮，经 `NavigationRoot.kt` 的 `navigateToUnblock` 跳转 `JavDbUnblockRoute`。
- **Screen**：`PHONE/presentation/film/MovieScreen.kt`；弹窗与小组件都在 `PHONE/presentation/film/components/` 下：
  - `ItemPlayButton.kt`：封面上的悬浮播放按钮。未滚动时浮在封面中央并跟随内容一起滚动，点击直接播放（`MovieAction.Play(startFromBeginning = false)`，与按钮栏的播放一致）；封面滑出超过一半后收起，用弹簧动画飞到顶栏「回到首页」按钮后方（尺寸与收起锚点按 `ItemTopBar` 的按钮对齐）。悬浮层铺满全屏但不消费触摸，只有按钮圆形区域可点，`headerHeightPx` 由 `MovieScreen.kt` 测量封面后传入。
  - `FilePathText.kt`：副标题下方的文件路径，本地优先于服务器、各自去重拼接、可点击展开；服务端路径用 `ic_cloud`、本地已下载用 `ic_folder`。
  - `PlaybackSourceDialog.kt`：条目有多个来源（被识别成多个版本）时点播放先选版本，选中索引经 Intent extra `mediaSourceIndex` 交给播放器。
  - `MoreMenuDialog.kt`（离线模式不显示）提供「编辑封面」「编辑 nfo」「下载字幕」三个入口；「删除全部」不放进菜单列表，而是用错误色置于标题行右上角（低频破坏性操作统一放右上角），点击后由 `DeleteItemWithFilesDialog.kt` 再确认（连服务器视频文件一起删，确认按钮为错误色，提示中「视频文件」用错误色强调）。按钮行左下角是「抓取网址」配置入口（打开 `ScraperUrlsDialog.kt`）。弹窗切换由 `MovieScreen.kt` 内的私有枚举 `MoreMenuDialogState` 控制（`rememberSaveable` 保存，旋转不丢），表单数据在弹窗打开时加载，结果以 Toast 提示，「删除全部」成功后返回上一页。
  - `ScraperUrlsDialog.kt`：「抓取网址」弹窗，按 `MovieState.scrapeSiteUrls` 的 key 顺序为每个站点渲染一个输入框，站点 key（`ScraperSite.key`：javdb / javbus / jav321，以及字幕站 `subtitlecat`）作为标签与存储 key，保存经 `MovieAction.SaveScrapeUrls` 落到 `AppPreferences.scrapeSiteUrl(key)`；字幕站偏好默认值已填 `https://subtitlecat.com`，打开弹窗即可见、无需手动填写，可改成镜像域名（须与官方站路径结构一致），`CORE/subtitle/SubtitlecatScraper.kt` 拼搜索 URL 时读的就是它（`AppPreferences.subtitleSiteBaseUrl()`）；带防呆提示——网址归一化后解析不出 host、不是 http(s) 站点根地址（带其它 scheme 或路径 / 查询参数）、或 nfo 站点的 host 不含对应站点 key（可能贴错站点）时显示「可能不匹配」，但**不阻止保存**（字幕站不做 host 含 key 校验，填镜像域名不会误报）；防呆与保存共用同一套 scheme 补全规则，避免提示通过却把 URL 拼坏，保存时没写 http / https 的按 https 处理。nfo 站点留空表示清除自定义网址（回退到打包配置；release / staging 的打包配置为空，站点即被跳过），字幕站留空表示恢复内置默认地址。nfo 站点的实际取址逻辑在 `CORE/scraper/ScraperUrlProvider.kt` + `ScraperUrlProviderImpl.kt`（运行时配置优先于打包配置），文字 / 封面 / 头像抓取共用；字幕站不经过它，直接在 `CORE/subtitle/SubtitlecatScraper.kt` 里读偏好。
  - `EditItemImagesDialog.kt`：封面编辑弹窗。上面用缩略图网格列出服务器上「当前服务器使用」的图片（带类型名与同类序号，点击任意一张可全屏查看），服务器上没有任何图片时该区块留空；右上角「清理」二次确认后删光服务器上的图片；左下角「抓取」按番号抓横图 / 竖图，右下角「关闭」（关闭会中断进行中的抓取并丢掉已抓到的图片）。抓取结果不另开弹窗，直接列在同一片网格里：配了地址的站点逐行展示，失败的给出原因（单站失败不影响其它站点），成功的列出抓到的横图 / 竖图（可全屏查看）并在行末给一个「上传」按钮（上传前先删服务器上同类型的旧图，再写入新图），另外在单张图片的全屏查看界面底部也有「上传」，只传当前查看的这一张，方便横图 / 竖图分开传；没有站点配置地址时给出提示并禁用「抓取」。
  - `ImageThumbnail.kt`：可全屏查看的图片缩略图（点击打开 `ImageViewerDialog.kt`）与图片类型文案，供「当前服务器使用」与抓取结果共用；可选地把「单张上传」入口透传给全屏查看界面。
  - `ImageViewerDialog.kt`：全屏查看单张图片，模型同时接受服务器图片地址与本地抓取图片字节；点击关闭，双指缩放、放大后可拖动；调用方给出上传回调时，底部多一个「上传」按钮（只传当前这一张，上传中换成转圈），用于抓取结果里的单张图片。
  - `EditItemMetadataDialog.kt`：编辑 nfo 字段、逐站点并发抓取、切换来源与翻译；支持整份或逐字段恢复服务器原值。**长期不变式：抓取结果的空字段不得覆盖服务器现有内容**（合并见 `MovieViewModel.mergeWithCurrent()`）。演员 / 导演按表单整体替换，编剧等其它人员保留；保存前校验标题、数值与日期。
  - `ScrapeActorImageDialog.kt`：「获取头像」弹窗，与人物列表页共用 `ActorImageScrapeViewModel`；演员长按菜单另有「复制演员名」。抓取支持并发搜索、逐站点展示结果、代理设置与取消，上传头像写入 Person 条目并覆盖原头像，需要服务器管理员权限。具体站点规则见 `CORE/scraper/ActorImageScraperImpl.kt`，站点不匹配或占位图不得上传。
  - `TranslateSettingsDialog.kt`：读写本机 OpenAI 兼容接口设置，可选翻译标题、简介、分类 / 标签及抓取后自动翻译；「翻译现有内容」只处理当前表单。实现位于 `CORE/translator/`：标题 / 简介由模型翻译，分类 / 标签优先使用内置词典与缓存、模型兜底；部分失败保留已成功的译文与原文，不覆盖用户未确认的服务器数据。
  - `ScrapeKeywordDialog.kt` / `ScrapeProgressDialog.kt`：nfo 抓取关键词与进度入口，预填识别出的番号，支持代理、逐站点结果和取消；取消后仍保留已成功结果。封面抓取不走此进度弹窗。
  - `SubtitleDownloadDialog.kt`：「下载字幕」弹窗，一个弹窗三个阶段（关键词 + 代理入口 / 搜索中 / 候选列表），关键词与代理分别复用 `defaultScrapeKeyword` / `scrapeProxy`。搜索与上传的状态机在 `CORE/subtitle/SubtitleDownloadController.kt`（`SubtitleDownloadState` / `SubtitleDownloadEvent`，详情页与播放页各注入一份、互不干扰），本弹窗与播放页菜单里的「下载字幕」共用同一套 UI：从字幕站（默认 subtitlecat，地址在「更多 → 抓取网址」里配置，已预填默认网址）搜索前 5 个候选并读完每页可用语言，**先按简体 > 繁体 > 英文定语言**（有简体就绝不退到繁体），再在该语言内按「字幕最后时间点与视频时长之差」挑最贴合的一份（容差为时长的 2%，限 1~3 分钟，因为片尾长时间无台词时字幕最后时间点会早于视频结束），下载后经 `JellyfinRepository.uploadSubtitle` 上传服务器；临时文件落在 `cacheDir/subtitle_temp`，每次使用前清理 3 天前的文件。列表底部另有「播放视频时对比」入口（`MovieAction.PrepareSubtitleCompare` → `SubtitleDownloadEvent.CompareReady`），把已下好的候选落盘并带 Intent extra `openSubtitleCompare` 进播放器，见「字幕对照模式」。
  - 抓取实现集中在 `CORE/scraper/`，文字、封面与头像抓取共用站点解析和 HTTP 基础设施。站点地址优先取运行时配置，未配置则回退打包配置；封面按真实比例归类横图 / 竖图。Cookie、UA 和具体站点兼容规则以该目录源码为准，不在页面索引重复维护。
- **ViewModel / State / Action / Event**：`FILM/presentation/movie/MovieViewModel.kt`、`FILM/presentation/movie/MovieState.kt`、`FILM/presentation/movie/MovieAction.kt`、`FILM/presentation/movie/MovieEvent.kt`；另使用 `CORE/presentation/downloader/DownloaderViewModel.kt` 与共享的 `FILM/presentation/actorimage/ActorImageScrapeViewModel.kt`（State / Action / Event 同层：`ActorImageScrapeState.kt` / `ActorImageScrapeAction.kt` / `ActorImageScrapeEvent.kt`，与人物列表页共用同一个 ViewModel（各页面一份实例），编排「获取头像」弹窗的抓取 / 上传 / 代理；人物 Id + 名字 + 是否已有头像由界面传入，上传成功后各自负责刷新自己的列表；抓取整体异常（非单站失败）时会给尚无结果的站点补一条失败原因，避免这些行一直停在转圈状态）。
- **State 分组**：影片 / 播放源与演职员；封面和 nfo 的加载、编辑与错误；元数据 / 图片抓取的站点、结果、来源选择、代理与翻译；评论开关、关键词、列表、加载 / 错误及 JavDB 登录态；字幕下载整份状态（`subtitleDownload`，镜像自 `CORE/subtitle/SubtitleDownloadState.kt`）；派生本地 / 远程路径与文件名。完整字段以 `MovieState.kt` 为准。
- **主要方法 / Action**：加载影片 / 图片 / nfo；播放、标记、导航、元数据与图片抓取 / 上传 / 删除、翻译、抓取网址、`ReloadComments`、字幕下载与上传（`DownloadSubtitle` / `CancelSubtitleDownload`）与进播放器对比（`PrepareSubtitleCompare`）。成功 / 失败通过 `MovieEvent` 反馈，包括 `MetadataUpdated`、`MetadataUnchanged`、`MetadataTranslateFailed`、`SubtitleUploaded`、`SubtitleCompareReady` 等；完整集合以 `MovieAction.kt` / `MovieEvent.kt` 为准。
- **入口**：任意列表页点击 `FindroidMovie` 条目（`PHONE/NavigationRoot.kt`），如首页、媒体库、下载、收藏、合集、剧集 / 季 / 人物作品列表页。

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

- **职责**：按 `indexes` 展示分组偏好并处理嵌套导航。网络分类的「外网」组管理评论开关、代理、缓存与 JavDB 站点账号；文本输入和登录组件位于 `PHONE/presentation/settings/components/`，会话实现见 `CORE/scraper/JavDbSessionImpl.kt`。登录 / 退出会清理短评缓存，可见短评数量受站点账号权益限制。
- **路由**：`SettingsRoute(indexes: IntArray)`，定义并注册于 `PHONE/NavigationRoot.kt`。
- **Screen**：`PHONE/presentation/settings/SettingsScreen.kt`。
- **ViewModel / State / Action / Event**：`SETTINGS/presentation/settings/SettingsViewModel.kt`、`SETTINGS/presentation/settings/SettingsState.kt`、`SETTINGS/presentation/settings/SettingsAction.kt`、`SETTINGS/presentation/settings/SettingsEvent.kt`。
- **State 字段**：`isLoading`、`preferenceGroups`。
- **主要方法 / Action**：`loadPreferences(indexes, deviceType)`、`onAction()`；`OnBackClick`、`OnUpdate`。
- **Event**：`NavigateToUsers`、`NavigateToServers`、`NavigateToAbout`、`NavigateToSettings`、`NavigateToSettingsFileEdit`、`UpdateTheme`、`LaunchIntent`、`RestartActivity`。
- **入口**：底部「设置」标签（`PHONE/NavigationRoot.kt`）。

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

- **职责**：视频播放界面（传统 View + Media3 `PlayerView` / ExoPlayer），展示标题、章节，提供跳过片头 / 片尾、PiP、手势，以及右上角菜单（字幕 / 字幕设置 / 下载字幕 / 播放速度 / 音轨 / 截取当前画面设为横屏封面）。字幕外观（字号 / 颜色 / 位置 / 偏移）的加载与应用委托给 `PHONE/presentation/player/subtitle/SubtitleAdjustManager.kt`，字幕搜索 / 上传与多字幕同屏对照分别见「字幕下载」与「字幕对照模式」。
- **路由**：**无 NavHost 路由**，为独立 `Activity`（`class PlayerActivity : BasePlayerActivity()`）；Intent extra `openSubtitleCompare` 表示开播后直接打开字幕对照面板（详情页「播放视频时对比」使用，读一次即清除；同一影片已在播放时只开面板、不重开视频，否则进度会被重置）。
- **Screen**：`PHONE/PlayerActivity.kt`；其中 `override val viewModel: PlayerViewModel by viewModels()`。菜单里的「截取当前画面设为横屏封面」由 `PlayerActivity.captureBackdrop()` 先经 `PlayerViewModel.hasBackdrop()` 确认服务器上没有 `BACKDROP`，再截帧上传（离线模式下不显示该菜单项，同样不显示「下载字幕」）；截帧工具为 `PHONE/utils/VideoFrameCapture.kt`（`SurfaceView.captureFrameAsJpeg()`，PixelCopy + 按视频显示比例居中裁掉黑边 + JPEG 压缩）。
- **ViewModel**：`PLAYER/presentation/PlayerViewModel.kt`。
- **State 字段**（`UiState`，`PlayerViewModel.kt`）：`currentItemTitle`、`currentSegment`、`currentSkipButtonStringRes`、`currentTrickplay`、`currentChapters`、`fileLoaded`；事件流 `eventsChannelFlow`（`PlayerEvents`）。
- **主要方法**：`initializePlayer(itemId, itemKind, startFromBeginning, mediaSourceIndex)`（`mediaSourceIndex` 由详情页「选择播放版本」经 Intent extra `mediaSourceIndex` 传入，为 `null` 时按默认规则选源）、`updatePlaybackProgress()`、`updateCurrentSegment()`、`reloadCurrentItemWithExternalSubtitle(uri, label, language)`（ExoPlayer 后端重取服务器外挂字幕，用 `setMediaItems` 重建播放列表挂上刚上传的字幕；服务器未索引好时注入本地侧载兜底，保住位置 / 播放状态并在重建期间暂停进度上报）、`switchToTrack()`、`selectSpeed()`、`skipSegment()`、`seekToNextChapter()`、`seekToPreviousChapter()`、`isLastChapter()`、`hasBackdrop(itemId)`、`setBackdrop(itemId, imageBytes)`；属性 `currentItemId`（当前播放条目的服务器 id，未开始播放时为 `null`，是字幕上传 / 侧载的依据）。
- **辅助弹窗**（非页面）：`PHONE/presentation/player/PlayerMenuDialogFragment.kt`（右上角菜单，横屏下一行正方形格子、无标题行，汇总字幕 / 字幕设置 / 下载字幕 / 播放速度 / 音轨入口；没有可用字幕轨时「选择字幕轨」「字幕设置」置灰，并把截图设封面、打开下载字幕弹窗的动作回调给宿主 `PlayerActivity`；网格 UI 见 `PHONE/presentation/player/PlayerMenuContent.kt`）、`PHONE/presentation/player/SpeedSelectionDialogFragment.kt`、`PHONE/presentation/player/TrackSelectionDialogFragment.kt`，与 `PlayerActivity` 通过构造参数共享同一 `PlayerViewModel`。
- **入口**：各详情页的播放动作（`MovieAction.Play`、`EpisodeAction.Play` 等）。

### 字幕调节模式（播放器内组件，非独立页面）

- **职责**：播放器内的字幕外观调节模式——调整字号（±5%，范围 50%–200%）、字体透明度（进度条）、垂直拖动字幕位置、字幕进度偏移（±0.3s / ±1s / ±5s / ±20s 档位 + 小输入框）；「应用」保存为全局设置，「重置」恢复全部预览默认值并把进度偏移清零，「关闭」（含调节模式下的系统返回键）丢弃修改。调节模式开着时覆盖层消费全部触摸，播放器手势不会穿透。字幕进度偏移按条目 id 记录并实时保存（下次播放同视频自动恢复），其余设置「应用」后才持久化。字号与位置按**屏幕高度（px）**分开记录（折叠屏两种尺寸互不影响），透明度全局一份。
- **无路由 / 无 Screen / 无 ViewModel**：由 `PlayerActivity` 直接挂载的组件实现——
  - `PHONE/presentation/player/subtitle/SubtitleAdjustManager.kt`：状态与编排（State 数据类 + `MutableStateFlow`），监听视频尺寸变化计算默认位置（黑边放得下一行字幕时整条落进黑边、顶边贴视频底边；放不下时一半盖画面一半盖黑边、底边至少 15dp），把预览改动实时应用到两个后端，「应用 / 重置 / 关闭」的持久化语义也在这里。
  - `PHONE/presentation/player/subtitle/SubtitleAdjustOverlay.kt`：调节模式的全屏 Compose 覆盖层（挂载于 `activity_player.xml` 的 `ComposeView`），使用深色主题，按「设置卡 + 拖动手柄」整列高度计算垂直偏移，并避让系统安全区域与键盘、限制面板宽高；拖动时单独绘制字幕底边参考线，带文字提示的胶囊手柄排在设置卡右下方外侧，按住拖动改变字幕底边距。
  - `PHONE/presentation/player/subtitle/SubtitleAdjustPanel.kt`：圆角悬浮设置卡（非弹窗），依附当前字幕区域左侧、整列底边贴着字幕文字上方并随拖动跟随，调整时可同时看到字幕效果；垂直偏移由 `SubtitleAdjustOverlay.kt` 计算后经 modifier 传入。「外观」分区展示字号步进与透明度滑块，「字幕进度」分区展示偏移输入框和分两行排列的全部档位；标题栏可重置，底部固定关闭 / 应用，仅中间内容在高度不足时滚动。键盘弹出期间收起标题、分区切换和拖动手柄，为偏移输入框与底部操作保留空间。
  - `PHONE/presentation/player/subtitle/DelayedSubtitleView.kt`：字幕容器（内含真实 media3 `SubtitleView`；`SubtitleView` 是 final 不能继承），布局里刻意不放 `exo_subtitles` 让 `PlayerView` 跳过内部渲染，由 `SubtitleAdjustManager` 监听 `onCues` 转发进来、延迟 `setCues` 实现 ExoPlayer 后端的字幕延后；负偏移（提前）ExoPlayer 做不到，按 0 处理并提示。`isSuppressed` 为 true 时丢弃 cue 并清空画面（字幕对照模式打开时用）。
- **后端差异**：ExoPlayer 走 `SubtitleView`（`setFractionalTextSize` / `CaptionStyleCompat` / `setBottomPaddingFraction` / 延迟 `setCues`）；mpv 由 `PLAYER/mpv/MPVPlayer.kt` 的 `setSubtitleScale` / `setSubtitleOpacity` / `setSubtitleBottomOffset` / `setSubtitleDelay` 写 libmpv 属性（`sub-scale` / `sub-color` / `sub-border-color` / `sub-pos` / `sub-delay`，字体色与包边色共用同一 alpha），内嵌 ASS 字幕的字号 / 颜色调整可能不生效，应用时弹 toast 提示。隐藏 / 恢复真实字幕轨与侧载新字幕也分两套：ExoPlayer 侧用 `DelayedSubtitleView.isSuppressed` 与 `setMediaItems` 重建播放列表（`PlayerViewModel.reloadCurrentItemWithExternalSubtitle`），mpv 侧写 `sub-visibility` 与 `sub-add <path> select <title> <lang>`（`MPVPlayer.setSubtitleVisible` / `MPVPlayer.addExternalSubtitle`）。
- **持久化**：`SETTINGS/domain/models/SubtitleAppearance.kt`（外观 JSON 模型，目前仅字体不透明度）+ `SETTINGS/domain/AppPreferences.kt` 的 `subtitleAppearance` / `subtitleScales` / `subtitlePositions` / `subtitleOffsets` 四个偏好（后三个为 JSON map）。
- **入口**：播放器菜单「字幕设置」（`PHONE/presentation/player/PlayerMenuDialogFragment.kt`）；锁屏状态下控制层不可见，自然不可达。

### 字幕对照模式（播放器内组件，非独立页面）

- **职责**：把下载好的多份候选字幕**同屏**叠在画面上逐条对照，最多 5 行，每行「序号 + 时长差 + 当前这句 + 应用」；文本随播放位置刷新，点「应用」二次确认后上传服务器并立即切换为当前字幕轨。为什么不用播放器自身的字幕轨：ExoPlayer 的一个 `TextRenderer` 只渲染一个 group，mpv 也只有 `sid` + `secondary-sid`，都撑不住 5 条，因此由 App 自己解析字幕、按播放位置取句。
- **无路由 / 无 Screen / 无 ViewModel**：由 `PlayerActivity` 装配并接线，实现细节收拢在编排类里——
  - `PHONE/presentation/player/subtitle/SubtitlePlaybackCoordinator.kt`：播放页「下载字幕（入口 B）+ 对照面板」的编排——弹窗与面板状态、状态机事件（上传后侧载切轨 / 失败提示 / 快照就绪开面板）、面板开关、目标条目与关键词跟随切集、对照快照生命周期（`release()`）、画中画收放。由 `PlayerActivity` 手动构造（非 Hilt 单例，依赖作用域与播放器随页面生灭），Activity 只留装配与生命周期接线；弹窗宿主为 `PHONE/presentation/player/subtitle/PlayerSubtitleDownloadDialogHost.kt`（与详情页共用 `SubtitleDownloadDialog`）。
  - `PHONE/presentation/player/subtitle/SubtitleCompareManager.kt`：状态（`CompareState` / `CompareRow`）与取句；播放页每 150ms 调一次 `refresh()`，按「当前播放位置 − 已保存的字幕进度偏移」二分取句，行号与次序**固定不重排**（避免用户点错）。面板打开时通过 `SubtitleAdjustManager.setRealSubtitleVisible(false)` 关掉真实字幕轨，否则画面会堆成 6 行。
  - `PHONE/presentation/player/subtitle/SubtitleCompareOverlay.kt`：全屏 Compose 覆盖层（`activity_player.xml` 的 `subtitle_compare_overlay`），每行独立 30% 黑蒙层圆角块（不整片压暗画面）、正文 18sp 最多两行、面板内 `fontScale` 锁 1、「关闭」固定在右上角且不参与行流。
  - `CORE/subtitle/SubtitleCompareCache.kt`：快照落盘 `cacheDir/subtitle_compare/<itemId>/`（`manifest.json` + 按候选 `pageUrl` hash 命名的文件，先写 `.tmp` 再 rename）；`@Singleton`，`updates` 流让播放页在候选边下边比时增量补行；新一轮对比由 `SubtitleDownloadController.prepareCompare()` 先清空该目录，否则旧行号会按序合并进面板。
  - `CORE/subtitle/SubtitleCueParser.kt`：解析 SRT / VTT 为按时间排序的 `SubtitleCue`，编码按 UTF-8 → GBK → Big5 严格解码兜底；时间戳正则复用 `CORE/subtitle/SubtitleTiming.kt`（Android 的 ICU 正则里花括号必须写成 `[{]` / `[}]`，否则类初始化直接抛异常）。
- **两个入口**：A 详情页下载字幕弹窗底部的「播放视频时对比」（落盘后带 Intent extra `openSubtitleCompare` 进播放器）；B 播放页菜单「下载字幕」打开同一个弹窗后再点「播放视频时对比」。已在播放同一部影片时只开面板、不重开视频。
- **生命周期**：进画中画隐藏面板并恢复真实字幕轨，退出小窗后重新打开（缓存保留）；关闭面板、应用成功、播放器内部切集、退出播放页都会删除对应条目的快照。上传成功（含一期弹窗里的「上传」）会立即侧载到当前播放器并切轨，不必等服务器重新索引。

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
- **无 `onAction` 的 ViewModel**：`DownloadsViewModel`。
- **导航**：统一使用类型安全路由；`navigateToItem(navController, item)`（`PHONE/NavigationRoot.kt`）按 `FindroidItem` 子类型分发到具体详情页；`safeNavigate` / `safePopBackStack` 仅在生命周期为 `RESUMED` 时执行导航（`PHONE/NavigationRoot.kt`）。
- **共享元素转场**：NavHost 由 `PHONE/NavigationRoot.kt` 的 `SharedTransitionNavHost` 包裹（内含 `SharedTransitionLayout` 与 M3 全局转场）；需要参与共享元素的目的地（首页 / 媒体库 / 下载 / 媒体库详情 / 合集 / 收藏 / 筛选结果 / 电影详情）在 `composable` 块内用 `SharedElementHost` 下发转场作用域，列表侧封面经 `PHONE/presentation/utils/SharedTransition.kt` 的 `heroSharedBounds` 标记。
- **离线模式**：由 `LocalOfflineMode` CompositionLocal 控制（`PHONE/presentation/utils/`），离线时隐藏「媒体」标签与仅联网功能。
- **UI 规范**：遵循 Android Material Design（Material 3），优先使用 Material3 通用控件（`AlertDialog`、`Dialog` + `Card`、`TextButton`、`OutlinedTextField`、`LazyVerticalGrid` 等），不手搓等效自定义控件；弹窗中的删除、清空这类低频且不可逆的操作统一放弹窗右上角（与标题同行）并用醒目样式提示，执行前二次确认。参考 `MoreMenuDialog.kt`、`EditItemImagesDialog.kt`、`EditItemMetadataDialog.kt`。
