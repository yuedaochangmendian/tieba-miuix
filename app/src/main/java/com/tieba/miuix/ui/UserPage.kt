// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tieba.miuix.data.TiebaRepository
import com.tieba.miuix.protocol.UserCard
import com.tieba.miuix.protocol.UserPostItem
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
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 用户资料页。
 *
 * 数据全部来自**匿名可用**的接口：
 * - 名片：`GET /c/u/pc/userCard`（昵称、吧龄、获赞/粉丝/关注数、签名、IP 归属地）
 * - 发言：`POST /c/u/feed/userpost`（主题与回复是同一个接口，靠 `is_thread` 区分）
 *
 * 关注 / 取关 / 私信都需要登录，本页只给提示，不做假的成功态。
 *
 * @param portrait 帖子里拿到的 portrait（可能是纯串也可能是完整 URL，内部会归一）
 */
@Composable
fun UserPage(
    portrait: String,
    onBack: () -> Unit,
    onOpenThread: (tid: Long, title: String, forumName: String) -> Unit,
    uid: Long = 0L,
    bottomPadding: Dp = 0.dp,
) {
    val repository: TiebaRepository = LocalRepository.current
    val accountStore = LocalAccountStore.current
    val scope = rememberCoroutineScope()

    var card by remember { mutableStateOf<UserCard?>(null) }
    var posts by remember { mutableStateOf<List<UserPostItem>>(emptyList()) }
    var tab by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var loadedOnce by remember { mutableStateOf(false) }
    var showFollowHint by remember { mutableStateOf(false) }

    val scrollBehavior = MiuixScrollBehavior()
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()

    suspend fun reloadFrom(targetTab: Int) {
        isLoading = true
        errorMessage = ""
        runCatching { repository.getUserCard(portrait) }
            .onSuccess { loaded -> if (loaded != null) card = loaded }
            .onFailure { errorMessage = it.message ?: "用户资料加载失败" }
        // 名片接口不返回 uid，优先用调用方带进来的，实在没有才退化成不可用。
        val userId = card?.tiebaUid?.takeIf { it > 0L } ?: uid
        if (userId <= 0L) {
            posts = emptyList()
            hasMore = false
            isLoading = false
            loadedOnce = true
            if (errorMessage.isEmpty()) {
                errorMessage = "接口没有返回这个用户的 ID，暂时看不了他的发言。"
            }
            return
        }
        runCatching { repository.getUserPosts(uid = userId, pn = 1, isThread = targetTab == 0) }
            .onSuccess { result ->
                posts = result.items
                hasMore = result.hasMore
                page = 1
            }
            .onFailure { errorMessage = it.message ?: "发言列表加载失败" }
        isLoading = false
        loadedOnce = true
    }

    suspend fun loadMore() {
        if (isLoadingMore || !hasMore) return
        val userId = card?.tiebaUid?.takeIf { it > 0L } ?: uid
        if (userId <= 0L) return
        isLoadingMore = true
        runCatching { repository.getUserPosts(uid = userId, pn = page + 1, isThread = tab == 0) }
            .onSuccess { result ->
                posts = posts + result.items
                hasMore = result.hasMore
                page += 1
            }
            .onFailure { errorMessage = it.message ?: "加载更多失败" }
        isLoadingMore = false
    }

    LaunchedEffect(portrait) { reloadFrom(0) }

    val shownTitle = card?.displayName?.ifEmpty { "用户资料" } ?: "用户资料"

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = shownTitle,
                subtitle = if (isLoading) "加载中…" else "贴吧用户",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { reloadFrom(tab) } }) {
                        Icon(MiuixIcons.Refresh, "刷新")
                    }
                },
            )
        },
    ) { innerPadding ->
        PullToRefresh(
            isRefreshing = isLoading && loadedOnce,
            onRefresh = { scope.launch { reloadFrom(tab) } },
            modifier = Modifier.fillMaxSize(),
            pullToRefreshState = pullToRefreshState,
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + bottomPadding,
            ),
            topAppBarScrollBehavior = scrollBehavior,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 4.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (errorMessage.isNotEmpty()) {
                    item(key = "error") { MessageCard(errorMessage, isError = true) }
                }
                item(key = "profile") {
                    ProfileCard(
                        card = card,
                        isLoading = isLoading && !loadedOnce,
                        showFollowHint = showFollowHint,
                        isLoggedIn = accountStore.isLoggedIn,
                        onFollowClick = { showFollowHint = true },
                        onMessageClick = { showFollowHint = true },
                    )
                }
                item(key = "tabs") {
                    TabRow(
                        tabs = listOf("他的主题", "他的回复"),
                        selectedTabIndex = tab,
                        onTabSelected = { index ->
                            if (index != tab) {
                                tab = index
                                scope.launch {
                                    listState.scrollToItem(0)
                                    reloadFrom(index)
                                }
                            }
                        },
                    )
                }
                if (isLoading && posts.isEmpty() && errorMessage.isEmpty()) {
                    item(key = "loading") {
                        MessageCard("正在加载发言…", isError = false)
                    }
                }
                items(
                    items = posts,
                    key = { item ->
                        if (item.postId != 0L) item.postId else item.threadId
                    },
                ) { item ->
                    UserPostCard(
                        item = item,
                        onClick = {
                            if (item.threadId != 0L) {
                                onOpenThread(item.threadId, item.title, item.forumName)
                            }
                        },
                    )
                }
                if (loadedOnce && posts.isEmpty() && errorMessage.isEmpty()) {
                    item(key = "empty") {
                        MessageCard(
                            text = if (tab == 0) {
                                "他还没有公开的主题，或者已设置为不可见。"
                            } else {
                                "没有取到回复。回复接口对部分用户会返回空列表。"
                            },
                            isError = false,
                        )
                    }
                }
                item(key = "more") {
                    LoadMoreRow(
                        hasMore = hasMore,
                        isLoading = isLoadingMore,
                        visible = posts.isNotEmpty(),
                        onLoadMore = { scope.launch { loadMore() } },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(
    card: UserCard?,
    isLoading: Boolean,
    showFollowHint: Boolean,
    isLoggedIn: Boolean,
    onFollowClick: () -> Unit,
    onMessageClick: () -> Unit,
) {
    Card(
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(16.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val name = card?.displayName.orEmpty()
            SafeImage(
                url = card?.portraitUrl.orEmpty(),
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape),
                contentDescription = name.ifEmpty { "头像" },
                cornerRadius = 32.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name.ifEmpty { if (isLoading) "加载中…" else "未知用户" },
                    style = MiuixTheme.textStyles.title4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                val meta = buildList {
                    if (!card?.genderLabel.isNullOrEmpty()) add(card!!.genderLabel)
                    if (!card?.tbAge.isNullOrEmpty()) add("吧龄 " + card!!.tbAge)
                    if (!card?.ipAddress.isNullOrEmpty()) add("IP " + card!!.ipAddress)
                }.joinToString(" · ")
                Text(
                    text = meta.ifEmpty { "还没有更多公开信息" },
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            StatColumn("获赞", card?.agreeNum ?: 0, Modifier.weight(1f))
            StatColumn("粉丝", card?.fansNum ?: 0, Modifier.weight(1f))
            StatColumn("关注", card?.concernNum ?: 0, Modifier.weight(1f))
        }

        if (!card?.intro.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = card!!.intro,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceContainer,
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onFollowClick,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(if (card?.hasConcerned == true) "已关注" else "关注")
            }
            Button(
                onClick = onMessageClick,
                modifier = Modifier.weight(1f),
            ) {
                Text("私信")
            }
        }

        if (showFollowHint) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (isLoggedIn) {
                    "关注 / 私信功能还没做，这一版先把资料和发言读出来。"
                } else {
                    "关注 / 私信需要先登录（设置 → 登录贴吧），当前只能匿名查看。"
                },
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun StatColumn(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatCount(value),
            style = MiuixTheme.textStyles.title4,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun UserPostCard(item: UserPostItem, onClick: () -> Unit) {
    Card(
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(14.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        onClick = onClick,
    ) {
        if (item.forumName.isNotEmpty()) {
            Text(
                text = item.forumName + "吧",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
        }
        Text(
            text = item.displayTitle.ifEmpty { "（无标题）" },
            style = MiuixTheme.textStyles.body1,
            fontWeight = if (item.isThread) FontWeight.Medium else FontWeight.Normal,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.content.isNotEmpty() && item.content != item.title) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = item.content,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))
        val meta = buildList {
            add(formatTime(item.createTime))
            if (item.replyNum > 0) add("回复 " + formatCount(item.replyNum))
            if (item.agreeNum > 0) add("赞同 " + formatCount(item.agreeNum))
            if (item.isDeleted) add("已删除")
        }.joinToString(" · ")
        Text(
            text = meta,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
