# 字幕下载交互（候选列表 + 用户手选）

## 关于本文件

本文件记录「下载字幕」交互的需求与实际实现。

- **一期（候选列表 + 用户手选上传）：已实现（2026-11-03）**，第一~四节按代码现状书写；
- **二期（播放页多字幕同屏对照）：已实现（2026-11-03）**，见第五节；实现时的取舍与偏差记录在 5.10，
  验收仍待真机确认（5.8 的勾选只在真机走过一遍后才打）。

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
| A | 下载字幕弹窗**底部**新增按钮「播放视频时对比」 | 仅当至少 1 条候选为 `Ready` 时可点，否则禁用。点击后：把已 `Ready` 的候选（最多 5 条）落盘 → 走**独立关闭路径**关弹窗（不能触发 `onDismiss` 里的 `CancelSubtitleDownload`，否则刚落盘的候选会被清空）→ 跳转播放器并带上「打开对照面板」的 extra。**剩余候选不取消，继续下载**：每下完一条就补进同一份快照，面板自己多出一行（详见 5.6）。若当前**已经在播放页**打开该弹窗（`PlayerActivity` 是 `singleTask`），不发新播放指令，直接在当前播放器上打开对照面板 |
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
每行各自一块 30% 黑蒙层（不给整个面板铺一整片）
┌──────────────────────────────────────────────────────────────┐
│ ① 字幕短 25 秒      人生就是这样，你永远不知道下一…     [应用] │
└──────────────────────────────────────────────────────────────┘   ← 每行一个圆角块
┌──────────────────────────────────────────────────────────────┐
│ ② 字幕短 24 秒      人生就是這樣，你永遠不知道下一…     [应用] │
└──────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────┐
│ ④ 字幕长 1 分 05 秒  （此刻无台词）                     [应用] │
└──────────────────────────────────────────────────────────────┘
```

硬性要求：

- **次序**：1~5 对应**落盘快照**里的前 5 条（与搜索结果同序），固定不重排（避免用户点错，与一期
  「不按推荐度重排」同一理由）；可用候选不足 5 条时按实际条数显示，不足的行不占位。
- **每条最多两行**：`maxLines = 2` + `overflow = Ellipsis`，不出现第三行、不撑高行高。单行 18sp 在横屏下
  约 12 个汉字，两行约 24 个；一句 30~40 字的字幕只能看到大半句。**18sp 是「两行 + 5 条候选」这个高度
  预算的上限**：再往上要么 5 行占满整屏，要么每行只剩半句。
- **行头**：序号 `①`~`⑤`，其后跟时长差；**不写语言名**——候选基本都是中文字幕，写出来只是白占
  横向空间，简体 / 繁体看文本一眼能分。行头与字幕文本是**两段独立文本**，不要混排进同一个
  `AnnotatedString`：14sp 的行头混在 18sp 正文里只会基线对齐，小字看着比正文高一截。
- **行内对齐**：**行头靠左**（本身也在行高中线），**字幕文本在剩余区域上下左右都居中**
  （`Box(weight(1f).heightIn(min = ...), contentAlignment = Center)` + `Text(fillMaxWidth, textAlign = Center)`），
  「应用」按钮固定最右。注意**最小高度必须加在 Box 上而不是 Text 上**：`Text` 被 `heightIn` 撑高后
  文字仍画在容器顶部，看着就是「上下没居中」。
  时长差**沿用一期写法**
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
- **背景**：**每一行各自**铺 30% 黑色蒙层（`#4D000000`，圆角 8dp），不要给整个面板铺一整片：
  横屏下 5 行加起来接近整屏，一整片半透明黑会把画面压掉大半，就没法一边看画面一边判断对轴了。
- **位置**：屏幕上方起、左右各留 12dp，但**必须从顶部控制条下方开始**——顶部一行有 `video_name` 与右侧的
  锁定 / 画中画 / 菜单按钮，面板贴顶会直接压住它们；也不压到底部控制条区域。
- **关闭入口的位置**：固定在面板右上角一条 40dp 高的带里（按钮 26dp + **14dp 空隙**），**用 Box 定位、
  不参与行流**，各行从这条之下开始排。两个真机踩过的坑：① 和行一起排在 `Column` 流里会压到第一条字幕；
  ② 空隙只留 2~4dp 时，两个半透明块的圆角糊在一起，看着仍像「盖住」。
