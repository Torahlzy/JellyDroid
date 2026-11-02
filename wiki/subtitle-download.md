# 字幕下载交互改造设计（候选列表 + 用户手选）

## 关于本文件

本文件记录「下载字幕」交互改造的**需求与设计**，状态为**已定稿、尚未实现**。实现完成后请把本文的「现状」与「实现落点」两节更新为实际代码路径，并移除本提示。

- 需求确认日期：2026-11-03
- 涉及模块：`:core`（字幕流水线）、`:modes:film`（MovieViewModel）、`:app:phone`（弹窗 UI）
- 相关代码（当前实现）：
  - `CORE/subtitle/SubtitleDownloader.kt`、`CORE/subtitle/SubtitleDownloaderImpl.kt`
  - `CORE/subtitle/SubtitlecatScraper.kt`、`CORE/subtitle/SubtitleDownloadStage.kt`
  - `CORE/subtitle/SubtitleCandidate.kt`、`CORE/subtitle/SubtitleDownload.kt`、`CORE/subtitle/DownloadedSubtitle.kt`
  - `CORE/subtitle/SubtitleCache.kt`、`CORE/subtitle/SubtitleTiming.kt`
  - `FILM/presentation/movie/MovieViewModel.kt`、`FILM/presentation/movie/MovieState.kt`
  - `PHONE/presentation/film/components/SubtitleDownloadDialog.kt`
  - `PHONE/presentation/film/MovieScreen.kt`

## 一、现状（自动挑选，用户不参与）

用户只能输入关键词，后续全部自动完成：

1. `SubtitlecatScraper.search()` 搜索关键词，取前 5 个候选（每个候选 = 一个字幕标题 / 一个详情页）；
2. `SubtitleDownloaderImpl.resolveAll()` 逐个打开详情页，收集每页可下载的语言（简体 / 繁体 / 英文）；
3. **全局语言优先**：简体一份都没有才降档到繁体，再没有才用英文；不因「繁体更贴合时长」而换语言；
4. 在选定语言内逐份下载，比较字幕最后时间点与视频时长；落进容差（视频时长 2%，下限 1 分钟、上限 3 分钟）即采用，都不在容差内取误差最小的一份；
5. 自动上传到服务器，`MovieViewModel.downloadSubtitle()` 收尾并重新拉取条目。

弹窗只有两个状态：关键词输入（`stage == null`）与整体进度提示（`SubtitleDownloadStage` 的 Searching / Resolving / Downloading / Uploading）。

**问题**：用户看不到站点上都有哪些候选、各自差多少，只能被动接受自动挑选结果；挑错了只能重下一遍。

## 二、目标交互

把「自动挑一份」改成「下载齐 + 展示时长差 + 用户手选上传」。关键词输入阶段保持不变，之后换成候选列表。

### 2.1 阶段划分

| 阶段 | 界面 |
|---|---|
| 关键词输入 | 现状不变：关键词输入框 + 抓取代理入口 + 确定 / 取消 |
| 搜索中 | 列表区显示「搜索中」进度，随后立即切换为候选列表 |
| 候选列表 | 搜索结果返回即展示标题列表，每条按自身进度补全 |
| 上传中 | 被点选那条显示「上传中」，其余行按钮禁用 |
| 结束 | 成功：关弹窗 + 成功提示 + 重新拉取条目；失败：留在列表并提示错误 |

### 2.2 候选列表的流式行为

- 搜索成功后立刻列出前 5 个标题，**不等任何下载完成**；每条右侧先显示 loading；
- 每条独立流水线：打开详情页 → 按**该标题内部** 简体 > 繁体 > 英文 取第一份可下载的 srt / vtt → 下载 → 解析字幕最后时间点 → 与视频时长求差；
- 处理完的条目右侧由 loading 换成带符号时长差，**可以立刻点上传**，不必等其它条目下完；
- 单条失败（无中英文字幕 / 详情页打不开 / 下载失败）只影响该条，标为「不可用」。

### 2.3 列表条目结构

```
┌────────────────────────────────────────────────┐
│ 标题第一行……                          loading  │
│ 标题折行后的第二、三行……      [ -25 秒 ] [上传] │
└────────────────────────────────────────────────┘
```

