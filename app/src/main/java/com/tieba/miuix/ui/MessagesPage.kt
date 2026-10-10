// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.tieba.miuix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tieba.miuix.data.TiebaRepository
import com.tieba.miuix.protocol.MessageItem
import com.tieba.miuix.protocol.MessageType
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
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

private const val MSG_TAB_REPLY = 0
private const val MSG_TAB_AT = 1

/**
 * 消息中心：回复我的 / @我的。
 *
 * 这两个接口都必须登录；未登录时只显示一句提示 + 登录按钮，不发起请求。
 * 数据来源是 App 的表单通道（`/c/u/feed/replyme` 与 `/c/u/feed/atme`），
 * 网页端的 `/mo/q/message/` 系列路径在客户端请求头下只会返回一张错误页。
 */
@Composable
fun MessagesPage(
    bottomPadding: Dp,
    onOpenThread: (tid: Long, title: String, forumName: String, postId: Long) -> Unit,
    onOpenUser: (portrait: String, uid: Long) -> Unit = { _, _ -> },
) {
    val repository = LocalRepository.current
    val accountStore = LocalAccountStore.current
    val requestLogin = LocalRequestLogin.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val pullState = rememberPullToRefreshState()

    var selectedTab by rememberSaveable { mutableIntStateOf(MSG_TAB_REPLY) }
    var items by remember { mutableStateOf<List<MessageItem>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loadedOnce by remember { mutableStateOf(false) }

    val type = if (selectedTab == MSG_TAB_REPLY) MessageType.REPLY_ME else MessageType.AT_ME

    suspend fun load(targetPage: Int, replace: Boolean) {
        if (isLoading) return
        isLoading = true
        if (replace) errorMessage = null
        try {
            val result = repository.getMessages(type, targetPage)
            if (result.isOk) {
                items = if (replace) result.items else items + result.items
                page = result.currentPage
                hasMore = result.hasMore
                errorMessage = null
            } else {
                val hint = result.errorMessage.ifBlank { "消息加载失败" }
                errorMessage = if (hint.contains("登录")) hint else hint
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: "网络请求失败"
        } finally {
            isLoading = false
            isRefreshing = false
            isLoadingMore = false
            loadedOnce = true
        }
    }

    suspend fun reload() {
        listState.scrollToItem(0)
        load(1, true)
    }

    LaunchedEffect(accountStore.isLoggedIn, selectedTab) {
        if (!accountStore.isLoggedIn) {
            items = emptyList()
            loadedOnce = true
            return@LaunchedEffect
        }
        items = emptyList()
        hasMore = false
        page = 1
        loadedOnce = false
        load(1, true)
    }

    val unreadCount = items.count { it.unread }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "消息",
                subtitle = when {
                    !accountStore.isLoggedIn -> "未登录 · 登录后才能查看回复与 @"
                    unreadCount > 0 -> unreadCount.toString() + " 条未读"
                    else -> accountStore.displayName.ifEmpty { "已登录" }
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    if (accountStore.isLoggedIn) {
                        IconButton(onClick = { scope.launch { reload() } }) {
                            Icon(imageVector = MiuixIcons.Refresh, contentDescription = "刷新")
                        }
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
                    text = "登录后可以在这里看到「回复我的」与「@我的」。",
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
                tabs = listOf("回复我的", "@我的"),
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )

            PullToRefresh(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    scope.launch { load(1, true) }
                },
                modifier = Modifier.fillMaxSize(),
                pullToRefreshState = pullState,
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    errorMessage?.let { message ->
                        item(key = "error") { MessageCard(text = message, isError = true) }
                    }

                    items(items = items, key = { item -> itemKey(item) }) { item ->
                        MessageCardRow(
                            item = item,
                            onClick = {
                                if (item.threadId > 0L) {
                                    onOpenThread(
                                        item.threadId,
                                        item.title,
                                        item.forumName,
                                        item.postId,
                                    )
                                }
                            },
                            onOpenUser = {
                                if (item.replyerPortraitUrl.isNotEmpty() || item.replyerId > 0L) {
                                    onOpenUser(item.replyerPortrait, item.replyerId)
                                }
                            },
                        )
                    }

                    if (loadedOnce && items.isEmpty() && errorMessage == null) {
                        item(key = "empty") {
                            CenterHint(
                                text = if (selectedTab == MSG_TAB_REPLY) {
                                    "还没有人回复你"
                                } else {
                                    "还没有人 @ 你"
                                },
                            )
                        }
                    }

                    item(key = "load_more") {
                        LoadMoreRow(
                            hasMore = hasMore,
                            isLoading = isLoadingMore,
                            visible = items.isNotEmpty(),
                            onLoadMore = {
                                if (!hasMore || isLoadingMore) return@LoadMoreRow
                                isLoadingMore = true
                                scope.launch { load(page + 1, false) }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 同一条消息的列表 key 要稳定：优先 pid，其次 tid+时间。 */
private fun itemKey(item: MessageItem): String = when {
    item.postId != 0L -> "p" + item.postId
    item.quotePid != 0L -> "q" + item.quotePid
    else -> "t" + item.threadId + "_" + item.time
}

@Composable
private fun MessageCardRow(
    item: MessageItem,
    onClick: () -> Unit,
    onOpenUser: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(14.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = onClick,
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            SafeImage(
                url = item.replyerPortraitUrl,
                modifier = Modifier.size(36.dp),
                contentDescription = item.displayName,
                cornerRadius = 18.dp,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.displayName,
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (item.unread) {
                        Spacer(modifier = Modifier.width(6.dp))
                        UnreadDot()
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    val timeText = formatRelativeSeconds(item.time)
                    if (timeText.isNotEmpty()) {
                        Text(
                            text = timeText,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }

                val meta = buildMeta(item)
                if (meta.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = meta,
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (item.content.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.content,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (item.quoteContent.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    QuotedBlock(item.quoteUserName, item.quoteContent)
                }
            }
        }
    }
}

/** 副标题：帖子标题 / 吧名 / 楼层类型。 */
private fun buildMeta(item: MessageItem): String {
    val parts = mutableListOf<String>()
    if (item.title.isNotBlank()) parts += item.title
    if (item.forumName.isNotBlank()) parts += item.forumName + "吧"
    parts += if (item.isFloorReply) "回复了你的主题" else "回复了你的楼层"
    return parts.joinToString(" · ")
}

@Composable
private fun QuotedBlock(name: String, content: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 8.dp,
        insideMargin = PaddingValues(10.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column {
            if (name.isNotBlank()) {
                Text(
                    text = name,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            Text(
                text = content,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 未读小红点。 */
@Composable
private fun UnreadDot() {
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(color = MiuixTheme.colorScheme.error, shape = CircleShape),
    )
}