- **隐藏真实字幕轨**：进面板时统一关闭当前字幕轨的渲染（Exo 侧在 `DelayedSubtitleView` 加抑制开关，
  `SubtitleAdjustManager.onCues` 不再往下转发；mpv 侧 `setSubtitleVisible(false)`），退出时恢复。
  否则面板 5 行 + 底部真实字幕会在画面上堆成 6 行。
- **跟随播放**：每 100~200ms 用 `player.currentPosition` 重算 5 条各自当前 cue；不暂停、不改倍速。
  轮询协程在 `repeatOnLifecycle(STARTED)` 内起停，进后台即停。
- **画中画时隐藏面板**：`PlayerActivity` 支持 PiP，面板是 Compose 覆盖层，会跟着进小窗。
- **退出**：返回键优先级为**对照面板 > 字幕调节面板 > 退出播放器**（现在的返回回调只判调节模式）；
  两个面板互斥，开对照前先退出调节模式。面板另提供显式关闭入口。

尺寸与上限（按横屏实测算）：

- 正文 18sp、行高 24sp（行头与按钮 14sp、关闭 13sp），行内上下 padding 3dp、行间间距 4dp →
  单行约 30dp，两行约 54dp；
- 5 行合计约 54×5 + 4×4 ≈ 286dp，加「关闭」带 40dp ≈ 326dp（只有 2~3 条候选时约 150~210dp）；
  再加顶部让位 64dp 约 390dp——1080p 横屏（约 393dp）刚好，720p 横屏（约 360dp）满 5 条会顶到屏幕底，
  届时优先压缩行间间距而不是字号；
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
- **弹窗里的「上传」（不经对照面板）也走同一套切轨**：上传只改服务器，当前 `MediaItem` 不会自动多出
  这条轨，「选择字幕轨」自然看不到新字幕。因此在播放页上传成功后，用
  `SubtitleCompareCache.stageForPlayback()` 把同一份字节写成 `manual-<hash>.<ext>`（**不写进 manifest**，
  不进对照面板），立刻侧载 + 切轨，就绪后删掉该临时文件。详情页没有播放器，忽略即可。

### 5.5 缓存

- 候选字节落盘：`cacheDir/subtitle_compare/<itemId>/`，文件名用候选 `pageUrl` 的短 hash（**不要用
  `<序号>`**：序号随搜索顺序 / 取消重下变化，容易把 A 的字节当成 B 的），扩展名按实际格式；另加一份
  元信息 JSON：语言（含上传用的 `jellyfinLanguage`）、`format`（srt / vtt，`uploadSubtitle` 必需）、标题、
  `durationDiffMs`、是否推荐位、序号（仅用于显示 ①~⑤）。
- 落盘时机：点「播放视频时对比」时把已 `Ready` 的候选写入；**剩余候选不取消、继续下载**，每下完一条就
  增量补进同一份快照（`upsert(itemId, mapOf(行号 to 字幕))`），写入后经 `SubtitleCompareCache.updates`
  通知播放页重新载入面板，新行自己长出来（详见 5.10）。
- **行号按候选位置固定**（候选在搜索结果里的下标 + 1），不按完成顺序：第 1 条还在下、第 2 条先好时
  面板显示 ②，等 ① 补进来也不会挤动已有行。
- 写入用「先写 `<name>.tmp` 再 rename」，避免进程中途被杀留下半个文件被当成有效缓存；写入是**合并**语义，
  不清空目录、不重排已有行（面板可能正开着）。
- **快照语义**：面板只认同一个 `itemId` 下不断累积的那一批。从入口 B 重新搜索会重置 controller 的候选并
  断开对照关联，旧快照仍留在磁盘上，直到关闭面板或应用成功时清理；要换一批就重新点一次
  「播放视频时对比」（写入时同行号会覆盖）。
- 清理：应用成功（切轨完成）或退出对照后删除整个 `itemId` 目录；不做全局定时清理（`cacheDir` 由系统回收）。
- **一期文档曾记录「落盘缓存已删除」（`SubtitleCache`）**，二期是有意把它加回来——对照需要跨页面存活，
  不能再只放 `MovieViewModel` 内存里。

