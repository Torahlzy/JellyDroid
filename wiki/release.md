# 发版步骤（GitHub Release）

最后更新：2026-10-28

本分支发版 = **本机构建并签名 release APK + 手工创建 GitHub Release**，不走 GitHub Actions。

不走上游 CI 的原因：`.github/workflows/publish.yaml` 只对 `v*` 标签触发，需要 5 个 secrets（keystore 与 Google Play 上传凭据），本仓库均未配置，且它的最后一步是 `upload_to_play_store`（Play 上架）。本分支只发 GitHub Release，标签统一用 `release-X.Y`，不会命中 `v*`，也就不会误触发那个必然失败的 workflow。

## 1. 升级版本号

改 `buildSrc/src/main/kotlin/Versions.kt`：

```kotlin
const val APP_CODE = 210      // versionCode，十进制递增：2.0=200、2.1=210、2.2=220
const val APP_NAME = "2.1"    // versionName，与标签 release-2.1 对应
```

`APP_CODE` 必须单调递增，否则新包无法覆盖安装旧包。`:app:phone` 与 `:app:tv` 都从这里取版本，改一处即可。

## 2. 准备签名

`app/phone/build.gradle.kts` 从 `local.properties`（已被 `.gitignore` 排除）读取四项，四项齐全才注册 release 签名：

```properties
release.store.file=
release.store.password=
release.key.alias=
release.key.password=
```

任一项缺失时 release / staging 会构建成**未签名包**：既装不上，也不能发布。发版前先确认这四项非空，并确保 keystore 与上一版是同一个——换签名会导致老用户必须卸载重装。

## 3. 构建

必须用 JDK 21（Gradle 配置按 Java 21 编译，JDK 17 会在 `:settings:compileDebugJavaWithJavac` 报「无效的源发行版：21」）：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12.1'
./gradlew :app:phone:assembleLibreRelease
```

耗时约 5 分钟。产物在 `app/phone/build/outputs/apk/libre/release/`，按 ABI 拆成 4 个：

| 文件 | 适用设备 |
|---|---|
| `phone-libre-arm64-v8a-release.apk` | 绝大多数手机 |
| `phone-libre-armeabi-v7a-release.apk` | 32 位老设备 |
| `phone-libre-x86_64-release.apk` | x86_64 模拟器 / 平板 |
| `phone-libre-x86-release.apk` | x86 模拟器 |

需要 Play 的 AAB 时另跑 `./gradlew :app:phone:bundleLibreRelease`（ABI split 在 bundle 任务下自动关闭）；本分支当前不发 Play。

## 4. 校验产物

用 SDK 的 build-tools 核对版本号与签名（`<sdk>` 取 `local.properties` 的 `sdk.dir`）：

```powershell
& '<sdk>\build-tools\37.0.0\apksigner.bat' verify --print-certs '<apk 路径>'
& '<sdk>\build-tools\37.0.0\aapt2.exe' dump badging '<apk 路径>'
```

确认两点：`versionCode` / `versionName` 是新版本；签名证书 SHA-256 与上一版一致。只看 4 个包里的一个即可，其余 ABI 同一次构建。

## 5. 提交并打标签

```powershell
git add buildSrc/src/main/kotlin/Versions.kt
git commit -m "chore(release): 版本号升级到 2.1（versionCode 210）"
git tag release-2.1
git push origin main
git push origin release-2.1
```

标签沿用 `release-X.Y`（历史上的 `v*` 标签来自上游）。**不要用 `v*`**，会触发 `publish.yaml` 那个需要 secrets 的 CI 任务。

## 6. 创建 GitHub Release

用 `gh` 一步创建并上传四个包（`--repo` 写当前仓库名，仓库改名后旧名仍会跳转，但建议用新名）：

```powershell
gh release create release-2.1 --repo Torahlzy/JellyDroid --title "Jellydroid 2.1" --notes-file notes.md `
  "app/phone/build/outputs/apk/libre/release/phone-libre-arm64-v8a-release.apk" `
  "app/phone/build/outputs/apk/libre/release/phone-libre-armeabi-v7a-release.apk" `
  "app/phone/build/outputs/apk/libre/release/phone-libre-x86_64-release.apk" `
  "app/phone/build/outputs/apk/libre/release/phone-libre-x86-release.apk"
```

要点：

- **资产名保持构建产物原名**（`phone-libre-<abi>-release.apk`），与 2.0 起的下载链接形态一致，便于用户按 ABI 挑选。
- 说明里至少写清：本版主要变化、四个包各自适用设备、签名与上一版一致可直接覆盖安装、发布版不内置抓取站点地址。
- 也可以先在网页端建 draft 再手工上传，效果相同。

## 7. 发布后检查

- [ ] Release 已发布（不是 draft），4 个 APK 都在且大小合理（arm64 约 40 MB）。
- [ ] 说明中的版本号、标签、APK 文件名一致。
- [ ] 手机覆盖安装验证：从旧版升级后能正常启动，设置与登录态仍在。

## 环境与网络

- 本机 SSH 到 github.com 的 22 端口常被阻断；`push` 失败时改用 HTTPS 远端，配合 `gh auth token` 与本地代理（代理地址只写在 `local.properties`，不记录在仓库文档中）。
- `git remote` 可能仍指向改名前的仓库，push 时远端会提示 `moved`，推送本身仍然成功。
- 构建只依赖 `Gradle 分发包镜像`（已改为国内镜像）与 Maven 镜像配置，首次构建需联网下载依赖。
