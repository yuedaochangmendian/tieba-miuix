// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import com.tieba.miuix.protocol.ConcernedForum
import com.tieba.miuix.protocol.StoredThread
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
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
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

private const val FAV_TAB_THREADS = 0
private const val FAV_TAB_FORUMS = 1

/**
 * 「我的」页：收藏的主题帖 + 关注的吧。
 *
 * 两块数据都要登录（`/c/f/post/threadstore`、`/c/f/forum/getforumlist` 都是
 * `force_login` 通道），未登录时给一句明确提示而不是空列表 —— TiebaLite 的
 * 收藏页同样要求登录。
 */
@Composable
fun FavoritesPage(
    bottomPadding: Dp,
    onOpenThread: (tid: Long, title: String, forumName: String) -> Unit,
    onOpenForum: (name: String) -> Unit,
) {
    val repository = LocalRepository.current
    val accountStore = LocalAccountStore.current
    val requestLogin = LocalRequestLogin.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val pullToRefreshState = rememberPullToRefreshState()

    var selectedTab by remember { mutableIntStateOf(FAV_TAB_THREADS) }
    var threads by remember { mutableStateOf<List<StoredThread>>(emptyList()) }
    var forums by remember { mutableStateOf<List<ConcernedForum>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isSigning by remember { mutableStateOf(false) }
    var loadedOnce by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var pendingRemove by remember { mutableStateOf<StoredThread?>(null) }

    val listState = rememberLazyListState()

    suspend fun loadThreads(targetPage: Int, replace: Boolean) {
        if (replace) isLoading = true else isLoadingMore = true
        try {
            val result = repository.getStoredThreads(pn = targetPage)
            if (result.errorCode == 0) {
                threads = if (replace) result.items else threads + result.items
                page = targetPage
                hasMore = result.hasMore && result.items.isNotEmpty()
                errorMessage = null
            } else {
                errorMessage = result.errorMessage.ifBlank { "收藏列表加载失败" }
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: "网络请求失败"
        } finally {
            isLoading = false
            isLoadingMore = false
            isRefreshing = false
            loadedOnce = true
        }
    }

    suspend fun loadForums() {
        isLoading = true
        try {
            val result = repository.getConcernedForums()
            if (result.errorCode == 0) {
                forums = result.items
                errorMessage = null
            } else {
                errorMessage = result.errorMessage.ifBlank { "关注的吧加载失败" }
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: "网络请求失败"
        } finally {
            isLoading = false
            isRefreshing = false
            loadedOnce = true
        }
    }

    suspend fun reload() {
        listState.scrollToItem(0)
        if (selectedTab == FAV_TAB_THREADS) loadThreads(1, true) else loadForums()
    }

    LaunchedEffect(Unit) {
        if (accountStore.isLoggedIn) loadThreads(1, true) else loadedOnce = true
    }

    // 登录状态变了（比如刚在别的页面登录成功）就自动补拉一次。
    LaunchedEffect(accountStore.isLoggedIn, selectedTab) {
        if (!accountStore.isLoggedIn) {
            threads = emptyList()
            forums = emptyList()
            return@LaunchedEffect
        }
        if (selectedTab == FAV_TAB_THREADS) {
            if (threads.isEmpty()) loadThreads(1, true)
        } else if (forums.isEmpty()) {
            loadForums()
        }
    }

    LaunchedEffect(note) {
        if (note.isBlank()) return@LaunchedEffect
        kotlinx.coroutines.delay(2600)
        note = ""
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "我的",
                subtitle = if (accountStore.isLoggedIn) {
                    accountStore.displayName.ifEmpty { "已登录" }
                } else {
                    "未登录 · 只能匿名浏览"
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { scope.launch { reload() } }) {
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
            bottom = bottomPadding + 16.dp,
        )

        if (!accountStore.isLoggedIn) {
            Column(modifier = Modifier.fillMaxSize()) {
                MessageCard(
                    text = "登录后可以在这里看到收藏的帖子与关注的吧。",
                    isError = false,
                )
                TextButton(
                    text = "登录贴吧",
                    onClick = { requestLogin() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(
                tabs = listOf("收藏的帖子", "关注的吧"),
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )

            if (selectedTab == FAV_TAB_FORUMS && forums.isNotEmpty()) {
                Button(
                    onClick = {
                        if (isSigning) return@Button
                        isSigning = true
                        scope.launch {
                            val result = runCatching {
                                repository.signForums(forums.map { it.id })
                            }.getOrNull()
                            isSigning = false
                            note = result?.displayMessage ?: "一键签到失败"
                            if (result?.success == true) loadForums()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    enabled = !isSigning && accountStore.hasTbs,
                ) {
                    Text(
                        text = when {
                            isSigning -> "签到中…"
                            !accountStore.hasTbs -> "缺少发帖令牌，无法签到"
                            else -> "一键签到（" + forums.size + " 个吧）"
                        },
                        style = MiuixTheme.textStyles.button,
                    )
                }
            }

            PullToRefresh(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    scope.launch { reload() }
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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val message = errorMessage
                    if (message != null) {
                        item(key = "error") { MessageCard(text = message, isError = true) }
                    }
                    if (note.isNotBlank()) {
                        item(key = "note") { MessageCard(text = note, isError = false) }
                    }

                    if (selectedTab == FAV_TAB_THREADS) {
                        if (loadedOnce && threads.isEmpty() && message == null && !isLoading) {
                            item(key = "empty") {
                                MessageCard(
                                    text = "还没有收藏的帖子。在帖子里长按或点收藏就能存下来。",
                                    isError = false,
                                )
                            }
                        }
                        items(
                            items = threads,
                            key = { item -> item.threadId },
                        ) { item ->
                            StoredThreadCard(
                                item = item,
                                onClick = {
                                    onOpenThread(item.threadId, item.title, item.forumName)
                                },
                                onLongClick = { pendingRemove = item },
                            )
                        }
                        item(key = "more") {
                            LoadMoreRow(
                                hasMore = hasMore,
                                isLoading = isLoadingMore,
                                visible = threads.isNotEmpty(),
                                onLoadMore = { scope.launch { loadThreads(page + 1, false) } },
                            )
                        }
                    } else {
                        if (loadedOnce && forums.isEmpty() && message == null && !isLoading) {
                            item(key = "empty") {
                                MessageCard(
                                    text = "还没有关注的吧。到吧详情页点关注就能加进来。",
                                    isError = false,
                                )
                            }
                        }
                        items(
                            items = forums,
                            key = { item -> item.id.coerceAtLeast(item.name.hashCode().toLong()) },
                        ) { item ->
                            ConcernedForumCard(item = item, onClick = { onOpenForum(item.name) })
                        }
                    }
                }
            }
        }
    }

    val removing = pendingRemove
    if (removing != null) {
        OverlayDialog(
            show = true,
            title = "取消收藏",
            summary = removing.displayTitle,
            onDismissRequest = { pendingRemove = null },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TextButton(
                    text = "再想想",
                    onClick = { pendingRemove = null },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(),
                )
                TextButton(
                    text = "取消收藏",
                    onClick = {
                        pendingRemove = null
                        scope.launch {
                            val result = runCatching {
                                repository.setThreadStored(
                                    tid = removing.threadId,
                                    pid = removing.threadId,
                                    store = false,
                                )
                            }.getOrNull()
                            if (result?.success == true) {
                                threads = threads.filterNot { it.threadId == removing.threadId }
                                note = "已取消收藏"
                            } else {
                                note = result?.displayMessage ?: "取消收藏失败"
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(color = MiuixTheme.colorScheme.error),
                )
            }
        }
    }
}

/** 收藏列表里的一条帖子。长按可以取消收藏。 */
@Composable
private fun StoredThreadCard(
    item: StoredThread,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(14.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = onClick,
        onLongPress = onLongClick,
    ) {
        Text(
            text = item.displayTitle,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceContainer,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.forumName + "吧",
                modifier = Modifier.weight(1f, fill = false),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    if (item.authorName.isNotBlank()) append(item.authorName)
                    if (item.isDeleted) {
                        if (isNotEmpty()) append(" · ")
                        append("已被删除")
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatCount(item.replyCount) + " 回复",
                modifier = Modifier.padding(start = 8.dp),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Text(
            text = formatTime(item.lastTime),
            modifier = Modifier.padding(top = 4.dp),
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 关注的吧里的一条。 */
@Composable
private fun ConcernedForumCard(item: ConcernedForum, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(14.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.name + "吧",
                modifier = Modifier.weight(1f),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.level > 0) {
                Text(
                    text = "Lv." + item.level,
                    modifier = Modifier
                        .clip(CircleShape)
                        .padding(horizontal = 4.dp),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
            if (item.isSigned) {
                Text(
                    text = "已签到",
                    modifier = Modifier.padding(start = 8.dp),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}
