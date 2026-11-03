# 字幕下载交互（候选列表 + 用户手选）

## 关于本文件

本文件记录「下载字幕」交互的需求与实际实现。

- **一期（候选列表 + 用户手选上传）：已实现（2026-11-03）**，第一~四节按代码现状书写；
- **二期（播放页多字幕同屏对照）：设计定稿（2026-11-03），已按代码现状审阅修订（2026-11-03），未实现**，
  见第五节。

改动相关行为时以本文为需求基线。

- 需求确认日期：2026-11-03（一期、二期同日确认）
- 涉及模块：`:core`（字幕流水线）、`:modes:film`（MovieViewModel / State / Action）、`:app:phone`（弹窗 UI）
- 相关代码（当前实现）：
  - `core/src/main/java/dev/jdtech/jellyfin/core/subtitle/SubtitleDownloader.kt`（流式接口）
  - `core/src/main/java/dev/jdtech/jellyfin/core/subtitle/SubtitleDownloaderImpl.kt`
  - `core/src/main/java/dev/jdtech/jellyfin/core/subtitle/SubtitleCandidateResult.kt`
  - `core/src/main/java/dev/jdtech/jellyfin/core/subtitle/DownloadedSubtitle.kt`
  - `core/src/main/java/dev/jdtech/jellyfin/core/subtitle/SubtitleDownloadStage.kt`
  - `core/src/main/java/dev/jdtech/jellyfin/core/subtitle/SubtitlecatScraper.kt`
  - `core/src/main/java/dev/jdtech/jellyfin/core/subtitle/SubtitleTiming.kt`
  - `modes/film/src/main/java/dev/jdtech/jellyfin/film/presentation/movie/MovieViewModel.kt`
  - `modes/film/src/main/java/dev/jdtech/jellyfin/film/presentation/movie/MovieState.kt`
  - `modes/film/src/main/java/dev/jdtech/jellyfin/film/presentation/movie/SubtitleCandidateState.kt`
  - `app/phone/src/main/java/dev/jdtech/jellyfin/presentation/film/components/SubtitleDownloadDialog.kt`
  - `app/phone/src/main/java/dev/jdtech/jellyfin/presentation/film/MovieScreen.kt`

## 一、现状

用户在「更多」菜单点「下载字幕」，输入关键词后弹窗切成三步：

1. **搜索中**：`SubtitleDownloaderImpl.download()` 先 `SubtitlecatScraper.search()` 取前 5 个候选，
   立刻以 `SubtitleCandidateResult.Found` 把标题列表发出去；
2. **候选列表**：之后按站点顺序**逐条**处理候选——打开详情页 → 在**这条候选内部**按
   简体 > 繁体 > 英文取第一份可下载的 srt / vtt → 下载 → 解析字幕最后时间点 → 与视频时长求差，
   每处理完一条发一次 `Available` 或 `Unavailable`，界面随即由转圈换成「字幕短 / 长多少」（或「不可用」）；
3. **上传**：用户点某条的「上传」后，取消剩余候选的下载，只把选中的那份传到服务器。

`SubtitleDownloadStage` 只剩两个整批状态：`Searching` 与 `Uploading(candidate)`；逐条的下载进度属于
候选列表里某一行的状态（`SubtitleCandidateState`）。

**已经明确的取舍**：

- **语言优先级从「全局降档」改为「每条候选内部降档」**（原先：所有候选里一个简体都没有才退到繁体。
  现在：每条候选各取自己最好的一份）。因为用户自己挑，所以列表里**必须显示每条的语言**，
  否则会出现「A 只有英文、B 有简体」时盲选。
- **列表不按推荐度重排**。列表是边下边补的，若完成一条就把它移到前面，用户正要点的「上传」会换位置
  （移动靶），容易点错。推荐只体现为推荐色 + 「推荐」角标。
- **搜不到候选不做独立空列表页**：直接回到关键词输入，并在输入框下方红字提示「没找到可用字幕」，
  用户可改关键词重试（弹窗标题不变）。

## 二、目标交互

关键词输入阶段保持不变，之后换成候选列表，由用户手选上传。

### 2.1 阶段划分

| 阶段 | 界面 |
|---|---|
| 关键词输入 | 关键词输入框 + 抓取代理入口 + 确定 / 取消；上次搜索无结果时下方多一行红字提示 |
| 搜索中 | 一行「正在搜索字幕…」进度 |
| 候选列表 | 搜索结果返回即展示标题列表，每条按自身进度补全 |
| 上传中 | 被点选那条显示「上传中」，其余行按钮禁用 |
| 结束 | 成功：关弹窗 + 成功提示 + 重新拉取条目；失败：留在列表并提示错误 |

