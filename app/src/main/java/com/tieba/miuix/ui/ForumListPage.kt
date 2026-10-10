// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.tieba.miuix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tieba.miuix.data.TiebaRepository
import com.tieba.miuix.protocol.Page
import com.tieba.miuix.protocol.SimpleForum
import com.tieba.miuix.protocol.ThreadInfo
import com.tieba.miuix.protocol.TiebaConst.ThreadSortType
import com.tieba.miuix.protocol.toPlainText
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** 主题帖排序选项（展示名 -> 接口取值）。 */
private val SORT_OPTIONS: List<Pair<String, Int>> = listOf(
    "回复时间" to ThreadSortType.REPLY,
    "最新发布" to ThreadSortType.CREATE,
    "热门" to ThreadSortType.HOT,
)

/** 吧内主题帖列表页。 */
@Composable
fun ForumListPage(
    repository: TiebaRepository,
    initialKeyword: String,
    onKeywordChange: (String) -> Unit,
    onOpenThread: (tid: Long, title: String, forumName: String) -> Unit,
    bottomPadding: Dp = 16.dp,
) {
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()

    var keyword by rememberSaveable { mutableStateOf(initialKeyword) }
    var forumName by rememberSaveable { mutableStateOf(initialKeyword) }
    var sortType by rememberSaveable { mutableIntStateOf(ThreadSortType.REPLY) }

    var threads by remember { mutableStateOf<List<ThreadInfo>>(emptyList()) }
    var forum by remember { mutableStateOf<SimpleForum?>(null) }
    var page by remember { mutableStateOf<Page?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasLoaded by remember { mutableStateOf(false) }

    suspend fun load(targetPage: Int, replace: Boolean) {
        if (forumName.isBlank()) return
        if (replace) isRefreshing = true else isLoadingMore = true
        errorMessage = null
        try {
            val response = repository.getThreads(
                kw = forumName,
                pn = targetPage,
                sortType = sortType,
            )
            val error = response.error
            if (error.isOk) {
                threads = if (replace) response.threads else threads + response.threads
                forum = response.forum ?: forum
                page = response.page
            } else {
                errorMessage = error.errmsg.ifBlank { "接口返回错误（errno = " + error.errno + "）" }
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: ("网络请求失败：" + e.javaClass.simpleName)
        } finally {
            isRefreshing = false
            isLoadingMore = false
            hasLoaded = true
        }
    }

    LaunchedEffect(forumName, sortType) {
        load(targetPage = 1, replace = true)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = if (forumName.isBlank()) "贴吧 Miuix" else forumName + "吧",
                subtitle = forum?.let { "会员 " + it.memberNum + " · 帖子 " + it.postNum }.orEmpty(),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { scope.launch { load(targetPage = 1, replace = true) } }) {
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
            onRefresh = {
                isRefreshing = true
                scope.launch { load(targetPage = 1, replace = true) }
            },
            modifier = Modifier.fillMaxSize(),
            pullToRefreshState = pullToRefreshState,
            contentPadding = contentPadding,
            topAppBarScrollBehavior = scrollBehavior,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
            ) {
                item(key = "search") {
                    SearchCard(
                        keyword = keyword,
                        onKeywordChange = {
                            keyword = it
                            onKeywordChange(it)
                        },
                        sortType = sortType,
                        onSortTypeChange = { sortType = it },
                        onSubmit = {
                            val trimmed = keyword.trim()
                            keyword = trimmed
                            onKeywordChange(trimmed)
                            if (trimmed != forumName) forumName = trimmed
                        },
                    )
                }

                val message = errorMessage
                if (message != null) {
                    item(key = "error") {
                        MessageCard(text = message, isError = true)
                    }
                }

                if (hasLoaded && threads.isEmpty() && message == null) {
                    item(key = "empty") {
                        MessageCard(
                            text = if (forumName.isBlank()) {
                                "输入一个贴吧的名字（比如「李毅」），然后点「进入贴吧」。"
                            } else {
                                "没有取到帖子，下拉可以重试。"
                            },
                            isError = false,
                        )
                    }
                }

                items(items = threads, key = { it.id }) { thread ->
                    Box(modifier = Modifier.padding(bottom = 10.dp)) {
                        ThreadCard(
                            thread = thread,
                            onClick = { onOpenThread(thread.id, thread.title, forumName) },
                        )
                    }
                }

                item(key = "more") {
                    LoadMoreRow(
                        hasMore = page?.hasMore == true,
                        isLoading = isLoadingMore,
                        visible = threads.isNotEmpty(),
                        onLoadMore = {
                            scope.launch {
                                load(targetPage = (page?.currentPage ?: 1) + 1, replace = false)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchCard(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    sortType: Int,
    onSortTypeChange: (Int) -> Unit,
    onSubmit: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        TextField(
            value = keyword,
            onValueChange = onKeywordChange,
            modifier = Modifier.fillMaxWidth(),
            label = "输入贴吧名称",
            useLabelAsPlaceholder = true,
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = MiuixIcons.Search,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            },
            trailingIcon = {
                if (keyword.isNotEmpty()) {
                    IconButton(onClick = { onKeywordChange("") }) {
                        Icon(imageVector = MiuixIcons.Clear, contentDescription = "清空")
                    }
                }
            },
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SORT_OPTIONS.forEach { option ->
                val selected = sortType == option.second
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 3.dp),
                    cornerRadius = 10.dp,
                    insideMargin = PaddingValues(vertical = 8.dp),
                    colors = CardDefaults.defaultColors(
                        color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurfaceContainer,
                    ),
                    pressFeedbackType = PressFeedbackType.Sink,
                    showIndication = true,
                    onClick = { onSortTypeChange(option.second) },
                ) {
                    Text(
                        text = option.first,
                        modifier = Modifier.fillMaxWidth(),
                        style = MiuixTheme.textStyles.footnote1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth()) {
            Text(text = "进入贴吧", style = MiuixTheme.textStyles.button)
        }
    }
}

@Composable
private fun ThreadCard(
    thread: ThreadInfo,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(16.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = onClick,
    ) {
        Text(
            text = thread.title.ifBlank { "（无标题）" },
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val preview = thread.firstPostContent.toPlainText()
        if (preview.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = preview,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (thread.isGood) {
                Text(
                    text = "精",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .background(
                            color = MiuixTheme.colorScheme.primary,
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = thread.authorName.ifBlank { "匿名用户" },
                modifier = Modifier.weight(1f, fill = false),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = thread.replyNum.toString() + " 回复",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
