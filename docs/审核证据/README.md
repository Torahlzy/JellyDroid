# 审核证据使用说明

对应基线：`0b83cc5708a0ee9b5da7b38dc6f71997b225e328`。问题解释、触发条件、修复方向及未验证边界见上一层审核报告。

- `audit_manifest.json`：765 个已审文件的状态、SHA-256、关联问题和范围外文件/理由；只对源文件计算摘要，不对生成的审核交付物循环计算。
- `findings.json`：30 项问题的结构化索引，保留首轮 JD-001～004 编号；正文按优先级排列。
- `validation_results.json`：本轮三个验证脚本的实际输出，原始缺陷仍存在时脚本通过表示“成功复现/验证该契约”，不表示问题已修复。
- `verify_first_batch.py`：原始 DAO SQL 同步竞争、等价 JVM 同名文件覆盖、8→9 原始迁移 SQL。
- `verify_continuation.py`：当前纯 Kotlin 字幕解析源码、带平台替身的延迟字幕类、提取的 URL/同站判断函数；StateFlow 赋值与缓存窗口的等价探针。
- `verify_resources_and_schema.py`：资源结构/占位符/简繁中文差集、导出 schema 2～9 及手工迁移 6→7/8→9。

## 当前修复回归

在仓库根目录执行：

```sh
python3 docs/审核证据/verify_fixes.py
python3 docs/审核证据/verify_review_fixes.py
python3 docs/审核证据/verify_kotlin_syntax.py
python3 - <<'PY'
import sys
sys.path.insert(0, 'docs/审核证据')
from verify_resources_and_schema import verify_resources
print(verify_resources(False))
PY
```

- `verify_fixes.py`：当前 DAO SQL 的同步快照确认、任务归属检查；当前字幕解析、缓存与 Controller、延迟字幕类（平台替身），以及提取的文件命名、重定向和正文限额函数。通过表示这些隔离场景符合修复预期。
- `verify_review_fixes.py`：编译当前 `SubtitleCompareManager`、取消原因类型及提取的协调器/下载入口，使用可控挂起点验证冷启动等待初始化、同条目会话换代重读、切条目/换用户/关闭时丢弃旧加载，以及页面退出、主动取消、重进页面取消/删除和同来源请求复用。Repository、缓存、下载准备与 Android 接口使用替身，不代表真实网络或 WorkManager 验收。
- `verify_kotlin_syntax.py`：通过 Kotlin compiler PSI 检查本次修改/新增的 `.kt` 文件语法；不解析类型、不编译 Android 变体。
- `fix_validation_results.json`：修复后实际验证输出和应用编译阻塞原因。

原始三个 `verify_first_batch.py` / `verify_continuation.py` / `verify_resources_and_schema.py` 的默认入口用于复现审核基线缺陷，其中部分函数读取当前源码，**不能把它们对修复后源码的失败判为回归**。要复现原始输出，应在隔离的原基线 checkout 使用，不回退当前工作区。当前资源核对显式传入 `False`，不再预期缺失六个中文 key。

已尝试 `./gradlew :app:phone:compileLibreDebugKotlin --offline`，失败于缺少 Build Tools 37.0.0，尚未进入应用源码编译；没有修改构建版本或安装 SDK，没有执行 assemble/build/check/ktfmtFormat。离线探针和 PSI 检查不等于 Android 类型检查、KSP/Hilt 接线及真机验收通过。

需要 Python 3 和 Java。续审 Kotlin 探针只使用本机 Gradle 缓存中的 Kotlin compiler/stdlib 2.4.10、coroutines 1.8.0 与编译器运行依赖，不下载依赖、不调用 Gradle、不构建 Android。当前项目 Kotlin 是 2.4.20，该探针不作为项目变体编译结果。缺少缓存会明确失败，应先解决探针环境，不能解释为业务源码编译失败。

所有数据库在内存创建，临时目录自动清理；不读取真实数据库、下载媒体、本地账号或签名信息，不联网。Handler/视图替身只验证调度逻辑；等价文件/时序探针不能替代 Android 真机并发、升级、性能和解码验证。