### 2.2 候选列表的流式行为

- 搜索成功后立刻列出前 5 个标题，**不等任何下载完成**；每条右侧先显示 loading；
- 每条独立流水线：打开详情页 → 按**该标题内部** 简体 > 繁体 > 英文 取第一份可下载的 srt / vtt →
  下载 → 解析字幕最后时间点 → 与视频时长求差；
- 处理完的条目由 loading 换成时长差（「字幕短 27 秒」这类），**可以立刻点上传**，不必等其它条目下完；
- 单条失败（无中英文字幕 / 详情页打不开 / 下载失败）只影响该条，标为「不可用」。

### 2.3 列表条目结构

```
┌────────────────────────────────────────────────┐
│ 标题第一行……                                   │
│ 标题折行后的第二、三行……                        │
│ 简体中文  [推荐]  字幕短 27 秒          [上传]  │
└────────────────────────────────────────────────┘
```

- **标题必须支持折行**：`Text` 不设 `maxLines` / `overflow`，长标题完整展开，不出现省略号；
- 标题下方一条副信息依次是：语言（简体中文 / 繁体中文 / 英文）→ 推荐条目才有「推荐」角标 → 时长差
  （`FlowRow`，窄屏或大字号下自动换行，不会裁掉）；
- 行的纵向留白足够，两行以上标题不挤在一起；行间用 `HorizontalDivider` 分隔；
- 右侧操作区按状态切换：
  - 下载中：小号 `CircularProgressIndicator`；
  - 完成：独立的**「上传」按钮**（时长差已移到左侧副信息里，右侧只放动作）；
  - 不可用：灰字「不可用」，无按钮、不可点。

### 2.4 时长差的展示

- **写成「字幕短 / 长多少」而不是 ±秒**：`字幕短 27 秒`、`字幕长 3 分 12 秒`。只给 `-27 秒` 时
  用户分不清是字幕比视频短还是视频比字幕短；
- 差值不足 1 秒显示「与视频等长」；
- 差值落进容差的条目：加「推荐」角标（容差 = 视频时长 2%，下限 1 分钟、上限 3 分钟，判据在
  `SubtitleDownloaderImpl` 里，随结果以 `isRecommended` 带出）；
- 视频时长未知（服务端没给 runtime）或字幕里没有时间戳：显示「未知」，不算推荐，但仍可选。
- **字幕末尾的假时间码要被忽略**：有些字幕工具会在最后塞一条签名 cue，时间码随手写成
  `23:59:58` 甚至 `99:59:59`（实测 `MUKC-103-zh-TW.srt` 末尾就是 WhisperJAV 的
  `23:59:58,000 --> 23:59:59,000` 签名行），按它算会得出「字幕比视频长 20 小时」。
  计算时以「视频时长 + 容差上界（3 分钟）」为上限，只取不超过上限的最晚时间点；
  若所有时间戳都超出上限（时间轴完全对不上），按「未知」处理。
  不用「跳过最后一条 cue」这类规则：签名 cue 可能有多条，也可能被写在开头。

### 2.5 上传

- **不做「点整行即上传」**，每个可用的条目后面跟一个明确的「上传」按钮；
- 点某条「上传」后：
  - 该行按钮变「上传中」+ 转圈并禁用，行内给出「正在上传」的明确文案；
  - 其余所有行的「上传」按钮一并禁用，避免重复提交；
  - **取消剩余还在跑的下载任务**（用户已经做出选择，继续下载无意义）；
- **成功**：关闭弹窗、成功提示（含语言）、重新拉取条目让播放源带上新字幕轨；
- **失败**：弹窗留在列表上，给出可见错误提示（Toast）；失败行恢复为可选，其余行按钮解禁，
  可重试或改选别的（已下载的字节留在内存里，无需重新下载）。

### 2.6 边界与失败

| 情况 | 行为 |
|---|---|
| 搜索无结果 | 回到关键词输入，输入框下方红字「没找到可用字幕」，可改关键词重试 |
| 全部条目不可用 | 列表保留，逐行灰字「不可用」 |
| 视频时长未知 / 字幕无时间戳 | 差值显示「未知」，条目可选，不标推荐 |
| 上传失败 | 见 2.5，列表状态可恢复 |

