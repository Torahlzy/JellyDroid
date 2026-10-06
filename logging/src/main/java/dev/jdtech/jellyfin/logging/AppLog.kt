package dev.jdtech.jellyfin.logging

import timber.log.Timber

/**
 * 应用统一的日志入口。
 *
 * 所有需要输出到 logcat 的日志都必须经过本类，禁止直接使用 [Timber] 或 [android.util.Log]。
 * 本类内部转交 Timber，tag 由 [TorahDebugTree] 统一加上 `torah` 前缀
 * （形如 `torah/MovieViewModel`），因此在 logcat 中搜索 `torah` 即可过滤出本应用的全部日志。
 *
 * 用法：
 * ```kotlin
 * AppLog.d("Stream url: $streamUrl") // 也支持占位符：AppLog.d("Stream url: %s", url)
 * AppLog.e(exception, "加载电影失败")
 * AppLog.e(exception)
 * AppLog.tag("MPV").d("mpv 事件：%s", event) // 临时指定业务 tag
 * ```
 */
object AppLog {
    /** 所有日志 tag 的统一前缀，即 logcat 过滤关键字 */
    const val TAG_PREFIX = "torah"

    /**
     * 需要临时指定 tag 时使用，例如把 mpv 相关日志归到同一个 tag 下。
     *
     * @param tag 业务 tag，不含前缀，实际输出为 `torah/<tag>`
     * @return 可直接链式调用日志方法的 Timber.Tree
     */
    fun tag(tag: String): Timber.Tree = Timber.tag("$TAG_PREFIX/$tag")

    // —— verbose ——

    fun v(message: String, vararg args: Any?) = Timber.v(message, *args)

    fun v(t: Throwable, message: String, vararg args: Any?) = Timber.v(t, message, *args)

    fun v(t: Throwable) = Timber.v(t)

    // —— debug ——

    fun d(message: String, vararg args: Any?) = Timber.d(message, *args)

    fun d(t: Throwable, message: String, vararg args: Any?) = Timber.d(t, message, *args)

    fun d(t: Throwable) = Timber.d(t)

    // —— info ——

    fun i(message: String, vararg args: Any?) = Timber.i(message, *args)

    fun i(t: Throwable, message: String, vararg args: Any?) = Timber.i(t, message, *args)

    fun i(t: Throwable) = Timber.i(t)

    // —— warn ——

    fun w(message: String, vararg args: Any?) = Timber.w(message, *args)

    fun w(t: Throwable, message: String, vararg args: Any?) = Timber.w(t, message, *args)

    fun w(t: Throwable) = Timber.w(t)

    // —— error ——

    fun e(message: String, vararg args: Any?) = Timber.e(message, *args)

    fun e(t: Throwable, message: String, vararg args: Any?) = Timber.e(t, message, *args)

    fun e(t: Throwable) = Timber.e(t)

    // —— assert（不应发生的情况）——

    fun wtf(message: String, vararg args: Any?) = Timber.wtf(message, *args)

    fun wtf(t: Throwable, message: String, vararg args: Any?) = Timber.wtf(t, message, *args)

    fun wtf(t: Throwable) = Timber.wtf(t)
}
