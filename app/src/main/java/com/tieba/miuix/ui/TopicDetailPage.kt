// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tieba.miuix.data.TiebaRepository
import com.tieba.miuix.protocol.ThreadSearchItem
import com.tieba.miuix.protocol.TopicDetail
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 热话题详情页。
 *
 * 顶部是话题大图与简介，下面列出这个话题下最热的若干条帖子——
 * 走 `/mo/q/newtopic/topicDetail`，匿名可读，所以未登录也能正常浏览。
 */
@Composable
fun TopicDetailPage(
    repository: TiebaRepository,
    topicId: Long,
    topicName: String,
    onBack: () -> Unit,
    onOpenThread: (tid: Long, title: String, forumName: String) -> Unit,
    bottomPadding: Dp = 16.dp,
) {
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()

    var detail by remember { mutableStateOf<TopicDetail?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var loadedOnce by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        if (isRefreshing) return
        isRefreshing = true
        errorMessage = null
        try {
            detail = repository.getTopicDetail(topicId, topicName)
        } catch (e: Exception) {
            errorMessage = e.message ?: ("网络请求失败：" + e.javaClass.simpleName)
        } finally {
            isRefreshing = false
            loadedOnce = true
        }
    }

    LaunchedEffect(topicId) {
        if (!loadedOnce) load()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = detail?.name?.ifBlank { topicName } ?: topicName.ifBlank { "热话题" },
                subtitle = detail?.let { "讨论 " + formatCount(it.discussNum) }.orEmpty(),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { load() } }) {
                        Icon(imageVector = MiuixIcons.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { innerPadding ->
        val contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = innerPadding.calculateTopPadding() + 8.dp,
            bottom = innerPadding.calculateBottomPadding() + bottomPadding,
        )
        PullToRefresh(
            isRefreshing = isRefreshing,
            onRefresh = { scope.launch { load() } },
            modifier = Modifier.fillMaxSize(),
            pullToRefreshState = pullToRefreshState,
            contentPadding = contentPadding,
            topAppBarScrollBehavior = scrollBehavior,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val message = errorMessage
                if (message != null) {
                    item(key = "error") { MessageCard(text = message, isError = true) }
                }

                val current = detail
                if (current != null) {
                    item(key = "header") { TopicHeader(current) }
                    item(key = "header-title") { SectionTitle("热议帖子") }
                    if (current.threads.isEmpty()) {
                        item(key = "empty") {
                            MessageCard(text = "这个话题还没有汇总的帖子。", isError = false)
                        }
                    }
                    items(items = current.threads, key = { it.tid.toString() + "-" + it.pid }) { item ->
                        TopicThreadCard(item) {
                            onOpenThread(item.tid, item.title, item.forumName)
                        }
                    }
                } else if (loadedOnce && message == null) {
                    item(key = "loading") { MessageCard(text = "正在加载话题…", isError = false) }
                }
            }
        }
    }
}

/** 话题头图 + 标题 + 简介。 */
@Composable
private fun TopicHeader(detail: TopicDetail) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(14.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        val image = detail.bigImage.ifEmpty { detail.image }
        if (image.isNotEmpty()) {
            SafeImage(
                url = image,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp)),
                contentDescription = detail.name,
                cornerRadius = 12.dp,
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
        Text(
            text = detail.name,
            style = MiuixTheme.textStyles.title4,
            color = MiuixTheme.colorScheme.onSurface,
        )
        if (detail.desc.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = detail.desc,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "讨论 " + formatCount(detail.discussNum),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.primary,
            )
            if (detail.interactionNum > 0) {
                Text(
                    text = "互动 " + formatCount(detail.interactionNum),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

/** 话题下的一条帖子。 */
@Composable
private fun TopicThreadCard(item: ThreadSearchItem, onClick: () -> Unit) {
    val settings = currentSettings
    val preview = remember(item.content) { item.content.trim().take(100) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(14.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = onClick,
    ) {
        if (item.forumName.isNotBlank()) {
            Text(
                text = item.forumName + "吧",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            text = item.title.ifBlank { "（无标题）" },
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (preview.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = preview,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val author = item.authorName.ifBlank { "匿名用户" }
            Text(
                text = author,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = formatCount(item.replyNum) + " 回复",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