### 2.7 缓存

- 已下载的字节只放内存（ViewModel 里的 `downloadedSubtitles`），选中即传、其余丢弃；
- 原先的 `cacheDir/subtitle_temp` 落盘缓存（`SubtitleCache`）在本次改造中**已删除**。

## 三、实现落点

| 位置 | 实现 |
|---|---|
| `SubtitleDownloader` | `download(keyword, videoDurationMs): Flow<SubtitleCandidateResult>`，先发 `Found(标题列表)`，再逐条发 `Available` / `Unavailable` |
| `SubtitleDownloaderImpl` | `channelFlow` 内顺序处理；每条候选内按语言优先级取第一份，`durationDiffMs` 带符号（负数表示字幕比视频短），上界过滤掉超出「视频时长 + 3 分钟」的假时间码，`isRecommended` 由容差算出 |
| `SubtitleTiming` | `lastTimestampMillis(text, maxMs)`：返回不超过 `maxMs` 的最晚时间点，`maxMs` 就是上面那个上界 |
| `SubtitleDownloadStage` | 只剩 `Searching` 与 `Uploading(candidate)` |
| `SubtitleCandidateState` | 三态密封接口：`Loading` / `Ready(language, durationDiffMs, isRecommended)` / `Unavailable` |
| `MovieState` | `subtitleDownloadStage`、`subtitleCandidates`、`subtitleSearchFoundNothing` |
| `MovieViewModel` | `downloadSubtitle()` 收集流式结果写列表；`uploadSubtitle()` 先取消下载任务再上传；失败保留列表；`cancelSubtitleDownload()` 清空并回输入态 |
| `MovieAction` | 新增 `UploadSubtitle(candidate)`；`CancelSubtitleDownload` 语义扩大为「中断搜索 / 剩余下载 / 上传」 |
| `MovieEvent` | 删掉 `SubtitleNotFound`（无结果改由 `MovieState.subtitleSearchFoundNothing` 在弹窗内提示） |
| `SubtitleDownloadDialog` | 三阶段：关键词输入 / 搜索中 / 候选列表（`LazyColumn` + `weight(1f, fill = false)`，弹窗最高 540dp） |
| 字符串资源 | `:core` 的 `values` / `values-zh-rCN` / `values-zh-rTW` 同步新增 |

新增文案 key（均在 `:core`）：

- `subtitle_candidate_unavailable`（不可用）
- `subtitle_candidate_unknown_duration`（未知）
- `subtitle_candidate_recommended`（推荐）
- `subtitle_candidate_duration_seconds`（时长秒数，`%1$d 秒`）
- `subtitle_candidate_duration_minutes`（时长分秒，`%1$d 分 %2$d 秒`）
- `subtitle_candidate_shorter`（字幕短 `%1$s`）
- `subtitle_candidate_longer`（字幕长 `%1$s`）
- `subtitle_candidate_length_matched`（与视频等长）
- `subtitle_uploading_candidate`（上传中）
- `subtitle_no_candidates`（没找到可用字幕）

## 四、验收要点

- [x] 搜索完成后列表立即出现，不等下载；
- [x] 每条下载完成即在副信息行显示「字幕短 / 长 N 秒」，可独立点「上传」，不必等全部完成；
- [x] 长标题完整折行、不截断、行内不重叠；
- [x] 容差内条目推荐色 + 角标（**不重排**，见「已经明确的取舍」）；
- [x] 无中英文字幕 / 下载失败的条目置灰标「不可用」；
- [x] 点上传后该行「上传中」，其余行按钮禁用，剩余下载被取消；
- [x] 上传成功关弹窗并刷新条目；失败留在列表且可重选；
- [x] 中文（简 / 繁）文案齐全，无英文残留。

## 五、二期：播放页多字幕同屏对照（设计定稿，未实现）

日期：2026-11-03。此时**不改动一期已实现的行为**，只在其上追加「下载到的候选先不上传，进播放器同屏看
5 条再挑一条上传」的能力。

### 5.1 目标与核心取舍

- 目标：把「搜索到的前 5 条候选取到的字幕」**同时铺在播放画面上**，用户边看边比，挑一条最合适的，
  确认后**只上传这一条**。上传前任何一条都不写服务器。
