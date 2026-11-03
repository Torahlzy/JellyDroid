package dev.jdtech.jellyfin.presentation.utils

import androidx.compose.ui.util.lerp

// 头图允许占用的最大屏幕高度比例：横屏 / 平板上宽幅头图会铺满整屏，吸顶后上滑时缩小到该比例为止
private const val HEADER_MAX_HEIGHT_FRACTION_OF_SCREEN = 2f / 5f

/**
 * 头图是否高过限高、需要在上滑时缩小（横屏 / 平板会命中，竖屏手机基本不会）。
 *
 * 需要缩小的头图必须同时吸顶：缩放之外还有页面滚动，不吸顶就会「边缩边被页面带走」，
 * 缩到一半整块滚出屏幕。
 */
internal fun headerNeedsShrink(headerHeightPx: Int, screenHeightPx: Int): Boolean =
    headerHeightPx > 0 &&
        screenHeightPx > 0 &&
        headerHeightPx > screenHeightPx * HEADER_MAX_HEIGHT_FRACTION_OF_SCREEN

/**
 * 头图随上滑的等比缩放比例。
 *
 * 只在头图吸顶（[isHeaderPinned]）时才缩小：不吸顶时头图会跟着页面一起滚走，
 * 再叠加缩放就成了「边缩边往上飞」，滚过半屏整块消失。
 *
 * 吸顶后，头图高度不超过 [HEADER_MAX_HEIGHT_FRACTION_OF_SCREEN] 屏时恒为 1（竖屏手机基本落在这里，行为不变）；
 * 超过时（横屏 / 平板上宽幅头图会占满整屏），从 1 线性缩小到
 * 「限高 / 头图高」，正好在滚过头图与限高的差值距离时缩到限高并停住。
 */
internal fun headerScaleForScroll(
    scrollPx: Int,
    headerHeightPx: Int,
    screenHeightPx: Int,
    isHeaderPinned: Boolean,
): Float {
    if (!isHeaderPinned || !headerNeedsShrink(headerHeightPx, screenHeightPx)) return 1f
    val maxHeaderHeight = screenHeightPx * HEADER_MAX_HEIGHT_FRACTION_OF_SCREEN
    val shrinkScrollDistance = headerHeightPx - maxHeaderHeight
    val progress = (scrollPx / shrinkScrollDistance).coerceIn(0f, 1f)
    return lerp(1f, maxHeaderHeight / headerHeightPx, progress)
}
