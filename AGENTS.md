# AGENTS.md

本文件为 AI 编码助手在此仓库中工作时提供指导。

## 重要约定

- **修改代码后不要默认运行编译/构建。** 除非用户明确要求，否则不要执行 `./gradlew assemble*`、`./gradlew build`、`./gradlew check` 等命令。该仓库编译耗时较长，且 `check` 仅验证 ktfmt 格式，项目中没有单元测试或插桩测试。
- 需要格式化 Kotlin 代码时，可使用 `./gradlew ktfmtFormat`；仅在用户要求时才运行。
- 保持修改最小化、聚焦，避免不必要的重构或大范围重写。
- **默认只改手机版（`:app:phone`），不考虑 TV 版（`:app:tv`）。** 修改界面或功能时，不要同步维护 TV 版界面，也不必把 TV 版纳入考虑范围；只有与界面无关的通用逻辑（如 `:core`、`:data`、`:player` 等共享模块）才可一并影响 TV 版。当前分支只维护手机版，一切从简。
- **注释一律使用中文**，不要新增英文注释（包括代码注释、KDoc、TODO 说明）。
- **新增或修改功能前，先读「[代码设计与编码规范](#代码设计与编码规范)」一节**，按其分层、分包与代码风味要求实现。
- **打印日志一律走 `AppLog`**（见「[日志规范](#日志规范)」），禁止直接用 `Timber` / `Log` / `println`；tag 的 `torah` 前缀由底层自动添加。

## 代码设计与编码规范

本节是新增/改造功能时的默认约束，优先级高于个人习惯：先想清楚结构，再写代码。

### 1. 先设计再动手

- 动手前用几行文字说明方案：涉及哪些模块、新增哪些类型、数据从哪来到哪去、状态有哪些、失败怎么处理。跨 3 个以上模块或新增 5 个以上文件时，先把方案复述出来再实现，避免方向性返工。
- 优先复用既有抽象（`JellyfinRepository`、`Downloader`、`AppPreferences`、`PlaylistManager`、`ItemsPagingSource`），不要造平行的第二套实现。
- 若方案有取舍（例如要不要新增模块、要不要改数据库 schema），先说明取舍，不要默默选一种。

### 2. 分层与依赖方向

依赖只能单向：`presentation(:app:phone) → 模式模块(:modes:film / :setup / :settings) → :core / :player:* → :data`。

| 层 | 放什么 | 不放什么 |
|---|---|---|
| `:data` | SDK 调用（Jellyfin API）、Room 实体/DAO、DTO、Repository 实现 | 任何 Compose / Android UI 代码 |
| `:core` | 跨模块共享业务：下载器、后台 Worker、DI 装配、通用工具、通用主题与组件、公共字符串资源 | 具体某个页面的逻辑 |
| `:modes:film` `:setup` `:settings` | 各自模式的 ViewModel / State / Action / Event（无 UI） | `@Composable` 界面 |
| `:app:phone` | Compose Screen、导航、Activity、手机端主题 | 网络请求、数据库访问、业务规则 |

硬规则：

- **UI 层不得直接访问 Jellyfin SDK、Room DAO、SharedPreferences**，一律经 `JellyfinRepository` / `AppPreferences` / ViewModel。
- **ViewModel 不得持有 `NavController`、不得直接操作 UI 组件**；导航与外部副作用（跳播放器、打开 URI）在 Screen 的 `onAction` lambda 里处理。
- **禁止反向依赖**（如 `:data` 依赖 `:core`），也禁止为了省事把工具类丢进 `:app:phone` 后被其他模块引用。

### 3. 分包规则

- 源码根是 `src/main/java`（不是 `src/main/kotlin`）；包前缀为 `dev.jdtech.jellyfin`（`:modes:film` 为 `dev.jdtech.jellyfin.film`）。新建文件务必落在既有根包下。
- **按业务域分包，不按类型分包。** 不要建顶层 `viewmodel/`、`ui/`、`adapter/` 这类目录；要建 `presentation/movie/`、`presentation/home/` 等域目录。
- 同一个域内，`XxxViewModel.kt`、`XxxState.kt`、`XxxAction.kt`（必要时 `XxxEvent.kt`）**同层放在该域目录下**；不要另建子目录把它们拆开。
- Screen 放 `:app:phone` 的 `presentation/<域>/`，该域内的可复用 `@Composable` 放 `presentation/<域>/components/`；跨域通用的放 `:core` 的 `core/presentation/`。
- 一个文件只放一个主要类型，文件名与主要类型同名（`MovieViewModel.kt` → `class MovieViewModel`）。
- 新建文件前，先列出同域已有文件，命名和位置与之对齐；宁可扩展现有文件，也不要新建语义重复的 `XxxViewModelV2`。

### 4. 面向对象设计要求

- **用密封层次表达"有限的几种情况"**：领域模型扩展参照 `FindroidItem`（密封接口）→ `FindroidMovie` / `FindroidShow` / … ；页面动作统一 `sealed interface XxxAction`，带参用 `data class`，无参用 `data object`。
- **面向接口编程**：新能力先定义接口再写实现。已有 `JellyfinRepository` → `JellyfinRepositoryImpl`（联网）/ `JellyfinRepositoryOfflineImpl`（离线）三件套。
- **给 Repository 加方法时，联网与离线两个实现都要实现**，离线实现从 Room 读取并返回同构数据，不能只在其中一侧添加。
- **单一职责**：ViewModel 只做状态与编排（调 Repository、更新 State、发事件）；解析、映射、格式化下沉到 `domain/`、`utils/` 或扩展函数（如 `toFindroidMovie()` 这类 DTO → 领域模型映射）。
- **状态不可变**：State 用 `data class` + 全部 `val` + 默认值，派生值写成类内计算属性；禁止把可变集合暴露给 UI 直接修改。
- **依赖注入用 Hilt**：`@Inject constructor` / `@HiltViewModel`，不要手动 new 依赖，不要新增全局单例存放可变状态（`JellyfinApi` 是历史特例，勿仿照新增）。
- **组合优先于继承**；仅在复用既有基类（如 `BasePlayerActivity`）时才继承。

### 5. 代码风味（严格对齐本仓库既有写法）

写新代码前先看同域的既有页面，照它的写法写。当前主流约定：

- 状态流：
  ```kotlin
  private val _state = MutableStateFlow(MovieState())
  val state = _state.asStateFlow()
  // 更新：本仓库主流写法，勿改用 .update {}
  _state.emit(_state.value.copy(movie = movie, error = null))
  ```
- 动作分发：ViewModel 统一 `fun onAction(action: XxxAction) = when (action) { ... }`；Screen 在自己的 `onAction` 里先处理导航/副作用，再转交 `viewModel.onAction(action)`。
- 一次性事件：`private val eventsChannel = Channel<XxxEvent>()` + `val events = eventsChannel.receiveAsFlow()`；UI 侧用 `ObserveAsEvents`（基于 `repeatOnLifecycle(STARTED)`）收集。
- Screen 结构：`public XxxScreen`（`hiltViewModel()` + 副作用 + 导航回调）+ `private XxxScreenLayout`（纯状态渲染），并提供 `@PreviewScreenSizes` 预览，预览数据取 `:core` 的 `core/presentation/dummy/`。
- 状态收集：`val state by viewModel.state.collectAsStateWithLifecycle()`；加载用 `LaunchedEffect(true) { viewModel.loadXxx(...) }`。
- 组件签名：可复用 `@Composable` 以 `modifier: Modifier = Modifier` 作为最后一个参数；Screen 级入口函数不接收 modifier，内部自行 `Modifier.fillMaxSize()`。
- 尺寸与文案：间距一律 `MaterialTheme.spacings.*`，不硬编码 dp；字符串/图标一律 `stringResource` / `painterResource`，新字符串加到对应模块的 `res/values/strings.xml`（跨模块共用的放 `:core`）。
- 导航：`NavigationRoot.kt` 中新增 `@Serializable` 路由类并注册 `composable<...>`，跳转走 `safeNavigate`。
- Gradle 依赖用类型安全访问器：`implementation(projects.modes.film)`。
- 格式化：ktfmt `kotlinLangStyle()`（缩进 4 空格、kotlinlang 风格）。**不要引入 detekt / ktlint / spotless / `.editorconfig`**，本仓库只认 ktfmt。

### 6. 代码要让人类一眼看懂

- 命名要能"读出来"：类用名词、方法用动词（`loadMovie()`）、布尔用 `is/has/can` 前缀（`isPlayed`、`hasSubtitles`）；禁止 `flag`、`tmp`、`data2`、`manager2`。
- 一个函数只做一件事。超过约 40 行或嵌套超过 3 层就拆函数，用函数名表达意图。
- 多用早返回（guard clause）和表达式体，减少深层 if/else 嵌套。
- 消灭魔法值：数字与字符串常量提为 `const val`、枚举，或走资源；同一常量不重复出现两遍。
- 避免超长链式调用与多层 `let/apply/also` 嵌套；必要时拆成有名字的中间变量。
- 不用 `!!`。优先 `?: return`、`?: 默认值`、`checkNotNull(x) { "说明" }`。
- 异常不静默吞掉：`try-catch` / `runCatching` 后要把错误落到 State 的 `error` 字段并给用户可见反馈，诊断信息用 `AppLog` 打日志。
- 提交前通读一遍新增代码，确认"不借助上下文也能读懂"。

### 7. 中文注释的加与不加

- 注释一律中文（见「重要约定」），只解释**为什么**和**非显而易见的约束**，不复述代码在做什么。
  - 好：`// 离线模式下无网络，只能从 Room 读取，缺失字段用默认值兜底`
  - 不好：`// 设置电影`
- 建议加注释的位置：类/接口的职责（一行 KDoc）、业务规则与边界条件、平台或 SDK 的坑、魔法值的来源、复杂算法的步骤。
- 不要加：对代码的直译、空 KDoc 模板、无说明的 `TODO`。新增的公开类与关键函数建议带一行中文 KDoc。

### 8. 完成前自检清单

逐项确认后再交付：

- [ ] 代码落在正确的模块与包（`src/main/java` 下，按业务域分包），无跨层反向依赖。
- [ ] UI 未直接触碰 SDK / DAO / SharedPreferences。
- [ ] 涉及 Repository 的新能力，联网与离线两条实现都已覆盖。
- [ ] `XxxViewModel` / `XxxState` / `XxxAction` / `XxxScreen` 命名与同域既有文件一致。
- [ ] 状态更新、事件分发、导航处理沿用本仓库既有写法。
- [ ] 文案与尺寸走资源，无硬编码文案与魔法值。
- [ ] 关键处有中文注释，且无冗余注释。
- [ ] 日志统一走 `AppLog`，未直接调用 `Timber` / `Log` / `println`。
- [ ] 新增/删除/修改了页面或路由 → 已同步 `wiki/pages.md`。
- [ ] 未擅自运行构建或格式化（除非用户要求）。

## 日志规范

所有日志走统一包装类，tag 自动带 `torah` 前缀，logcat 中搜索 `torah` 即可过滤出本应用全部日志。

- **统一入口**：`dev.jdtech.jellyfin.logging.AppLog`（位于 `:logging` 模块）。
  **新增或修改代码时，打印日志一律用 `AppLog`，禁止直接写 `Timber.x()`、`android.util.Log.x()`、`println()`。**
- **为什么单独建 `:logging` 模块**：各模块之间都是 `implementation`（不传递）依赖，`:data`、`:player:local` 这类模块访问不到 `:core` 里的类（且 `:core` 依赖 `:data`，反向依赖会成环）。只有不依赖任何项目模块的最底层模块才能被所有模块使用，因此日志包装类放在 `:logging`，与 `:settings`、`:player:core` 同级。
- **tag 机制**：tag 由 `TorahDebugTree` 在输出时统一处理——Timber 默认取调用方类名，Tree 在其前加 `torah/`，最终形如 `torah/MovieViewModel`。业务代码**不要手写 tag、不要自己拼 `torah` 前缀**；确需归类时用 `AppLog.tag("MPV").d(...)`，前缀依然自动添加。
- **API 用法**：
  ```kotlin
  AppLog.d("Stream url: $streamUrl") // 也支持占位符：AppLog.d("Stream url: %s", url)
  AppLog.e(exception) // 打印异常堆栈
  AppLog.e(exception, "加载电影 %s 失败", movieId) // 异常 + 说明
  AppLog.tag("MPV").d("mpv 事件：%s", event) // 临时指定业务 tag
  ```
- **级别选择**：`v`/`d` 调试流程，`i` 关键节点，`w` 可恢复的异常，`e` 错误（必须带 Throwable 或写明原因），`wtf` 理论上不该发生的情况。
- **存量代码**：仓库中仍有约 60 处历史 `Timber.*` 调用，**不专门做批量迁移**——因为 Tree 统一加前缀，过滤不受影响；但**改动某个文件时，顺手把该文件里的 `Timber.` 替换为 `AppLog.`** 并移除 `timber.log.Timber` 导入（若不再使用）。
- **敏感信息不落日志**：服务器地址、用户 token、用户名等禁止打印。
- **release 构建**：只有 debug 构建会 plant Tree，release 自动无日志输出，调用处无需判断 `BuildConfig.DEBUG`，也不要为"性能"手动加条件。
- **新模块需要打日志时**：在其 `build.gradle.kts` 加 `implementation(projects.logging)`。

## 项目概述

Findroid 是 Jellyfin 的第三方 Android 应用——使用原生 Jetpack Compose 界面浏览和播放电影/剧集。支持两种视频后端：ExoPlayer 和 mpv。

## Wiki 知识库

`wiki/` 目录存放按代码生成的项目知识文档，用于快速定位功能实现：

| 文档 | 内容 |
|---|---|
| [wiki/pages.md](wiki/pages.md) | 手机端（`:app:phone`）主要页面的路由、Screen、ViewModel / State / Action 路径与职责 |

**使用方式**：需要定位某个页面或功能时，先查 `wiki/pages.md` 的「页面总览」表，再按表中路径读取源码，避免全库盲目搜索。

**更新机制（重要）**：出现以下任一改动后，必须同步更新 `wiki/pages.md`，否则文档会失真：

- 新增 / 删除 / 重命名页面（Screen、ViewModel、State、Action 文件）。
- 新增 / 修改 / 删除路由（`NavigationRoot.kt` 中的 `@Serializable` 路由类或 `composable<...>` 注册块）。
- 页面职责变化（新增 section、更换数据来源、改变入口页面）。

更新步骤：以 `NavigationRoot.kt` 为准核对「页面总览」表 → 更新对应条目 → 在 `wiki/pages.md` 文末「变更记录」追加一行 → 更新其开头「最后更新」日期。文档中引用的 `.kt` 路径必须真实存在，校验命令见该文档的「维护与更新机制」一节。

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
  ├── :settings         ← 偏好设置系统（AppPreferences + 设置界面）
  └── :logging          ← 统一日志入口（AppLog、TorahDebugTree），不依赖任何项目模块
```

`:core` 依赖 `:data`、`:player:core`、`:settings`。`:logging` 是最底层模块，不依赖任何项目模块，被所有模块依赖。所有模块均使用 Hilt 进行依赖注入。

### 架构模式

**MVVM + 单向数据流。** 每个页面包含：
- 一个 `ViewModel`，暴露 `StateFlow<XxxState>` 并处理 `XxxAction` 密封类。
- 一个 `@Composable` Screen（位于 `:app:phone`），观察状态并分发动作。
- State/Action/Event 文件与 ViewModel 同层放置（`modes:film` 或 `:setup` 中）。

以电影页为例：
- `MovieState.kt` / `MovieAction.kt` 在 `modes:film`
- `MovieViewModel.kt` 在 `modes:film`
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