- **不用播放器的字幕轨**：ExoPlayer 一个 `TextRenderer` 只能渲染一个选中 group，mpv 也只有 `sid` +
  一个 `secondary-sid`，都撑不住 5 条。改为 App 自己在 `PlayerView` 上叠一层 Compose 对照面板，
  自己解析字幕、自己按播放位置取当前一句。这样两条后端行为一致，也不必改 `trackSelector`。
- 对照面板本身不动 player 层，复用 `PlayerActivity` 已有的覆盖层机制（`activity_player.xml` 的
  `subtitle_adjust_overlay` + `SubtitleAdjustManager` / `SubtitleAdjustOverlay` 是同一套模式）。
- **但「隐藏真实字幕轨」和「应用后切轨」会越过 player 层——原方案写「player 层完全不动」是错的**。
  mpv 侧与 Exo 的重载各加一个 API，只加这三个最小的，不加别的：
  - `MPVPlayer.setSubtitleVisible(visible)`：进对照面板时隐藏真实字幕轨（`sub-visibility`），退出恢复。
    ExoPlayer 侧不用改 player 层，在 `DelayedSubtitleView` 加抑制开关即可。
  - `MPVPlayer.addExternalSubtitle(fileUri, label, language)`：现有 `sub-add` 只写在 `prepareMediaItem()`
    里消费 `mediaItem.localConfiguration.subtitleConfigurations`，没有对外的添加 / 切换入口。
  - `PlayerViewModel.reloadCurrentItemWithExternalSubtitle(fileUri, label, language)`：ExoPlayer 侧把缓存
    文件作为 `MediaItem.SubtitleConfiguration` 注入当前条目并重新 prepare。
- **「应用」是一次重载，不是「保持播放位置重建 MediaItem」**：现有唯一通道 `initializePlayer()` 是
  `setMediaItems + prepare + play`，会重新走 `postPlaybackStop`、重拉 segments / trickplay，播放位置也会
  回到起点。因此应用后必须显式 `seekTo(原位置)`、重载期间暂停进度上报、重载完成后再恢复播放；
  用户会看到一次短暂的重载（黑一下 / 转圈），这个代价写进验收，别指望无声切换。
- 只覆盖**外挂下载字幕**（srt / vtt，与 `SubtitlecatScraper` 现状一致）；内嵌字幕轨不参与对照。ASS 不在范围。
- **本期明确不做**：不暂停、不降速（0.5x）、不做跳句 / 逐句前进、不做同一条候选的简繁双轨对照。
  就是「实时播放 + 5 行同屏」。

### 5.2 入口

| 入口 | 位置 | 行为 |
|---|---|---|
| A | 下载字幕弹窗**底部**新增按钮「播放视频时对比」 | 仅当至少 1 条候选为 `Ready` 时可点，否则禁用。点击后：取消仍在下载的候选 → 把已 `Ready` 的候选（最多 5 条）落盘 → 走**独立关闭路径**关弹窗（不能触发 `onDismiss` 里的 `CancelSubtitleDownload`，否则刚落盘的候选会被清空）→ 跳转播放器并带上「打开对照面板」的 extra。若当前**已经在播放页**打开该弹窗（`PlayerActivity` 是 `singleTask`），不发新播放指令，直接在当前播放器上打开对照面板 |
| B | 视频播放器**右上角菜单**新增「下载字幕」 | 打开与详情页**完全相同**的下载字幕弹窗（同一套 UI 与状态机），流程不变；关闭弹窗后若已有可用候选，可再点「播放视频时对比」进对照 |

入口 A 的跳转链路（原方案未写，必须补）：

- 详情页跳播放器集中在 `MovieScreen.kt` 末尾的 `startActivity(PlayerActivity)`，带 `itemId` / `itemKind` /
  `startFromBeginning` / `EXTRA_MEDIA_SOURCE_INDEX`。入口 A 复用这条路径，**必须原样带上当前的
  `mediaSourceIndex`**，否则会播成另一个版本；再追加 `PlayerActivity.EXTRA_OPEN_SUBTITLE_COMPARE`。
- `PlayerActivity` 的 `onNewIntent` 现在**无条件**重新 `initializePlayer()`。二期要区分两种情况：同一
  `itemId` 只开对照面板、不重开视频；不同 `itemId` 才重开播放。这条分支不写清就会出现「点对比结果
  进度条被重置」。

入口 B 要求弹窗能脱离 `MovieViewModel` 复用：

