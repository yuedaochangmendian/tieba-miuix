// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import com.tieba.miuix.protocol.UserFollowList
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
 * 关注列表页。
 *
 * 走 `POST /c/u/follow/followList`：`uid` 为 0 表示查自己的关注（需要登录），
 * 传别人的 uid 时匿名也能看公开的关注列表。
 */
@Composable
fun FollowListPage(
    repository: TiebaRepository,
    uid: Long,
    title: String = "",
    onBack: () -> Unit,
    onOpenUser: (uid: Long, portrait: String, name: String) -> Unit,
    bottomPadding: Dp = 16.dp,
) {
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()

    var items by remember { mutableStateOf<List<UserFollowList.Item>>(emptyList()) }
    var page by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var total by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasLoaded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    suspend fun load(target: Int, append: Boolean) {
        if (isRefreshing || isLoadingMore) return
        if (append) isLoadingMore = true else isRefreshing = true
        if (!append) errorMessage = null
        try {
            val result = repository.getUserFollowList(uid = uid, pn = target)
            if (result.isOk) {
                items = if (append) items + result.items else result.items
                page = target
                hasMore = result.hasMore
                total = result.totalFollowNum
            } else {
                errorMessage = result.errorMessage.ifBlank {
                    "关注列表读取失败（error_code = " + result.errorCode + "）"
                }
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: ("网络请求失败：" + e.javaClass.simpleName)
        } finally {
            isRefreshing = false
            isLoadingMore = false
            hasLoaded = true
        }
    }

    LaunchedEffect(uid) {
        if (!hasLoaded) load(1, append = false)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = if (title.isBlank()) "关注" else title,
                subtitle = if (total > 0) formatCount(total) + " 人" else "",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { load(1, append = false) } }) {
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
            onRefresh = { scope.launch { load(1, append = false) } },
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

                if (hasLoaded && items.isEmpty() && message == null) {
                    item(key = "empty") {
                        MessageCard(
                            text = if (uid <= 0L) {
                                "还没有关注任何人，或者这个账号没有公开关注列表。"
                            } else {
                                "这个用户没有公开的关注列表。"
                            },
                            isError = false,
                        )
                    }
                }

                items(items = items, key = { it.id }) { item ->
                    FollowUserCard(
                        item = item,
                        onClick = {
                            onOpenUser(item.id, item.portrait, item.displayName)
                        },
                    )
                }

                if (hasMore) {
                    item(key = "more") {
                        LoadMoreRow(
                            hasMore = true,
                            isLoading = isLoadingMore,
                            visible = true,
                            onLoadMore = { scope.launch { load(page + 1, append = true) } },
                        )
                    }
                }
            }
        }
    }
}

/** 关注列表里的一行：头像 + 昵称 + 简介。 */
@Composable
private fun FollowUserCard(
    item: UserFollowList.Item,
    onClick: () -> Unit,
) {
    val settings = currentSettings
    val name = remember(item.name, item.nameShow, settings.showBothNameAndNickname) {
        if (settings.showBothNameAndNickname) {
            listOf(item.nameShow, item.name).filter { it.isNotEmpty() }.joinToString(" · ")
        } else {
            item.displayName
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(10.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SafeImage(
                url = portraitUrl(item.portrait),
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape),
                contentDescription = name,
                cornerRadius = 21.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name.ifEmpty { "未知用户" },
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.intro.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.intro,
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
