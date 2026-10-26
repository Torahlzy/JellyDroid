package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.comment.MovieComment
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * 电影详情页底部的评论区（外网短评）。
 *
 * 只展示用户名与发言内容，不加载头像：站点本身也不给头像地址，且评论列表要尽量减少请求。
 * 外层已经是竖向滚动的 Column，因此这里用普通 Column 而不是 LazyColumn，避免同向嵌套滚动。
 */
@Composable
fun MovieCommentsSection(
    comments: List<MovieComment>,
    keyword: String,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(contentPadding),
    ) {
        Text(
            text = stringResource(CoreR.string.movie_comments_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))

        when {
            isLoading ->
                CircularProgressIndicator(
                    modifier = Modifier.size(MaterialTheme.spacings.default),
                    strokeWidth = MaterialTheme.spacings.extraSmall / 2,
                )
            error != null -> {
                Text(
                    text = stringResource(CoreR.string.movie_comments_error, error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onRetry) {
                    Text(text = stringResource(CoreR.string.movie_comments_retry))
                }
            }
            // 识别不到番号时不联网，直接告诉用户为什么这里是空的
            keyword.isBlank() ->
                Text(
                    text = stringResource(CoreR.string.movie_comments_no_keyword),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            comments.isEmpty() ->
                Text(
                    text = stringResource(CoreR.string.movie_comments_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            else -> {
                comments.forEachIndexed { index, comment ->
                    if (index > 0) {
                        HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.2f))
                    }
                    CommentItem(comment = comment)
                }
                // 网页端只对未登录 / 非 VIP 访客给最新几条，说明一下免得被当成「评论就这么少」
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
                Text(
                    text = stringResource(CoreR.string.movie_comments_limited),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CommentItem(comment: MovieComment, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacings.small)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = comment.userName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            comment.likes?.let { likes ->
                Text(
                    text = stringResource(CoreR.string.movie_comments_likes, likes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            comment.date?.let { date ->
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.extraSmall))
        Text(text = comment.content, style = MaterialTheme.typography.bodyMedium)
    }
}