- 抽出 `SubtitleDownloadController`（放 `:core`，普通类 + `StateFlow`，注入 `SubtitleDownloader` /
  `JellyfinRepository` / `AppPreferences`），承载现有 `subtitleDownloadStage` / `subtitleCandidates` /
  `subtitleSearchFoundNothing` 与 `downloadSubtitle()` / `uploadSubtitle()` / `cancelSubtitleDownload()`
  的编排。
- **不给 controller 加 `@Singleton`**：详情页与播放页各注入一份自己的实例，否则两个弹窗的候选、上传中
  状态互相污染。协程作用域由持有方传入（`MovieViewModel` 用 `viewModelScope`，`PlayerActivity` 用
  `lifecycleScope`），controller 自己不持有不受管的 job。
- **事件也要一起搬**：一期「上传成功（含语言）/ 失败」是 `MovieEvent`。controller 抽取后在 `:core` 定义
  `SubtitleDownloadEvent` + `Channel`，两处各自收集；不要一边读 controller 的 state、一边由 ViewModel 发事件。
- **状态类型要搬家**：`SubtitleCandidateState` 目前在 `:modes:film`，controller 放 `:core` 后必须移进
  `:core`；`MovieState` 的三个字幕字段收敛成一个内嵌的 controller 状态对象。一期**行为与文案不变**，
  但 `MovieScreen` / 弹窗的入参结构会变，实现时按新结构一次改到位。
- `SubtitleDownloadDialog` **已经是纯渲染**（入参是状态 + 回调），不需要「改成纯渲染」；二期只给它加入口 A
  的回调与按钮。
- 不要为了复用给 `:player:local` 加 `:core` 依赖（反向依赖），也不要在 `:player:local` 里再造一套字幕逻辑。

播放页需要的两个上下文（原方案未写）：

- **关键词**：详情页用 `MovieState.defaultScrapeKeyword`（`AvidParser` 从来源路径识别），播放页没有这个值。
  定为：入口 B 打开弹窗时，controller 先 `getMediaSources(itemId)` 取来源路径跑一遍 `AvidParser` 得到
  默认关键词；识别不到就是空输入框，与详情页一致。
- **视频时长**：详情页用条目 `runtimeTicks`；播放页改用 `player.duration`（真实媒体时长更准），尚未 ready
  或为 `C.TIME_UNSET` 时按 0 传，界面显示「未知」。

### 5.3 对照面板

```
20% 黑蒙层只铺这一带（不压暗画面其余部分）
────────────────────────────────────────────────────────────────
① 简体中文 · 字幕短 25 秒     人生就是这样，你永远不知道下一…    [应用]
② 繁体中文 · 字幕短 24 秒     人生就是這樣，你永遠不知道下一…    [应用]
③ 英文 · 字幕短 31 秒         Life is like this, you never…     [应用]
④ 简体中文 · 字幕长 1 分 05 秒  （此刻无台词）                    [应用]
⑤ 简体中文 · 未知             这部电影我看了三遍，每次都有新…     [应用]
────────────────────────────────────────────────────────────────
```

硬性要求：

- **次序**：1~5 对应**落盘快照**里的前 5 条（与搜索结果同序），固定不重排（避免用户点错，与一期
  「不按推荐度重排」同一理由）；可用候选不足 5 条时按实际条数显示，不足的行不占位。
- **每条最多两行**：`maxLines = 2` + `overflow = Ellipsis`，不出现第三行、不撑高行高。单行 12sp 在横屏下
  只剩约 18 个汉字，一句 30~40 字的字幕只能看到前半句，对照价值打折；两行仍在高度预算内
  （5×(2×16+10)+2×4 ≈ 218dp）。
- **行头**：序号 `①`~`⑤`，其后跟语言（简体中文 / 繁体中文 / 英文）与时长差。时长差**沿用一期写法**
  （「字幕短 / 长 N 秒」，不用 ± 号，与 2.4 的结论一致）：复用 `SubtitleDownloadDialog` 里现有的格式化
  逻辑——它现在是私有 `durationDiffText()`，需要先上移到 `:core` 的 `core/presentation/subtitle/`
  供两处共用。文案 key 是 `subtitle_candidate_shorter` / `subtitle_candidate_longer` /
  `subtitle_candidate_length_matched` / `subtitle_candidate_duration_seconds` /
  `subtitle_candidate_duration_minutes` / `subtitle_candidate_unknown_duration`
  （原方案写的 `subtitle_candidate_diff_seconds` / `_diff_minutes` 不存在）。
