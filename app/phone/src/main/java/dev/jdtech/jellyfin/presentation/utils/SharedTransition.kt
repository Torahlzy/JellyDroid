@file:OptIn(ExperimentalSharedTransitionApi::class)

package dev.jdtech.jellyfin.presentation.utils

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import dev.jdtech.jellyfin.presentation.theme.Motion

// 共享元素转场需要两个作用域：NavHost 外层 SharedTransitionLayout 的作用域，与各目的地的
// AnimatedContentScope。用 CompositionLocal 下发，避免把作用域参数沿
// Screen → section → ItemCard 的调用链层层穿透。
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * 列表封面 ↔ 详情页头部之间的共享元素 morph。
 *
 * 两个作用域任一缺失（未接导航转场、预览等场景）时退化为普通布局，不影响原有渲染。
 */
@Composable
fun Modifier.heroSharedBounds(key: String): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current ?: return this
    return with(sharedTransitionScope) {
        this@heroSharedBounds.sharedBounds(
            rememberSharedContentState(key = key),
            animatedVisibilityScope = animatedVisibilityScope,
            // 内容进出场节奏：海报在前段保持完整可见地缩小，尾段才与详情头部交叉淡化
            enter =
                fadeIn(
                    tween(
                        durationMillis = Motion.DurationMedium2,
                        delayMillis = Motion.DurationLong1 - Motion.DurationMedium2,
                        easing = Motion.EasingEmphasizedDecelerate,
                    )
                ),
            exit =
                fadeOut(
                    tween(
                        durationMillis = Motion.DurationMedium2,
                        delayMillis = Motion.DurationLong1 - Motion.DurationMedium2,
                        easing = Motion.EasingEmphasizedAccelerate,
                    )
                ),
            // Fit 的 scaleToBounds：边界放大时海报整体缩小、完整显示在容器内（不裁切），
            // 落定后再过渡到详情头部的宽幅图。RemeasureToBounds + Crop 会把竖图当宽图裁切放大，
            // 中途只能看到海报局部（局部放大观感）
            resizeMode =
                SharedTransitionScope.ResizeMode.scaleToBounds(
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center,
                ),
            // 共享元素属于跨页面的容器变化，取 M3 的共享元素时长（450ms）与 emphasized 缓动
            boundsTransform = { _, _ ->
                tween(durationMillis = Motion.DurationLong1, easing = Motion.EasingEmphasized)
            },
            // 默认 ParentClip 会把飞出的海报裁在原卡片边界内，看起来像只有局部在动；
            // 用矩形 OverlayClip 取消这层裁切，让海报完整地飞
            clipInOverlayDuringTransition = OverlayClip(RectangleShape),
        )
    }
}
