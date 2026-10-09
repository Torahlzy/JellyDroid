# AGENTS.md

本文件为 AI 编码助手在此仓库中工作时提供指导。

## 重要约定

- **改完代码不要默认跑构建。** 除非用户明确要求，否则不执行 `./gradlew assemble*`、`./gradlew build`、`./gradlew check`（编译耗时长；仓库没有测试，`check` 只校验 ktfmt 格式）。需要格式化时用 `./gradlew ktfmtFormat`，同样只在用户要求时运行。
- **但要查编译错误，且优先用不产生构建开销的方式。** 顺序是：① 先看编辑器 / IDE 诊断（对已分析文件的实时语法、类型、未解析引用报错，零构建开销，改完代码就该看一眼）；② 诊断不足以判断时（涉及 Gradle 配置、资源引用、KSP / Hilt 生成代码），只编译受影响模块的 Kotlin 任务，如 `./gradlew :app:phone:compileLibreDebugKotlin`（任务名以 `./gradlew :app:phone:tasks --all` 为准）；③ 仅在用户明确要求时才 `assemble*` / `bundle*` 打包。**不要为了「看有没有编译错误」去跑 `assemble*`**——它会附带 dex、资源、打包等与「查错」无关的耗时步骤。
- 保持修改最小化、聚焦，避免不必要的重构或大范围重写。
- **默认只改手机版（`:app:phone`），不考虑 TV 版（`:app:tv`）。** 只有与界面无关的共享逻辑（`:core`、`:data`、`:player:*`、`:settings`）才可能一并影响 TV 版。
- **注释一律使用中文**（代码注释、KDoc、TODO 说明）。
- **新增或修改功能前，先读「[代码设计与编码规范](#代码设计与编码规范)」**，按其分层、分包与代码风味要求实现。
- **日志一律走 `AppLog`**（见「[日志规范](#日志规范)」），禁止直接用 `Timber` / `Log` / `println`。

## 代码设计与编码规范

本节是新增 / 改造功能时的默认约束，优先级高于个人习惯。

### 1. 先设计再动手

- 动手前用几行文字说明方案：涉及哪些模块、新增哪些类型、数据从哪来到哪去、状态有哪些、失败怎么处理。跨 3 个以上模块或新增 5 个以上文件时，先把方案复述出来再实现。
- 优先复用既有抽象（`JellyfinRepository`、`Downloader`、`AppPreferences`、`PlaylistManager`、`ItemsPagingSource`），不要造平行的第二套实现。
- 方案有取舍（要不要新增模块、要不要改数据库 schema 等）时先说明取舍，不要默默选一种。

### 2. 分层与依赖方向

依赖只能向下：`:app:phone`（及 `:app:tv`）→ `:modes:film` / `:setup` / `:player:local` → `:core` → `:data` / `:player:core` / `:settings` → `:logging`。下层不得反向依赖上层。

| 层 | 放什么 | 不放什么 |
|---|---|---|
| `:data` | SDK 调用（Jellyfin API）、Room 实体/DAO、DTO、Repository 实现 | 任何 Compose / Android UI 代码 |
| `:core` | 跨模块共享业务：下载器、后台 Worker、DI 装配、爬虫、通用工具 / 主题 / 组件、公共字符串资源 | 具体某个页面的逻辑 |
| `:modes:film` `:setup` `:player:local` | 各自的 ViewModel / State / Action / Event（无 UI） | `@Composable` 界面 |
| `:settings` | `AppPreferences` 与设置项模型 | 业务数据访问 |
| `:app:phone` | Compose Screen、导航、Activity、手机端主题 | 网络请求、数据库访问、业务规则 |

硬规则：

- **UI 层不得直接访问 Jellyfin SDK、Room DAO、SharedPreferences**，一律经 `JellyfinRepository` / `AppPreferences` / ViewModel。
- **ViewModel 不得持有 `NavController`、不得直接操作 UI 组件**；导航与外部副作用（跳播放器、打开 URI）在 Screen 的 `onAction` lambda 里处理。
- **禁止反向依赖**，也禁止为了省事把工具类丢进 `:app:phone` 后被其他模块引用。

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
- 尺寸与文案：间距一律 `MaterialTheme.spacings.*`，不硬编码 dp；字符串 / 图标一律 `stringResource` / `painterResource`，新字符串加到对应模块的 `res/values/strings.xml`（跨模块共用的放 `:core`）。**必须同步翻译**：`:core` 下新增 `<string>` 时，同一次改动里把中文翻译加进 `:core` 的 `res/values-zh-rCN/strings.xml` 和 `res/values-zh-rTW/strings.xml`（其他语言目录不管）。中文目录按 key 排序位置就近插入即可，不必与英文文件逐行对齐；漏加的后果是中文界面显示英文——此问题已犯过两次，完成前用差集脚本核对（见「本地化核对命令」）。
- 弹窗布局：删除、清空这类低频且不可逆的操作，不放进弹窗的常用操作列表，统一放弹窗**右上角**（与标题同一行），并用错误色等醒目样式提示风险；执行前必须再弹一次确认。例：更多操作弹窗的「删除全部」（`MoreMenuDialog.kt`）、编辑封面的「清理」（`EditItemImagesDialog.kt`）、编辑 nfo 的「清空」（`EditItemMetadataDialog.kt`）。
- 界面设计：遵循 Android Material Design（Material 3），优先使用 Material3 通用控件（`AlertDialog`、`Dialog` + `Card`、`TextButton`、`OutlinedTextField`、`LazyVerticalGrid` 等）承载交互，不要手搓等效的自定义控件。
- 导航：`NavigationRoot.kt` 中新增 `@Serializable` 路由类并注册 `composable<...>`，跳转走 `safeNavigate`。
- Gradle 依赖用类型安全访问器：`implementation(projects.modes.film)`。
- 格式化：ktfmt `kotlinLangStyle()`（缩进 4 空格）。**不要引入 detekt / ktlint / spotless / `.editorconfig`**，本仓库只认 ktfmt。

### 6. 代码要让人类一眼看懂

- 命名要能"读出来"：类用名词、方法用动词（`loadMovie()`）、布尔用 `is/has/can` 前缀（`isPlayed`、`hasSubtitles`）；禁止 `flag`、`tmp`、`data2`、`manager2`。
- 一个函数只做一件事。超过约 40 行或嵌套超过 3 层就拆函数，用函数名表达意图。
- 多用早返回（guard clause）和表达式体，减少深层 if/else 嵌套。
- 消灭魔法值：数字与字符串常量提为 `const val`、枚举，或走资源。
- 避免超长链式调用与多层 `let/apply/also` 嵌套；必要时拆成有名字的中间变量。
- 不用 `!!`；优先 `?: return`、`?: 默认值`、`checkNotNull(x) { "说明" }`。
- 异常不静默吞掉：`try-catch` / `runCatching` 后要把错误落到 State 的 `error` 字段并给用户可见反馈，诊断信息用 `AppLog` 打日志。

### 7. 中文注释的加与不加

- 注释一律中文（见「重要约定」），只解释**为什么**和**非显而易见的约束**，不复述代码在做什么。
  - 好：`// 离线模式下无网络，只能从 Room 读取，缺失字段用默认值兜底`
  - 不好：`// 设置电影`
- 建议加注释的位置：类 / 接口的职责（一行 KDoc）、业务规则与边界条件、平台或 SDK 的坑、魔法值的来源、复杂算法的步骤。
- 不要加：对代码的直译、空 KDoc 模板、无说明的 `TODO`。

### 8. 完成前自检清单

- [ ] 代码落在正确的模块与包（`src/main/java` 下、按业务域分包），无跨层反向依赖。
- [ ] UI 未直接触碰 SDK / DAO / SharedPreferences。
- [ ] 涉及 Repository 的新能力，联网与离线两条实现都已覆盖。
- [ ] 命名、状态更新、事件分发、导航处理沿用同域既有写法。
- [ ] 文案与尺寸走资源，无硬编码文案与魔法值。
- [ ] 新增 `<string>` 时已同步 `values-zh-rCN` 与 `values-zh-rTW`（可用「本地化核对命令」验证差集只剩 `translatable="false"` 的 key）。
- [ ] 关键处有中文注释，且无冗余注释。
- [ ] 日志统一走 `AppLog`。
- [ ] 新增 / 删除 / 修改了页面或路由 → 已同步 `wiki/pages.md`（只写路径、不带行号）。

## 本地化核对命令

改完字符串后，核对英文 `values/strings.xml` 与中文目录的 key 差集（应只剩 `translatable="false"` 的 key，如 `app_name`）。把下面命令存成临时 `.ps1` 再执行（PowerShell 内联引号转义容易出错，别直接内联）：

```powershell
$pattern = 'string name="([^"]+)"'
$en = Select-String -Path '<模块>/src/main/res/values/strings.xml' -Pattern $pattern | ForEach-Object { $_.Matches[0].Groups[1].Value }
$zh = Select-String -Path '<模块>/src/main/res/values-zh-rCN/strings.xml' -Pattern $pattern | ForEach-Object { $_.Matches[0].Groups[1].Value }
Compare-Object $en $zh | Where-Object { $_.SideIndicator -eq '<=' } | ForEach-Object { $_.InputObject }
```

## 本地测试服务器

需要连真实服务端验证数据（如接口返回字段、头像 URL 是否可用）时，可使用仓库根目录 `local-test-server.md` 中记录的内网测试服务器地址与账号（纯本地账号，风险可控）。该文件已被 `.gitignore` 排除，**其中的信息与该文件本身都不要提交到远程**。

## 访问抓取站点的代理

`:core` 的 `scraper` 包针对的站点（JavDB / JavBus / Jav321）直连常被墙或 DNS 污染（实测 `javdb.com` 会被解析到一个无关 IP，连接 15 秒后超时）。**需要访问这些站点确认信息时**——核对页面结构、验证图片是否需要 Referer、确认某个演员名或番号在站点上是否存在等——一律用 `local.properties` 里 `scraper.proxy` 配置的本地代理，不要直连。

- 该值只写在本地 `local.properties`（已被 `.gitignore` 排除）；`AGENTS.md` 与仓库代码只说明用法，**不记录地址、也不要提交**。注意它与 App 内设置里的抓取代理 `scrapeProxy` 是两回事，后者是给 App 运行时抓取用的。
- 浏览器请求还须带 `Accept-Language`，否则 JavBus 会被跳到年龄验证页。取值并请求（中文 URL 的 `%` 在 cmd 下会被破坏，复杂命令先存成临时 `.ps1` 再执行）：

  ```powershell
  $proxy = (Select-String -Path local.properties -Pattern '^scraper\.proxy=(.+)$').Matches[0].Groups[1].Value
  Invoke-WebRequest -Uri '<站点地址>' -Headers @{'Accept-Language' = 'zh-CN,zh;q=0.9'} -Proxy $proxy -TimeoutSec 30 -UseBasicParsing
  ```

## 日志规范

所有日志走统一包装类，tag 自动带 `torah` 前缀，logcat 中搜索 `torah` 即可过滤出本应用全部日志。

- **统一入口**：`dev.jdtech.jellyfin.logging.AppLog`（位于 `:logging` 模块）。**新增或修改代码时，打印日志一律用 `AppLog`**，禁止直接写 `Timber.x()`、`android.util.Log.x()`、`println()`。
- **为什么单独建 `:logging` 模块**：各模块之间都是 `implementation`（不传递）依赖，`:data`、`:player:local` 这类模块访问不到 `:core` 里的类（且 `:core` 依赖 `:data`，反向依赖会成环）。只有不依赖任何项目模块的最底层模块才能被所有模块使用。
- **tag 机制**：tag 由 `TorahDebugTree` 统一添加——Timber 默认取调用方类名，Tree 在其前加 `torah/`，最终形如 `torah/MovieViewModel`。业务代码**不要手写 tag、不要自己拼 `torah` 前缀**；确需归类时用 `AppLog.tag("MPV").d(...)`。
- **API 用法**：
  ```kotlin
  AppLog.d("Stream url: $streamUrl") // 也支持占位符：AppLog.d("Stream url: %s", url)
  AppLog.e(exception) // 打印异常堆栈
  AppLog.e(exception, "加载电影 %s 失败", movieId) // 异常 + 说明
  AppLog.tag("MPV").d("mpv 事件：%s", event) // 临时指定业务 tag
  ```
- **级别选择**：`v`/`d` 调试流程，`i` 关键节点，`w` 可恢复的异常，`e` 错误（必须带 Throwable 或写明原因），`wtf` 理论上不该发生的情况。
- **存量代码**：仓库中仍有约 40 处历史 `Timber.*` 调用，**不专门做批量迁移**（Tree 统一加前缀，过滤不受影响）；**改动某个文件时顺手把该文件里的 `Timber.` 替换为 `AppLog.`**，并移除不再使用的导入。
- **敏感信息不落日志**：服务器地址、用户 token、用户名等禁止打印。
- **release 构建**不 plant Tree，自动无日志输出，调用处无需判断 `BuildConfig.DEBUG`，也不要为"性能"手动加条件。
- **新模块需要打日志时**：在其 `build.gradle.kts` 加 `implementation(projects.logging)`。

## 项目概述

Findroid 是 Jellyfin 的第三方 Android 应用——使用原生 Jetpack Compose 界面浏览和播放电影 / 剧集，支持 ExoPlayer 与 mpv 两种视频后端。

## Wiki 知识库

`wiki/` 目录存放按代码生成的项目知识文档，用于快速定位功能实现：

| 文档 | 内容 |
|---|---|
| [wiki/pages.md](wiki/pages.md) | 手机端（`:app:phone`）主要页面的路由、Screen、ViewModel / State / Action 路径与职责 |

**使用方式**：需要定位某个页面或功能时，先查 `wiki/pages.md` 的「页面总览」表，再按表中路径读取源码，避免全库盲目搜索。

**更新机制**：出现以下任一改动后，须同步 `wiki/pages.md`——新增 / 删除 / 重命名页面（Screen、ViewModel、State、Action 文件），路由增删改（`NavigationRoot.kt` 中的 `@Serializable` 路由类或 `composable<...>` 注册块），页面职责变化（新增 section、更换数据来源、改变入口页面）。步骤：以 `NavigationRoot.kt` 为准核对「页面总览」表 → 更新对应条目 → 刷新开头的「最后更新」日期；文中引用的 `.kt` 路径必须真实存在（校验命令见该文档的「维护与更新机制」一节）。

**书写要求**：`wiki/` 内的文档**只写文件路径，不得附带行号**（如 `Foo.kt`，而非 `Foo.kt:123`）；需要精确定位时，用「路径 + 符号（类 / 函数名）」表达。

## 构建命令

```bash
./gradlew :app:phone:compileLibreDebugKotlin  # 只编译 Kotlin（查编译错误用，比 assemble 快）
./gradlew :core:compileDebugKotlin            # 只编译某个库模块（模块无 flavor 时无 Libre 段）
./gradlew :app:phone:assembleLibreDebug    # 手机版 debug
./gradlew :app:phone:assembleLibreRelease  # 手机版 release
./gradlew :app:phone:bundleLibreRelease    # Android App Bundle
./gradlew ktfmtFormat                      # 格式化 Kotlin 代码
./gradlew clean                            # 清理
```

一种 flavor `libre`，三种 build type（`debug`、`release`、`staging`；staging 继承 release，application ID 后缀为 `.staging`）。项目中**没有单元测试或插桩测试**，`check` 仅验证 ktfmt 格式，因此改完代码无需运行它。

**查编译错误时的任务选择**：只编译 Kotlin 的任务名为 `compile<变体名>Kotlin`（变体名由 flavor + build type 拼成，如 `:app:phone:compileLibreDebugKotlin`；库模块没有 flavor 参数时是 `compileDebugKotlin`）。这类任务不打包 APK / AAB，是「验证代码能编过」的最小代价；具体任务名可用 `./gradlew <模块>:tasks --all` 确认。

## 架构

### 模块

| 模块 | 职责 |
|---|---|
| `:app:phone` | 手机应用入口（Compose UI + 导航、`MainActivity`、`BaseApplication`） |
| `:app:tv` | Android TV 应用（当前分支不维护） |
| `:core` | 共享 ViewModel、DI 装配、下载器、后台 Worker、爬虫 |
| `:data` | Jellyfin SDK 封装、Repository、Room 数据库、DTO |
| `:settings` | `AppPreferences` 偏好系统 |
| `:modes:film` `:setup` | 影视 / 引导流程的 ViewModel |
| `:player:core` | 与后端无关的播放器领域模型（`PlayerItem`、`Track`、`TrickplayInfo`） |
| `:player:local` | ExoPlayer / mpv 播放后端、`PlaylistManager` |
| `:logging` | 统一日志入口（`AppLog`、`TorahDebugTree`），不依赖任何项目模块 |

所有模块均使用 Hilt 进行依赖注入。

### 架构模式

**MVVM + 单向数据流。** 每个页面包含：一个 `ViewModel`（暴露 `StateFlow<XxxState>`、处理 `XxxAction` 密封类），一个 `:app:phone` 中的 `@Composable` Screen（观察状态、分发动作），State / Action / Event 与 ViewModel 同层放置。以电影页为例：`MovieState.kt` / `MovieAction.kt` / `MovieViewModel.kt` 在 `:modes:film`，`MovieScreen.kt` 在 `:app:phone`。

### 数据与 API

- 项目使用 **org.jellyfin.sdk:jellyfin-core**（非手写 HTTP）。参见 [doc/DEVELOPMENT.md](doc/DEVELOPMENT.md) 和 [doc/jellyfinapi/Jellyfin_API.md](doc/jellyfinapi/Jellyfin_API.md)。
- `JellyfinApi`（单例，位于 `:data`）封装 SDK 的 `createApi()`，暴露各子 API（`itemsApi`、`userLibraryApi`、`showsApi` 等）。
- `JellyfinRepository` 接口定义所有数据操作；`JellyfinRepositoryImpl`（联网，调 SDK）与 `JellyfinRepositoryOfflineImpl`（离线，只读 Room）两个实现，由 `RepositoryModule` 依据 `AppPreferences.offlineMode` 切换。
- `ItemsPagingSource` 封装 `getItems()`，实现 Paging 3 的偏移分页（随机排序在客户端实现）。
- 单一数据库 `ServerDatabase`（Room，版本 8）：`Server` / `ServerAddress` / `User` 存连接信息，`Findroid*Dto` 存已下载媒体、媒体源、用户数据、Trickplay 与分段。

### 媒体项类型层次

`FindroidItem`（密封接口）→ `FindroidMovie`、`FindroidShow`、`FindroidSeason`、`FindroidEpisode`、`FindroidBoxSet`、`FindroidCollection`、`FindroidFolder`、`FindroidPerson`。SDK 的 `BaseItemDto` 通过扩展函数映射（`toFindroidItem()`、`toFindroidMovie()` 等）。

### 离线模式与下载

`LocalOfflineMode` 是一个 `CompositionLocal`，用于隐藏仅联网可用的功能（如媒体标签页）；仓库层在注入时切换联网 / 离线实现。`Downloader` / `DownloaderImpl`（位于 `:core`）处理媒体项及其资源下载，`ImagesDownloaderWorker` 后台预下载封面图，`SyncWorker` 在网络恢复时把离线用户数据（播放位置、收藏）回传到服务器。

### 播放器

两种后端（均在 `:player:local`）：**ExoPlayer**（Media3，默认，FFmpeg 扩展提供音频编解码支持）与 **mpv**（`dev.jdtech.mpv:libmpv`，`MPVPlayer` 封装 mpv C 库，可启用软件解码）。`PlaylistManager` 管理播放队列，`PlayerViewModel` 暴露播放器状态并接收 `PlayerAction`。

### 后台任务

均在 `BaseApplication` 中通过 WorkManager 调度：`SyncWorker`（回传离线用户数据）、`ImagesDownloaderWorker`（预下载封面图）、`MpvCleanupWorker`（设备空闲且电量充足时清理过期的 mpv 文件）。
