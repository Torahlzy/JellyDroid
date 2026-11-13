package dev.jdtech.jellyfin.privacy

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import javax.inject.Inject

/** 统一将最近任务中的页面预览替换为黑色占位卡，保留前台截图能力。 */
class RecentsPrivacyLifecycleCallbacks @Inject constructor() :
    Application.ActivityLifecycleCallbacks {

    override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
        // 旧系统没有仅禁用最近任务截图的接口，不使用会限制前台截图和录屏的 FLAG_SECURE。
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        // 在主题应用完成后设置占位卡底色，避免浅色主题或播放器主题覆盖黑色背景。
        activity.setTaskDescription(
            ActivityManager.TaskDescription.Builder()
                .setLabel(activity.title?.toString())
                .setPrimaryColor(Color.BLACK)
                .setBackgroundColor(Color.BLACK)
                .build()
        )
        // 创建时就禁用快照，避免退后台后再加遮罩因窗口停止绘制而漏出上一帧。
        activity.setRecentsScreenshotEnabled(false)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
