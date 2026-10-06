package dev.jdtech.jellyfin.logging

import timber.log.Timber

/**
 * 为 logcat 的 tag 追加 [AppLog.TAG_PREFIX] 前缀的 Timber 树。
 *
 * Timber 默认取调用方类名作为 tag，本类在其前面加上前缀，使 tag 形如 `torah/MovieViewModel`。
 * 这样即使历史代码中仍直接调用 Timber，输出的日志也带前缀，过滤时不会漏掉。
 *
 * 由 `BaseApplication` 在 debug 构建下 plant。
 */
class TorahDebugTree : Timber.DebugTree() {
    override fun createStackElementTag(element: StackTraceElement): String =
        "${AppLog.TAG_PREFIX}/${super.createStackElementTag(element)}"
}
