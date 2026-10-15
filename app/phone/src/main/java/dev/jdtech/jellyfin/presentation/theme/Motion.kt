package dev.jdtech.jellyfin.presentation.theme

import androidx.compose.animation.core.CubicBezierEasing

/**
 * M3 动效 token 的项目内镜像。
 *
 * material3 的 `androidx.compose.material3.tokens.MotionTokens` 是 internal，应用无法直接引用，
 * 这里按官方规格（v0_103）镜像最常用的时长与缓动，避免在代码里散落魔法数字。
 * 取值与 material3 内部 token 一致，升级依赖时可对照更新。
 */
object Motion {
    /** 微交互 / 图标切换（150ms）。 */
    const val DurationShort3 = 150

    /** 小元素退出 / 消失（200ms）。 */
    const val DurationShort4 = 200

    /** 组件状态变化（300ms）。 */
    const val DurationMedium2 = 300

    /** 页面级转场（400ms）。 */
    const val DurationMedium4 = 400

    /** 共享元素转场 / 复杂布局变化（450ms）。 */
    const val DurationLong1 = 450

    /** 共享元素转场（500ms）。 */
    const val DurationLong2 = 500

    /** 停留状态变化 / 共享元素容器 morph 的默认缓动。 */
    val EasingEmphasized = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** 进入：快起缓停。 */
    val EasingEmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** 退出：慢起快走（退出总是比进入更快）。 */
    val EasingEmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
}
