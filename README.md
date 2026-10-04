![Findroid banner](images/findroid-banner.png)

# MyFindDroid

本仓库是 [Findroid](https://github.com/jarnedemeulemeester/findroid) 的个人分支（fork）。Findroid 是 Jellyfin 的第三方 Android 客户端，提供原生界面用于浏览和播放电影/剧集。

上游项目完整的功能介绍、截图、翻译、构建命令、许可等信息，请直接参考官方仓库及其 README：

- 官方仓库：<https://github.com/jarnedemeulemeester/findroid>
- 官方文档：<https://github.com/jarnedemeulemeester/findroid/blob/main/README.md>

本文件不再重复官方说明，仅记录本分支中自行开发或调整的内容。

## 本分支自定义功能

### 下载项详情查看

在“下载”页面，已下载的媒体项卡片右下角会显示一个信息按钮，点击后弹出对话框，展示该项的标题与本地文件路径，方便确认下载位置。

相关实现：
- 新增对话框组件 `DownloadDetailsDialog`
- `ItemCard` 新增 `onDetailsClick` 回调，仅对已下载项显示信息按钮
- `CollectionAction` 新增 `OnItemDetails` 动作
- 新增字符串 `download_details`、`download_file_path`

### 播放器亮度记忆优化

改进了播放器中通过手势调节屏幕亮度的记忆逻辑：

- 仅当用户手动调整过亮度时才覆盖系统亮度；若从未手动调节（记忆值为初始值 `-1.0`），则保持系统亮度不变，不再自动覆盖。
- 亮度手势结束时立即保存当前亮度，避免退出播放器后才写回导致记忆不准。
- 不再依赖“记住亮度”开关，行为更符合直觉。

相关实现：`PlayerGestureHelper`、`PlayerActivity`。

### 播放器控制栏 UI 调整

- 控制栏容器背景改为透明，修复播放画面被背景色遮挡的问题。
- 播放/快进/后退等控制按钮改为左下角对齐，缩小按钮间距与内边距，布局更紧凑。

相关实现：`app/phone/src/main/res/layout/exo_main_controls.xml`。

### 网络请求主线程异常修复

修复引导流程中添加服务器、登录时的 `NetworkOnMainThreadException`，将网络操作放入 `Dispatchers.IO` 执行，避免主线程发起网络请求导致崩溃。

相关实现：`SetupRepositoryImpl`。

### 分页重复项修复

服务端基于偏移量的分页并不保证稳定：当排序字段存在大量相同值时（例如 `DatePlayed`，未播放的项目取值相同），同一项目可能被多个分页重复返回，导致惰性网格抛出 “Key was already used” 异常。现已对分页结果按项目 id 去重。

相关实现：`ItemsPagingSource`。

## 与上游同步

本分支会不定期将上游 `main` 的更新合并进来，同时保留上述自定义改动。若需要上游的原生功能说明，请以上文官方仓库为准。

## 授权

本分支继承上游项目，基于 [GPLv3](LICENSE) 授权。原项目名称、Logo 及相关商标归原作者与各自所有者所有。