### 5.6 边界与失败

| 情况 | 行为 |
|---|---|
| 候选全部不可用 / 0 条 `Ready` | 「播放视频时对比」按钮禁用，播放页菜单的「下载字幕」仍可重开搜索 |
| 候选 1~4 条可用 | 按实际条数显示，不补空行 |
| 下载未完成就点了「播放视频时对比」 | 已 `Ready` 的先落盘并进面板，剩余继续下载；每下完一条补进快照，面板自动多出一行（行号按候选位置，不挤动已有行） |
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

> **已定案（2026-11-03 用户反馈后）**：两个方案都不用——**每一行各自铺 30% 黑蒙层**（`#4D000000`，
> 圆角 8dp）。整块铺无论 20% 还是 30%，横屏 5 行时都会压掉大半画面。

### 5.10 实现记录（2026-11-03）

按本节的规划实现，与原方案不同的地方如下（都是实现时才发现的具体约束）：

1. **状态类型按仓库约定拆文件**：原方案写「`SubtitleDownloadController.kt` 连同
   `SubtitleDownloadState` / `SubtitleDownloadEvent`」，实际拆成独立文件
   （`SubtitleDownloadState.kt` / `SubtitleDownloadEvent.kt`），`SubtitleCandidateState.kt`
   从 `:modes:film` 整体迁到 `:core`，符合仓库「一个文件一个主要类型」的约定。
2. **controller 不注入 `AppPreferences`**：抓取代理的读写留在持有方（详情页走
   `MovieAction.UpdateScrapeProxy`，播放页直接写偏好并镜像到自己的 StateFlow），
   controller 只依赖 `SubtitleDownloader` / `JellyfinRepository` / `SubtitleCompareCache` 三样。
3. **「播放视频时对比」放在候选列表正下方**，而不是与「取消」同一行的按钮区：
   `BaseDialog` 的按钮行只有取消 / 确定两个槽位，硬塞第三个会把两侧挤在一起。
4. **应用后清缓存前先等侧载字幕轨出现**（最多 8 秒）：原方案只说「清理必须放在切轨之后」，
   而 ExoPlayer 读侧载字幕是异步的，光靠「调用顺序」不足以保证读完。最初实现等的是播放器回到
   `STATE_READY`，但重载刚发起时状态往往还是旧的 READY，等于没等，文件随即被删，
   「选择字幕轨」里始终没有新轨（2026-11-08 修复，见第 24 条）。现改为监听 `onTracksChanged`，
   等到文本组数量增加或出现带该 label 且 isSupported 的文本组才清理文件，
   超时则保留文件交给缓存回收（服务器字幕轨的 label 可能为空，不能只按标签找）。
5. **重载期间跳过 `onMediaItemTransition` 的整套处理**：注入侧载字幕会用 `setMediaItems` 重建播放列表，
   会触发一次条目切换；不跳过会重复 `postPlaybackStart`、重拉 segments / trickplay，
   并往播放列表里再插一遍上 / 下一集。进度上报同样用 `isReloadingForSubtitle` 挡住。
6. **mpv 侧切轨用 `sub-add <path> select` 一次完成「加入 + 选中」**，没有再单独查一次 `sid`；
   路径优先传裸文件路径（`Uri.scheme == "file"` 时取 `path`），与现有 `sub-add` 的写法一致。
7. **播放页的字幕关键词在进播放页时就预先识别**（`getMediaSources` + `AvidParser`），
   而不是等用户点「下载字幕」才联网：否则用户打开弹窗时输入框先是空的，随后被覆盖。
   弹窗内输入框也做了「用户还没输入时跟随默认关键词补上」的处理。
8. **画中画退出后会自动把面板开回来**（进小窗前记一个标记，缓存不清），
   原方案只要求「进画中画隐藏面板」。
9. **`SubtitleTiming.TIMESTAMP` 与时间戳换算改成模块内可见**，供 `SubtitleCueParser` 复用同一套正则，
   没有复制第二份（原方案的这条要求已照做）。