- **行中**：该候选在当前播放位置的字幕文本；此刻无台词时该行文本区留空（不隐藏行、不加占位文案），
  序号、语言、「应用」按钮照常。**取句位置要叠加已保存的字幕进度偏移**
  （`AppPreferences.getSubtitleOffset(itemId)`，`SubtitleAdjustManager` 已经这么用），否则用户设过 +5 秒时
  面板内容会和画面 / 音轨错位，容易被误判成「这条不对轴」。
- **行尾**：「应用」按钮。
- **背景**：整块对照区域铺 **20% 黑色蒙层**（`#33000000`），让白字在亮画面上可读；不铺满全屏、不压暗
  画面其余部分（方便同时看画面判断是否对轴）。
- **位置**：屏幕上方起、左右各留 12dp，但**必须从顶部控制条下方开始**——顶部一行有 `video_name` 与右侧的
  锁定 / 画中画 / 菜单按钮，面板贴顶会直接压住它们；也不压到底部控制条区域。
- **隐藏真实字幕轨**：进面板时统一关闭当前字幕轨的渲染（Exo 侧在 `DelayedSubtitleView` 加抑制开关，
  `SubtitleAdjustManager.onCues` 不再往下转发；mpv 侧 `setSubtitleVisible(false)`），退出时恢复。
  否则面板 5 行 + 底部真实字幕会在画面上堆成 6 行。
- **跟随播放**：每 100~200ms 用 `player.currentPosition` 重算 5 条各自当前 cue；不暂停、不改倍速。
  轮询协程在 `repeatOnLifecycle(STARTED)` 内起停，进后台即停。
- **画中画时隐藏面板**：`PlayerActivity` 支持 PiP，面板是 Compose 覆盖层，会跟着进小窗。
- **退出**：返回键优先级为**对照面板 > 字幕调节面板 > 退出播放器**（现在的返回回调只判调节模式）；
  两个面板互斥，开对照前先退出调节模式。面板另提供显式关闭入口。

尺寸与上限（按横屏实测算）：

- 字号 12sp、行高 16sp、行内上下 padding 5dp、行间间距 2dp → 单行约 26dp，两行约 42dp；
- 5 行合计约 42×5 + 2×4 ≈ 218dp；
- 参考横屏屏高：1080p 手机约 393dp、720p 手机约 360dp，**2/3 上限约 240~260dp**，218dp 留有余量；
- **面板内把 `fontScale` 固定为 1**（`CompositionLocalProvider(LocalDensity provides Density(density, 1f))`）：
  12sp 会跟随系统字体缩放，最大 2.0 时行高翻倍、两行方案会冲到 300dp 以上，这里必须锁死。面板是辅助
  对照区，不承担无障碍阅读职责，正文阅读仍由播放器字幕轨承担；
- 若后续行内信息变多触碰上限，压缩行间距而不是缩字号。

### 5.4 应用与上传

- 点「应用」→ 先弹二次确认「上传并应用这条字幕？」，带标题与语言；取消则回到面板。
- 确认后调 `JellyfinRepository.uploadSubtitle(itemId, bytes, language.jellyfinLanguage, format)`，
  与一期同一套实现（**同语言会覆盖服务器上的旧字幕**，这是服务端行为，二期不改变；确认文案要把这句说清）。
- 上传成功后按后端切轨（**不是「重建 MediaItem 保持位置」**，代价见 5.1）：
  - ExoPlayer：`PlayerViewModel.reloadCurrentItemWithExternalSubtitle(缓存文件 uri, 标题, 语言)`，重新
    prepare 当前条目并 `seekTo(原位置)`；重载期间暂停进度上报，重载完成后再恢复播放。
  - mpv：`addExternalSubtitle` + 切到新加入的 `sid`。
  - 两条后端都**用本地缓存文件**，不等服务器把新字幕流发回来：上传后服务器重新索引的耗时与
    `MediaStream` 的出现时机不确定，用本地文件才能确定地立即生效；下次播放时服务器上的轨道自然存在。
- 成功：切轨 → 关闭对照面板 → 清理该 `itemId` 的缓存 → 提示成功。**清理必须放在切轨完成之后**
  （ExoPlayer 是异步读取侧载字幕文件，先删会读不到）。
- 失败：**留在对照面板**，Toast 报错，缓存保留，可重试或改选别的（不必重新下载）。
- 应用过程中按钮禁用，避免重复提交。

