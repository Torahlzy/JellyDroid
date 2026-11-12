package dev.jdtech.jellyfin.utils

import androidx.annotation.StringRes
import dev.jdtech.jellyfin.core.R as CoreR

/** 任务持久化只保存名称，避免应用升级后资源 id 变化导致错误提示错位。 */
enum class DownloadExportFailure(@param:StringRes val messageRes: Int) {
    FILE_MISSING(CoreR.string.download_export_file_missing),
    STORAGE_UNAVAILABLE(CoreR.string.storage_unavailable),
    NO_SPACE(CoreR.string.download_export_no_space),
    PERMISSION_REQUIRED(CoreR.string.download_export_permission_required),
    OTHER(CoreR.string.download_export_failed),
}
