// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.tieba.miuix.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tieba.miuix.data.TiebaRepository
import com.tieba.miuix.protocol.Page
import com.tieba.miuix.protocol.Post
import com.tieba.miuix.protocol.SimpleForum
import com.tieba.miuix.protocol.SubPostList
import com.tieba.miuix.protocol.ThreadInfo
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
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 楼层标签：一楼叫楼主，其余显示"N 楼"。 */
private fun floorLabel(floor: Int): String = when {
    floor <= 1 -> "楼主"
    else -> floor.toString() + " 楼"
}

/** 主题帖详情页：楼层列表 + 下拉刷新 + 加载更多。 */
@Composable
fun ThreadDetailPage(
    repository: TiebaRepository,
    tid: Long,
    title: String,
    forumName: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()

    var posts by remember { mutableStateOf<List<Post>>(emptyList()) }
    var thread by remember { mutableStateOf<ThreadInfo?>(null) }
    var forum by remember { mutableStateOf<SimpleForum?>(null) }
    var page by remember { mutableStateOf<Page?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasLoaded by remember { mutableStateOf(false) }

    suspend fun load(targetPage: Int, replace: Boolean) {
        if (tid <= 0L) return
        if (replace) isRefreshing = true else isLoadingMore = true
        errorMessage = null
        try {
            // 匿名浏览模式不请求楼中楼（withComments=false），避免服务端要求登录。
            val response = repository.getPosts(
                tid = tid,
                pn = targetPage,
                withComments = false,
            )
            val error = response.error
            if (error.isOk) {
                posts = if (replace) response.posts else posts + response.posts
                thread = response.thread ?: thread
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

    LaunchedEffect(tid) {
        load(targetPage = 1, replace = true)
    }

    val shownTitle = title.ifBlank { thread?.title.orEmpty().ifBlank { "帖子详情" } }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = shownTitle,
                subtitle = buildString {
                    if (forumName.isNotBlank()) {
                        append(forumName)
                        append("吧")
                    }
                    val p = page
                    if (p != null && p.totalPage > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("第 ")
                        append(p.currentPage)
                        append("/")
                        append(p.totalPage)
                        append(" 页")
                    }
                },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
                    }
                },
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
            bottom = innerPadding.calculateBottomPadding() + 16.dp,
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
                val message = errorMessage
                if (message != null) {
                    item(key = "error") {
                        MessageCard(text = message, isError = true)
                    }
                }

                if (hasLoaded && posts.isEmpty() && message == null) {
                    item(key = "empty") {
                        MessageCard(text = "没有取到楼层，下拉可以重试。", isError = false)
                    }
                }

                items(
                    count = posts.size,
                    key = { index ->
                        val id = posts[index].id
                        if (id != 0L) id else "post-" + index
                    },
                ) { index ->
                    PostCard(post = posts[index])
                }

                item(key = "more") {
                    LoadMoreRow(
                        hasMore = page?.hasMore == true,
                        isLoading = isLoadingMore,
                        visible = posts.isNotEmpty(),
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
private fun PostCard(post: Post) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(16.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = post.authorName.ifBlank { "匿名用户" },
                modifier = Modifier.weight(1f, fill = false),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = floorLabel(post.floor),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }

        val time = formatTime(post.time)
        if (time.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = time,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = post.plainText.ifBlank { "（空内容）" },
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface,
        )

        if (post.subPosts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 10.dp,
                insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainerHigh),
            ) {
                post.subPosts.forEach { sub ->
                    SubPostItem(sub = sub)
                }
            }
        }

        if (post.agreeNum > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "赞同 " + post.agreeNum,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun SubPostItem(sub: SubPostList) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = sub.author?.displayName?.ifBlank { "匿名用户" } ?: "匿名用户",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = sub.content.toPlainText().ifBlank { "（空内容）" },
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceContainer,
        )
    }
}
