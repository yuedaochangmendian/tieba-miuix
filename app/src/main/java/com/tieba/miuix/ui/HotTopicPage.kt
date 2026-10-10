// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tieba.miuix.data.TiebaRepository
import com.tieba.miuix.protocol.TopicListProto
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 热话题（热聊）列表。
 *
 * 走 `/c/f/recommend/topicList`（CMD=309289），**匿名可用**，
 * 每张卡片是话题配图 + 话题名 + 简介 + 讨论数，点进去是 [TopicDetailPage]。
 */
@Composable
fun HotTopicPage(
    repository: TiebaRepository,
    onOpenTopic: (id: Long, name: String) -> Unit,
    bottomPadding: Dp = 16.dp,
    embedded: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val pullToRefreshState = rememberPullToRefreshState()

    var topics by remember { mutableStateOf<List<TopicListProto.Topic>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var hasLoaded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        if (isRefreshing) return
        isRefreshing = true
        errorMessage = null
        try {
            val response = repository.getHotTopics()
            if (response.error.isOk) {
                topics = response.topics
            } else {
                errorMessage = response.error.errmsg.ifBlank {
                    "热话题读取失败（errno = " + response.error.errno + "）"
                }
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: ("网络请求失败：" + e.javaClass.simpleName)
        } finally {
            isRefreshing = false
            hasLoaded = true
        }
    }

    LaunchedEffect(Unit) {
        if (!hasLoaded) load()
    }

    // 嵌在首页里时，顶部栏与 tab 都由首页提供，这里只铺内容。
    val contentPadding = PaddingValues(
        start = 12.dp,
        end = 12.dp,
        top = if (embedded) 8.dp else 8.dp,
        bottom = bottomPadding,
    )

    PullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = { scope.launch { load() } },
        modifier = Modifier.fillMaxSize(),
        pullToRefreshState = pullToRefreshState,
        contentPadding = contentPadding,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val message = errorMessage
            if (message != null) {
                item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                    MessageCard(text = message, isError = true)
                }
            }

            if (hasLoaded && topics.isEmpty() && message == null) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    MessageCard(text = "暂时没有热话题，下拉可以重试。", isError = false)
                }
            }

            items(items = topics, key = { it.id }) { topic ->
                HotTopicCard(topic = topic, onClick = { onOpenTopic(topic.id, topic.name) })
            }
        }
    }
}

/** 一张热话题卡片：配图（没有配图就只显示文字）+ 话题名 + 简介 + 讨论数。 */
@Composable
private fun HotTopicCard(
    topic: TopicListProto.Topic,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(10.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (topic.hasImage) {
                SafeImage(
                    url = topic.image,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(10.dp)),
                    contentDescription = topic.name,
                    cornerRadius = 10.dp,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = "#" + topic.name,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (topic.desc.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = topic.desc,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (topic.discussNum > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = formatCount(topic.discussNum) + " 讨论",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }
    }
}