### 5.5 缓存

- 候选字节落盘：`cacheDir/subtitle_compare/<itemId>/`，文件名用候选 `pageUrl` 的短 hash（**不要用
  `<序号>`**：序号随搜索顺序 / 取消重下变化，容易把 A 的字节当成 B 的），扩展名按实际格式；另加一份
  元信息 JSON：语言（含上传用的 `jellyfinLanguage`）、`format`（srt / vtt，`uploadSubtitle` 必需）、标题、
  `durationDiffMs`、是否推荐位、序号（仅用于显示 ①~⑤）。
- 落盘时机：点「播放视频时对比」时，把已 `Ready` 的候选一次性写入；尚未下完的候选直接取消，不等。
- 写入用「先写 `<name>.tmp` 再 rename」，避免进程中途被杀留下半个文件被当成有效缓存。
- **快照语义**：面板只认这次落盘的那一批。进播放器后再从入口 B 重新搜索，controller 的候选会变，但面板
  不自动刷新（否则 ①~⑤ 指代的字幕会在用户眼皮底下换掉）；要换一批就重新点一次「播放视频时对比」。
- 清理：每次写入前清空该 `itemId` 目录；应用成功（切轨完成）或退出对照后删除；不做全局定时清理
  （`cacheDir` 由系统回收）。
- **一期文档曾记录「落盘缓存已删除」（`SubtitleCache`）**，二期是有意把它加回来——对照需要跨页面存活，
  不能再只放 `MovieViewModel` 内存里。

### 5.6 边界与失败

| 情况 | 行为 |
|---|---|
| 候选全部不可用 / 0 条 `Ready` | 「播放视频时对比」按钮禁用，播放页菜单的「下载字幕」仍可重开搜索 |
| 候选 1~4 条可用 | 按实际条数显示，不补空行 |
| 字幕文件解码失败（UTF-8 / GBK 都解不出文本） | 该行文本区留空但仍可「应用」（上传不依赖解析成功） |
| 缓存缺失（例如进程被杀后 `cacheDir` 被系统回收） | 提示「本地字幕已失效，请重新下载」并打开下载字幕弹窗。**不静默重下**：重下依赖关键词、是网络操作，静默重下看起来像卡住 |
| 视频时长未知 / 字幕无时间戳 | 时长差显示「未知」（一期同款） |
| 系统字体放大到 2.0 | 面板内 `fontScale` 锁 1，行高不变（见 5.3） |
| 进入画中画 | 面板整个隐藏，并同时恢复真实字幕轨的渲染，小窗里仍有字幕 |
| 上传失败 | 见 5.4，留在面板可重试 |

### 5.7 实现落点（规划）