10. **面板行头去掉语言名**（2026-11-03 用户反馈）：只留序号与时长差，语言看文本一眼能分。
11. **「下载没完也能先比」改为默认行为**（2026-11-03 用户反馈）：原方案是「取消剩余下载、只落盘已 Ready 的」，
    实测一次搜索要下十几秒，用户看到前两条可用就想先比着看。现在剩余候选继续下载，每完成一条
    `upsert` 进同一份快照并经 `SubtitleCompareCache.updates`（`SharedFlow`，`@Singleton` 才能跨页通知）
    让播放页重新载入面板；行号固定为「候选在搜索结果里的下标 + 1」，避免新行挤动已有行。
12. **ICU 正则坑（真机血泪）**：`Regex("""<[^>]*>|\{[^}]*}""")` 在桌面 JVM 合法，在 Android 上会让
    `SubtitleCueParser` 类初始化直接抛 `PatternSyntaxException`（结尾裸 `}` 被当量词），表现为整批字幕
    「解析出 0 条 cue」且面板全空。花括号必须写成 `[{]... [}]`。**Android 上的正则改动一律在真机验证**，
    桌面跑通不代表能用。
13. **蒙层从「整块 20%」改成「每行 30%」**（2026-11-03 用户反馈）：横屏 5 行合计接近整屏高度，整块蒙层
    看起来像把画面遮住了；改成每行一个独立圆角块（30% 黑、8dp 圆角），行内上下 padding 收到 3dp、
    行间 4dp。关闭入口也套同色小块，避免白字落在亮画面上看不清。
14. **正文字号 12sp → 18sp**（2026-11-03 用户反馈「太小」）：播放器场景观看距离比刷列表远，12sp 明显偏小。
    18sp 是两行文本 + 5 条候选的高度上限（5 行约 286dp）；行头与按钮同步到 14sp、关闭 13sp，维持主次层级。
    **仍嫌小的话**：要么把 `maxLines` 降到 1 换更大字号（每行只剩约 8 个汉字），要么把候选上限从 5 降到 3。
15. **上传成功后立即侧载**（2026-11-04 用户反馈「上传完成后选择字幕轨里没有新的」）：原设计里「上传」只
    写服务器，理应「下次播放自然出现」，但用户当场就要在轨道列表里看到并使用它。现在
    `SubtitleDownloadEvent.Uploaded` 带上 `DownloadedSubtitle`，播放页据此 `stageForPlayback()` 落一个
    临时文件并走与「应用」相同的侧载切轨（Exo 重载 / mpv `sub-add select`）。另外每次切轨就绪后打印
    `当前字幕轨共 N 组：label/language`——这行等于「选择字幕轨」菜单里会显示的内容，用于判断是没注入
    还是播放器没识别。
16. **「关闭」从行流里挪出来**（2026-11-04 用户反馈「关闭按钮盖住第一条字幕」）：原先它作为 `Column` 的
    第一个子项、与行距只有 4dp，真机上压住了第一行。改成 `Box` + `Alignment.TopEnd` 固定在右上角
    30dp 带内，`Column` 用 `padding(top = CloseRowHeight)` 显式让位，物理上不会再重叠。
17. **行头与字幕改为垂直居中**（2026-11-04 用户反馈）：原来是 `AnnotatedString` 内联混排（14sp 行头 +
    18sp 正文），只有基线对齐，小字视觉上比正文高一截。改成 `Row(verticalAlignment = CenterVertically)`
    里两段独立 `Text`，行头在整行（含两行正文）的中线上。
18. **行头靠左 + 字幕上下左右居中**（2026-11-04 用户反馈，两次澄清后定稿）：先做过「整块居中」，
    但用户要的是「说明靠左、字幕在行内上下左右居中」。最终结构：`Row(CenterVertically)` 里
    行头 → `Box(weight(1f).heightIn(min))`（`contentAlignment = Center`）里的 `Text(fillMaxWidth,
    textAlign = Center)` → 「应用」。
    **坑**：最小高度加在 `Text` 上时文字画在容器顶部，看起来「上下没居中」；必须加在 `Box` 上。
18. **新一轮对比先清空快照**（2026-11-04 审核修正）：`upsert` 按行号合并，是「边下边比」的追加语义；
    若上一轮快照还留在 `cacheDir`（未关闭面板就离开播放页等），新一轮候选更少时旧行号会一起进面板，
    显示用户这次根本没下过的字幕。现在由 `SubtitleDownloadController.prepareCompare()` 在新批次
    （`compareItemId == null`）落盘前先 `clear`。