- **标题必须支持折行**：`Text` 不设 `maxLines` / `overflow`，长标题完整展开，不出现省略号；
- 行的纵向留白足够，两行以上标题不挤在一起；
- 右侧操作区按状态切换：
  - 下载中：小号 `CircularProgressIndicator`；
  - 完成：带符号时长差 + 独立的**「上传」按钮**；
  - 不可用：灰字「不可用」，无按钮、不可点。

### 2.4 时长差的展示

- 带符号：比视频短显示 `-25 秒`，比视频长显示 `+3 分 12 秒`；
- 差值落进容差的条目：推荐色 / 角标，并**排到列表前部**；
- 视频时长未知（服务端没给 runtime）或字幕里没有时间戳：显示「未知」，不算推荐，但仍可选。

### 2.5 上传

- **不做「点整行即上传」**，每个可用的条目后面跟一个明确的「上传」按钮；
- 点某条「上传」后：
  - 该行按钮变「上传中」+ 转圈并禁用，行内给出「正在上传」的明确文案；
  - 其余所有行的「上传」按钮一并禁用，避免重复提交；
  - **取消剩余还在跑的下载任务**（用户已经做出选择，继续下载无意义）；
- **成功**：关闭弹窗、成功提示（含语言）、重新拉取条目让播放源带上新字幕轨；
- **失败**：弹窗留在列表上，给出可见错误提示；失败行恢复为可选，其余行按钮解禁，可重试或改选别的。

### 2.6 边界与失败

| 情况 | 行为 |
|---|---|
| 搜索无结果 | 列表空态「没找到可用字幕」，弹窗保持打开，可改关键词重试 |
| 全部条目不可用 | 同上，列表置灰展示原因 |
| 视频时长未知 / 字幕无时间戳 | 差值显示「未知」，条目可选，不标推荐 |
| 上传失败 | 见 2.5，列表状态可恢复 |

### 2.7 缓存

- 未选中的已下载字节不落缓存：选中即传、其余丢弃；
- `SubtitleCache` 的过期清理逻辑保留即可；若改造后确实不再写入缓存，可一并移除（实现时再定）。

## 三、实现落点（改造方案）

| 位置 | 改动 |
|---|---|
| `SubtitleDownloader` | 接口由「一次性返回单份 `DownloadedSubtitle`」改为**流式返回候选结果**（Flow 或逐条回调），语言优先级由「全局降档」改为「每标题内 简 > 繁 > 英」 |
| `SubtitleDownloaderImpl` | 保留搜索 + 逐页解析，去掉跨候选的语言降档与容差自动选中；改为每条产出「标题 / 语言 / 字节 / 时长差 / 失败原因」 |
| `SubtitleDownloadStage` | 精简为 `Searching` 与 `Uploading`；逐条状态（下载中 / 完成 / 不可用）移入候选数据模型 |
| `MovieState` | 新增候选列表字段（`List<SubtitleCandidateState>`），由流式结果驱动 |
| `MovieViewModel` | `downloadSubtitle()` 收集流式结果更新列表；新增「上传选中项」action；选中时 cancel 剩余下载 job |
| `SubtitleDownloadDialog` | 阶段② 换成可滚动的候选列表（弹窗限高，`LazyColumn` 或 `Column + verticalScroll`），条目三态渲染 |
| `MovieScreen` | 新增上传 action 的分发与错误提示 |
| 字符串资源 | 新增 key 需同步 `:core` 的 `values` / `values-zh-rCN` / `values-zh-rTW` |

建议的新增文案 key（命名按仓库既有风格，实现时可微调）：

- `subtitle_candidate_unavailable`（不可用）
- `subtitle_candidate_unknown_duration`（未知）
- `subtitle_candidate_duration_diff`（时长差，带符号）
- `subtitle_candidate_recommended`（推荐角标）
- `upload_subtitle`（上传）
- `subtitle_uploading_candidate`（上传中 / 正在上传）
- `subtitle_no_candidates`（没找到可用字幕）

## 四、验收要点

- [ ] 搜索完成后列表立即出现，不等下载；
- [ ] 每条下载完成即显示带符号时长差，可独立点「上传」，不必等全部完成；
- [ ] 长标题完整折行、不截断、行内不重叠；
- [ ] 容差内条目推荐色 + 排前；
- [ ] 无中英文字幕 / 下载失败的条目置灰标「不可用」；
- [ ] 点上传后该行「上传中」，其余行按钮禁用，剩余下载被取消；
- [ ] 上传成功关弹窗并刷新条目；失败留在列表且可重选；
- [ ] 中文（简 / 繁）文案齐全，无英文残留。
