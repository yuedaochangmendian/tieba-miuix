// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tieba.miuix.data.TiebaRepository
import com.tieba.miuix.protocol.ThreadInfo
import com.tieba.miuix.protocol.toPlainText
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

private const val HOME_MODE_RECOMMEND = 0
private const val HOME_MODE_FORUM = 1

/**
 * 首页。
 *
 * 两个模式：
 * - 「推荐」是默认页，走 `/c/f/forum/hotThreadList` 的热门推荐流，**匿名可用**，
 *   所以不登录也能直接看到内容；
 * - 「逛吧」是原来那个「输入吧名 + 帖子列表」的页面，复用 [ForumListPage]。
 *
 * 搜索不在这里 —— 底部导航有独立的「搜索」入口，避免首页既当信息流又当搜索框。
 */
@Composable
fun HomeFeedPage(
    repository: TiebaRepository,
    initialKeyword: String,
    onKeywordChange: (String) -> Unit,
    onOpenThread: (tid: Long, title: String, forumName: String) -> Unit,
    onOpenUser: (portrait: String) -> Unit = {},
    bottomPadding: Dp = 16.dp,
) {
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()

    var mode by rememberSaveable { mutableIntStateOf(HOME_MODE_RECOMMEND) }
    var threads by remember { mutableStateOf<List<ThreadInfo>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var hasLoaded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    suspend fun loadFeed() {
        if (isRefreshing) return
        isRefreshing = true
        errorMessage = null
        try {
            val response = repository.getHotThreads()
            val error = response.error
            if (error.isOk) {
                threads = response.threads
            } else {
                errorMessage = error.errmsg.ifBlank { "推荐流读取失败（errno = " + error.errno + "）" }
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: ("网络请求失败：" + e.javaClass.simpleName)
        } finally {
            isRefreshing = false
            hasLoaded = true
        }
    }

    // 只在「推荐」模式下真正进入页面时抓一次；切回来不重复请求。
    LaunchedEffect(mode) {
        if (mode == HOME_MODE_RECOMMEND && !hasLoaded) loadFeed()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "贴吧 Miuix",
                subtitle = if (mode == HOME_MODE_RECOMMEND) "为你推荐" else "",
                scrollBehavior = scrollBehavior,
                actions = {
                    if (mode == HOME_MODE_RECOMMEND) {
                        IconButton(onClick = { scope.launch { loadFeed() } }) {
                            Icon(imageVector = MiuixIcons.Refresh, contentDescription = "刷新推荐")
                        }
                    }
                },
                bottomContent = {
                    TabRow(
                        tabs = listOf("推荐", "逛吧"),
                        selectedTabIndex = mode,
                        onTabSelected = { mode = it },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                },
            )
        },
    ) { innerPadding ->
        if (mode == HOME_MODE_FORUM) {
            ForumListPage(
                repository = repository,
                initialKeyword = initialKeyword,
                onKeywordChange = onKeywordChange,
                onOpenThread = onOpenThread,
                onOpenUser = onOpenUser,
                bottomPadding = bottomPadding,
                embedded = true,
            )
            return@Scaffold
        }

        val contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = innerPadding.calculateTopPadding() + 8.dp,
            bottom = innerPadding.calculateBottomPadding() + bottomPadding,
        )
        PullToRefresh(
            isRefreshing = isRefreshing,
            onRefresh = { scope.launch { loadFeed() } },
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

                if (hasLoaded && threads.isEmpty() && message == null) {
                    item(key = "empty") {
                        MessageCard(text = "没有取到推荐内容，下拉可以重试。", isError = false)
                    }
                }

                items(items = threads, key = { it.id }) { thread ->
                    RecommendCard(
                        thread = thread,
                        onClick = { onOpenThread(thread.id, thread.title, thread.fname) },
                    )
                }
            }
        }
    }
}

/** 推荐流里的一条：吧名 + 标题 + 正文摘要 + 作者/回复数。 */
@Composable
private fun RecommendCard(
    thread: ThreadInfo,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val settings = currentSettings
    val preview = remember(thread.firstPostContent) {
        thread.firstPostContent.toPlainText().trim().take(120)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(14.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = onClick,
    ) {
        val forumName = thread.fname
        if (forumName.isNotBlank()) {
            Text(
                text = forumName + "吧",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // 点吧名去吧详情，而不是进帖子。
                modifier = Modifier.clickable {
                    context.startActivity(ForumDetailActivity.intent(context, forumName))
                },
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        Text(
            text = thread.title.ifBlank { "（无标题）" },
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
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val author = thread.authorName(settings.showBothNameAndNickname)
            if (author.isNotBlank()) {
                Text(
                    text = author,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Text(
                text = formatCount(thread.replyNum) + " 回复",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            if (thread.lastTimeInt > 0L) {
                Text(
                    text = formatTime(thread.lastTimeInt),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}
