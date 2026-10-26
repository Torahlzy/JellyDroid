package dev.jdtech.jellyfin.presentation.utils

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout

/**
 * 头图视差：图片随滚动反向缓动，产生景深感。
 *
 * [rate] 为视差速率，位移 = 滚动量 / rate（图片比页面慢半拍）；rate <= 0 时禁用视差，
 * 用于头图吸顶的场景——框架已固定不动，再带视差会让图片在框内漂移、露出缺口。
 */
fun Modifier.parallaxLayoutModifier(scrollState: ScrollState, rate: Int) =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val height = if (rate > 0) scrollState.value / rate else 0
        layout(placeable.width, placeable.height) { placeable.place(0, height) }
    }

/** 同 [parallaxLayoutModifier] 的 LazyList 版本，位移取列表首个可见项的项内偏移。 */
fun Modifier.parallaxLayoutModifier(lazyListState: LazyListState, rate: Int) =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val height =
            if (rate > 0) lazyListState.firstVisibleItemScrollOffset / rate else 0
        layout(placeable.width, placeable.height) { placeable.place(0, height) }
    }