19. **快照的清理时机补齐**：面板关闭 / 应用成功之外，播放器内部切集（上 / 下一集）与退出播放页也会删快照；
    后者用 `SubtitleCompareCache.clearAsync()`（缓存自带的 IO 作用域），因为 `onDestroy` 时
    `lifecycleScope` 已不可用，跟着它启动的清理会被直接取消。
20. **播放页的下载弹窗套 `FindroidTheme`**：另两个覆盖层都套了，漏了这处会让同一个弹窗在播放页里
    用 M3 默认（浅色）配色；离线模式下该菜单项与「截图设为封面」一样不显示。
21. **「当前操作条目」取播放器当前条目**：自动切集后启动参数里的 `itemId` 已过期，上传 / 侧载若沿用它
    会落到上一集，因此统一走 `viewModel.currentItemId ?: playedItemId`。
22. **对照快照引入批次令牌**（2026-11-04 审核修正）：详情页与播放页各持一份 controller，却写同一个单例
    缓存；若快照在「边下边补」期间被别处 clear（另一边点了新一轮对比、退出播放页），旧批剩余候选会按
    行号合并进新快照，把用户这轮没选过的字幕顶进面板。现在 `SubtitleCompareCache` 为每个条目记一个
    批次令牌（`clear` 一次递增一次），`upsert` 携带令牌校验、不匹配即拒绝写入（抛
    `IllegalStateException`，调用方据此作废本批关联）。
23. **离线模式不识别字幕关键词**（2026-11-04 审核修正）：播放页进页时的关键词识别依赖服务器来源路径，
    离线来源是本地路径识别不出，且菜单里没有「下载字幕」入口，直接跳过。
24. **上传 / 应用后「选择字幕轨」仍没有新轨的修复**（2026-11-08 用户反馈）：根因是
    `SubtitlePlaybackCoordinator.awaitPlaybackReady()` 在注入侧载字幕的异步操作刚返回时就命中
    `playbackState == STATE_READY` 立即返回，随后 `file.delete()`（弹窗上传路径）或
    `compareCache.clear()`（对照「应用」路径）把还没被播放器读取的字幕文件删掉，轨根本没形成。
    现改为 `awaitSubtitleTrack(label)`：监听 `onTracksChanged`，等 `currentTracks` 里出现带该 label
    且 isSupported 的文本组才清理文件；等不到则保留文件（切集 / 退出播放页由 `clear` 清，
    `cacheDir` 也可由系统回收）。`logTextTracks()` 同步标出 supported / selected，
    口径与 `TrackSelectionDialogFragment` 一致，便于一眼区分「没注入」与「轨不被支持」。
25. **字幕站变体时间行与编码兜底**（2026-11-09 用户反馈）：`aisubs.app` 的 srt 时间行写作
    `00：00：00.000-> 00：00：03.000`（全角冒号 + 单短横箭头），不是标准 SRT。时间戳正则只认
    ASCII 冒号、cue 解析只认 `-->`，真机上同期出现「时长差全是未知」与「对照面板 0 条 cue、
    每行文本区全空」；直接上传 / 侧载时 ExoPlayer 的 SubRipParser 与服务端同样解析不出内容。
    现在 `SubtitleTiming.TIMESTAMP` 兼容全角冒号、`SubtitleCueParser` 用 `contains("->")` 识别
    时间行，并在 `SubtitleDownloaderImpl` 下载后由 `SubtitleTiming.normalizeTimingLines()`
    把时间行写成标准 SRT / VTT 字节，让时长判断、落盘、上传、侧载、面板解析共用同一份数据。
    编码统一走新增的 `SubtitleTextDecoder`（UTF-8 → GBK → Big5 严格解码，返回文本与实际编码）：
    规范化只改「含箭头且至少两个时间戳」的行，台词里的全角冒号保持原样，改写后按原编码写回字节，
    因此 GBK / Big5 字幕也能被规范化且不会把台词弄乱。**遗留**：已落盘旧快照的
    `manifest.json` 里 `durationDiffMs` 仍是 null，重新搜索一次才会刷新。另外本条又动了
    Android 上的正则，按第 12 条的教训需真机确认类初始化不抛 `PatternSyntaxException`。
