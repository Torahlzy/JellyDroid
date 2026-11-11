package dev.jdtech.jellyfin.utils

import kotlinx.coroutines.CancellationException

/** 用户主动取消下载，与页面销毁造成的等待取消区分开。 */
internal class DownloadCancelledException : CancellationException("用户取消下载")
