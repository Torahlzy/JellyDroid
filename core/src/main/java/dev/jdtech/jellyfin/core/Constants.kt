package dev.jdtech.jellyfin.core

object Constants {
    // player
    const val GESTURE_EXCLUSION_AREA_VERTICAL = 48
    const val GESTURE_EXCLUSION_AREA_HORIZONTAL = 24
    const val FULL_SWIPE_RANGE_SCREEN_RATIO = 0.66f
    // 手势方向判定所需的最小累计位移（dp），小于该值视为方向未明，不激活横滑 / 竖滑任何一方
    const val GESTURE_DIRECTION_LOCK_DISTANCE = 24
    const val ZOOM_SCALE_BASE = 1f
    const val ZOOM_SCALE_THRESHOLD = 0.01f

    // favorites
    const val FAVORITE_TYPE_MOVIES = 0
    const val FAVORITE_TYPE_SHOWS = 1
    const val FAVORITE_TYPE_EPISODES = 2
}