26. **上传后当前会话仍看不到字幕轨的真正根因修复**（2026-11-09 用户按第 24 条加的
    `AppLog.tag("SubSideload")` 诊断日志回传 logcat 后定位）：日志显示 `replaceMediaItem` 后 BUFFERING → READY
    确实发生了，但 8 秒里一次 `onTracksChanged` 都没有、`currentTracks` 文本组始终 0；
    退出重播则 `setMediaItems` 后立刻 `onTracksChanged：总组=3，文本组=1`。
    即**在已 READY 的播放器上原地 `replaceMediaItem` 不会把新的 `SubtitleConfiguration` 应用上**，
    重开播放走的 `setMediaItems` 才会。现把 `PlayerViewModel.reloadCurrentItemWithExternalSubtitle()`
    改为：重新取一次服务器媒体源里刚索引好的外挂字幕（`PlaylistManager.getExternalSubtitles()`，
    与构造 `PlayerItem` 共用同一套 MediaStream 映射），更新当前 `PlayerItem` 后用
    `setMediaItems(列表, 当前 index, 当前位置)` 重建播放列表并保住播放状态；服务器还没索引好时
    才注入本地侧载文件，且两者不叠加，避免轨道列表里出现两条一样的字幕。
    `awaitSubtitleTrack()` 的判据同时改为「文本组数量增加或 label 命中」
    （服务器字幕轨的 label 可能为空）。**验收**：上传后菜单里的「选择字幕轨 / 字幕设置」
    应从置灰变为可用（菜单按 `currentTracks` 里 isSupported 的文本组判断），无需重播。
27. **2026-11-09 工作区审核修正**：第 26 条的「服务器还没索引好才注入本地侧载」原判据写成
    `serverSubtitles.isEmpty()`，但影片自带外挂字幕时服务器列表非空却不含刚上传的那条，
    兜底永不生效；现按「服务器外挂字幕数比上传前变多」判断是否已索引，重取失败时保留
    播放会话原有列表（不再用空表覆盖，否则原有外挂字幕会一起丢失）。对照「应用」路径在
    `awaitSubtitleTrack()` 超时时原先仍无条件 `compareCache.clear()`，会删掉还没读完的侧载
    文件、重现第 24 条的 bug；现超时保留整份快照并调用
    `SubtitleDownloadController.invalidateCompareBatch()` 结束本批，下一轮 `prepareCompare()`
    因关联作废重新落盘。此外时间行规范化原先只吃箭头右侧空白，标准 SRT / VTT 会被改成
    双空格并整份重编码，现两侧空白一起归一，合规字幕直接返回原字节；`SubtitleCueParser`
    识别时间行也补上「至少两个时间戳」判据，避免台词里的 `->` 抢走时间行。诊断日志统一
    走 `AppLog.tag("SubSideload")`，logcat 按 `torahLog/SubSideload` 过滤即可。
28. **快进快退后字幕全部消失的修复**（2026-11-10 用户反馈）：上传后黑一下仍有，那是第 26 条
    `setMediaItems` 重建播放列表的已知代价。但本次会话的字幕是 `cacheDir` 临时文件侧载，
    第 24 条的 `awaitSubtitleTrack()` 只等到「轨道清单出现」就 `file.delete()` / `clear()` 删文件；
    `onTracksChanged` 不代表播放器此后不再读这个 Uri，seek 会让文本轨重新取数据，
    文件已不在 → 字幕全无。退出重播走服务器上已索引好的持久 URL，不依赖临时文件，所以正常。
    现把侧载文件移到独立的 `cacheDir/subtitle_sideload/<itemId>/`，弹窗「上传」与对照「应用」
    两条路径都用 `stageForPlayback()` 写独立副本，播放会话内一律不删，只由 `release()` 的
    `clearAsync()` 在播放页销毁时回收；`clear()` 只删对照快照，不再波及侧载文件。
    `awaitSubtitleTrack()` 降级为纯诊断日志。**验收**：上传后快进 / 快退字幕仍在，
    「选择字幕轨」里仍有该轨，退出重播仍正常。