| 位置 | 内容 |
|---|---|
| `:core` 新增 `subtitle/SubtitleCue.kt` | `data class SubtitleCue(startMs, endMs, text)` |
| `:core` 新增 `subtitle/SubtitleCueParser.kt` | SRT / VTT → cue 列表；处理 BOM、CRLF、VTT 的 `NOTE` / `STYLE` 段；编码兜底 UTF-8 → GBK → Big5。时间戳格式与 `SubtitleTiming` 一致，但后者的 `TIMESTAMP` 是 `private val`，需要扩大可见性或另存一份常量，不要复制第二套正则 |
| `:core` 新增 `subtitle/SubtitleCompareCache.kt` | 按 `itemId` 落盘 / 读取 / 清理，格式见 5.5 |
| `:core` 新增 `subtitle/SubtitleDownloadController.kt` | 一期下载状态机的抽取（5.2），连同 `SubtitleDownloadState` / `SubtitleDownloadEvent`，并把 `SubtitleCandidateState` 从 `:modes:film` 迁进来 |
| `:core` 新增 `core/presentation/subtitle/SubtitleDurationText.kt` | 把 `SubtitleDownloadDialog` 里私有的 `durationDiffText()` 上移，供列表与对照面板共用（沿用一期文案 key） |
| `:player:local` `mpv/MPVPlayer.kt` | 新增 `addExternalSubtitle` / `setSubtitleVisible` 两个最小 API（5.1） |
| `:player:local` `presentation/PlayerViewModel.kt` | 新增 `reloadCurrentItemWithExternalSubtitle`（Exo 侧注入侧载字幕并重新 prepare） |
| `:modes:film` `MovieViewModel` | 改为代理 `SubtitleDownloadController`，对外动作保持不变；`MovieState` 的三个字幕字段收敛成内嵌状态对象 |
| `:app:phone` `presentation/film/components/SubtitleDownloadDialog.kt` | 已是纯渲染，只需加入口 A 的回调与底部「播放视频时对比」按钮 |
| `:app:phone` `presentation/film/MovieScreen.kt` | 入口 A 的跳转：带 `mediaSourceIndex` + `EXTRA_OPEN_SUBTITLE_COMPARE` 启动 `PlayerActivity`，并走独立关闭路径避免触发 `onDismiss` 的清理 |
| `:app:phone` `presentation/player/subtitle/SubtitleCompareManager.kt` | 对照状态与 cue 计算，写法对齐 `SubtitleAdjustManager`（`StateFlow` + 监听 player，协程在 `repeatOnLifecycle(STARTED)` 内起停） |
| `:app:phone` `presentation/player/subtitle/SubtitleCompareOverlay.kt` | 最多 5 行面板 Compose UI，锁 `fontScale`，避让顶部控制条 |
| `:app:phone` `presentation/player/subtitle/DelayedSubtitleView.kt` | 加真实字幕轨的抑制开关（对照期间不渲染） |
| `:app:phone` `res/layout/activity_player.xml` | 新增 `subtitle_compare_overlay`（`ComposeView`），与 `subtitle_adjust_overlay` 并列 |
| `:app:phone` `presentation/player/PlayerMenuDialogFragment.kt` | 新增「下载字幕」入口 |
| `:app:phone` `PlayerActivity.kt` | 组装对照 Manager、`onNewIntent` 区分「只开面板 / 重开播放」、返回键优先级、PiP 隐藏面板并恢复字幕轨、应用后切轨 |
| 字符串 | `:core` 新增 key，并同步 `values-zh-rCN` / `values-zh-rTW`；复用一期时长差文案，不新增 `*_diff_*` key |

规划新增文案 key（均在 `:core`）：`subtitle_compare_entry`（播放视频时对比）、`subtitle_compare_apply`
（应用）、`subtitle_compare_apply_confirm_title` / `subtitle_compare_apply_confirm_message`（确认上传，
正文要写明会覆盖同语言旧字幕）、`subtitle_compare_close`（关闭）、`subtitle_compare_cache_missing`
（本地字幕已失效，请重新下载）。

### 5.8 验收要点（二期）

- [ ] 弹窗底部「播放视频时对比」在 0 条可用时禁用、≥1 条可用时可点，点击后不触发 `onDismiss` 的候选清理；
- [ ] 点击后不再上传任何字幕，只落盘并进入播放器；详情页带过去的 `mediaSourceIndex` 与实际播放版本一致；
- [ ] 从详情页点对比时若播放器已在后台，`onNewIntent` 只开面板、不重置播放进度；
- [ ] 播放器右上角「下载字幕」能打开与详情页一致的弹窗，且默认关键词能由来源路径识别出来；
- [ ] 面板按快照条数显示 1~5 行，次序与落盘快照一致，长文本两行省略号、不撑破行高；
- [ ] 5 行总高度不超过屏高 2/3，20% 黑底可读，与顶部控制条、底部控制条都不重叠；
- [ ] 系统字体放大到 2.0 时面板高度不变；
- [ ] 进画中画面板隐藏、真实字幕轨恢复渲染；
- [ ] 播放中跟随刷新，不暂停、不降速，此刻无台词的行留空但可「应用」；设过字幕偏移时面板内容与画面同步；
- [ ] 面板打开期间真实字幕轨不渲染（画面上不出现第 6 行），退出后恢复；
- [ ] 返回键先关面板、再退调节模式、最后才退出播放器；
- [ ] 「应用」必须二次确认，取消不上传；
- [ ] 上传成功后经历一次重载：该条成为当前字幕轨、播放位置基本连续、缓存被清理；失败留在面板可重试；
- [ ] ExoPlayer 与 mpv 两条后端行为一致；
- [ ] 简 / 繁中文文案齐全。

### 5.9 待确认

只剩一点实现前需要拍板：

1. **20% 黑蒙层的范围**：只铺对照面板所占的带状区域（当前写法），还是整屏压暗。

原先的「对照期间是否隐藏真实字幕轨」已定：**必须隐藏**（做法见 5.3「隐藏真实字幕轨」），否则面板 5 行
与底部真实字幕会堆成 6 行，画面基本不可用。
