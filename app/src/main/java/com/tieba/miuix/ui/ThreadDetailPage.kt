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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tieba.miuix.data.TiebaRepository
import com.tieba.miuix.data.toHistory
import com.tieba.miuix.protocol.Page
import com.tieba.miuix.protocol.Post
import com.tieba.miuix.protocol.SimpleForum
import com.tieba.miuix.protocol.SubPostList
import com.tieba.miuix.protocol.ThreadInfo
import com.tieba.miuix.protocol.TiebaConst.PostSortType
import com.tieba.miuix.protocol.toPlainText
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.FloatingActionButton
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
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Reply
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 楼层标签：一楼叫楼主，其余显示"N 楼"。 */
private fun floorLabel(floor: Int): String = when {
    floor <= 1 -> "楼主"
    else -> floor.toString() + " 楼"
}

/** 主题帖详情页：楼层列表 + 下拉刷新 + 加载更多 + 只看楼主 / 倒序 / 跳页。 */
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
    val settings = currentSettings
    val historyStore = LocalHistoryStore.current
    val context = LocalContext.current

    var posts by remember { mutableStateOf<List<Post>>(emptyList()) }
    var thread by remember { mutableStateOf<ThreadInfo?>(null) }
    var forum by remember { mutableStateOf<SimpleForum?>(null) }
    var page by remember { mutableStateOf<Page?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasLoaded by remember { mutableStateOf(false) }

    // 只看楼主 / 倒序：与 TiebaLite 的 title_see_lz / title_sort 对应。
    var onlyThreadAuthor by remember { mutableStateOf(false) }
    var descending by remember { mutableStateOf(false) }
    var showPageJump by remember { mutableStateOf(false) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var viewerImages by remember { mutableStateOf<List<ImageItem>>(emptyList()) }

    // 回帖：只有登录 + 有 tbs 才允许，否则给出明确提示而不是让服务端拒绝。
    val accountStore = LocalAccountStore.current
    var showReply by remember { mutableStateOf(false) }
    var replySending by remember { mutableStateOf(false) }
    var replyError by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var favorited by remember { mutableStateOf(false) }

    // 本次会话里点过赞的楼层，用来切换按钮文案（服务端不下发「我是否已赞」）。
    var agreedPosts by remember { mutableStateOf(emptySet<Long>()) }

    // 楼中楼：按需展开（`/c/f/pb/floor`，匿名可读）。
    var expandedFloors by remember { mutableStateOf(emptySet<Long>()) }
    var floorReplies by remember { mutableStateOf(emptyMap<Long, List<SubPostList>>()) }
    var loadingFloors by remember { mutableStateOf(emptySet<Long>()) }
    var floorPages by remember { mutableStateOf(emptyMap<Long, Page>()) }

    // 回复某条楼中楼时的 quote_id / reply_uid（回复主题帖时都是 0）。
    var replyQuoteId by remember { mutableStateOf(0L) }
    var replyQuoteUid by remember { mutableStateOf(0L) }
    var replyToName by remember { mutableStateOf("") }

    LaunchedEffect(note) {
        if (note.isBlank()) return@LaunchedEffect
        kotlinx.coroutines.delay(2600)
        note = ""
    }

    suspend fun load(targetPage: Int, replace: Boolean) {
        if (tid <= 0L) return
        if (replace) isRefreshing = true else isLoadingMore = true
        errorMessage = null
        try {
            // 匿名浏览模式不请求楼中楼（withComments=false），避免服务端要求登录。
            val response = repository.getPosts(
                tid = tid,
                pn = targetPage,
                sortType = if (descending) PostSortType.DESC else PostSortType.ASC,
                onlyThreadAuthor = onlyThreadAuthor,
                withComments = false,
            )
            val error = response.error
            if (error.isOk) {
                posts = if (replace) response.posts else posts + response.posts
                thread = response.thread ?: thread
                forum = response.forum ?: forum
                page = response.page
                if (settings.recordHistory) {
                    val info = response.thread
                    if (info != null) {
                        historyStore.addThread(
                            info.toHistory(
                                forumName = forumName.ifBlank { response.forum?.name.orEmpty() },
                            ),
                        )
                    }
                }
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

    suspend fun reload() {
        listState.scrollToItem(0)
        load(targetPage = 1, replace = true)
    }

    LaunchedEffect(tid) {
        load(targetPage = 1, replace = true)
    }

    val shownTitle = title.ifBlank { thread?.title.orEmpty().ifBlank { "帖子详情" } }
    val totalPage = page?.totalPage ?: 0
    val currentPage = page?.currentPage ?: 1

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    when {
                        !accountStore.isLoggedIn -> note = "回复需要先登录：到「设置 → 账号」里登录贴吧"
                        !accountStore.hasTbs -> note = "还没取到发帖令牌（tbs），到「设置 → 账号」里刷新一下"
                        else -> {
                            replyError = ""
                            // 悬浮按钮回复的是主题帖本身，清掉楼中楼引用。
                            replyQuoteId = 0L
                            replyQuoteUid = 0L
                            replyToName = ""
                            showReply = true
                        }
                    }
                },
            ) {
                Icon(imageVector = MiuixIcons.Reply, contentDescription = "回复")
            }
        },
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
                    if (onlyThreadAuthor) {
                        if (isNotEmpty()) append(" · ")
                        append("只看楼主")
                    }
                    if (descending) {
                        if (isNotEmpty()) append(" · ")
                        append("倒序")
                    }
                },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
                    }
                },
                actions = {
                    val detailForumName = forumName.ifBlank { forum?.name.orEmpty() }
                    if (detailForumName.isNotBlank()) {
                        IconButton(onClick = {
                            context.startActivity(
                                ForumDetailActivity.intent(context, detailForumName),
                            )
                        }) {
                            Icon(imageVector = MiuixIcons.Info, contentDescription = "吧详情")
                        }
                    }
                    IconButton(onClick = {
                        if (!accountStore.isLoggedIn) {
                            note = "收藏需要先登录：到「设置 → 账号」里登录贴吧"
                        } else {
                            scope.launch {
                                val result = runCatching {
                                    repository.setThreadStored(
                                        tid = tid,
                                        pid = posts.firstOrNull()?.id ?: 0L,
                                        store = !favorited,
                                    )
                                }.getOrNull()
                                if (result?.success == true) {
                                    favorited = !favorited
                                    note = if (favorited) "已加入收藏" else "已取消收藏"
                                } else {
                                    note = result?.displayMessage ?: "操作失败"
                                }
                            }
                        }
                    }) {
                        Icon(imageVector = MiuixIcons.Favorites, contentDescription = "收藏")
                    }
                    IconButton(onClick = {
                        shareThread(context, tid, shownTitle)
                    }) {
                        Icon(imageVector = MiuixIcons.Share, contentDescription = "分享")
                    }
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

                if (note.isNotBlank()) {
                    item(key = "note") {
                        MessageCard(text = note, isError = false)
                    }
                }

                items(
                    items = posts,
                    key = { post -> if (post.id != 0L) post.id else post.hashCode().toLong() },
                ) { post ->
                    val pid = post.id
                    PostCard(
                        post = post,
                        agreed = agreedPosts.contains(pid),
                        replies = floorReplies[pid].orEmpty(),
                        repliesExpanded = expandedFloors.contains(pid),
                        repliesLoading = loadingFloors.contains(pid),
                        replyHasMore = floorPages[pid]?.hasMore == true,
                        onToggleReplies = {
                            if (pid != 0L) {
                                if (expandedFloors.contains(pid)) {
                                    expandedFloors = expandedFloors - pid
                                } else {
                                    expandedFloors = expandedFloors + pid
                                    if (!floorReplies.containsKey(pid)) {
                                        loadingFloors = loadingFloors + pid
                                        scope.launch {
                                            val loaded = runCatching {
                                                repository.getSubPosts(
                                                    tid = tid,
                                                    pid = pid,
                                                    forumId = forum?.id ?: thread?.fid ?: 0L,
                                                    pn = 1,
                                                )
                                            }.getOrNull()
                                            loadingFloors = loadingFloors - pid
                                            if (loaded == null) {
                                                note = "楼中楼加载失败，稍后再试"
                                            } else if (!loaded.error.isOk) {
                                                note = loaded.error.errmsg.ifBlank { "楼中楼加载失败" }
                                            } else {
                                                floorReplies = floorReplies + (pid to loaded.subPosts)
                                                loaded.page?.let { floorPages = floorPages + (pid to it) }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        onLoadMoreReplies = {
                            if (pid != 0L) {
                                val next = (floorPages[pid]?.currentPage ?: 1) + 1
                            loadingFloors = loadingFloors + pid
                            scope.launch {
                                val loaded = runCatching {
                                    repository.getSubPosts(
                                        tid = tid,
                                        pid = pid,
                                        forumId = forum?.id ?: thread?.fid ?: 0L,
                                        pn = next,
                                    )
                                }.getOrNull()
                                loadingFloors = loadingFloors - pid
                                if (loaded != null && loaded.error.isOk) {
                                    floorReplies = floorReplies +
                                        (pid to (floorReplies[pid].orEmpty() + loaded.subPosts))
                                    loaded.page?.let { floorPages = floorPages + (pid to it) }
                                }
                            }
                            }
                        },
                        onReplySubPost = { sub ->
                            if (!accountStore.isLoggedIn) {
                                note = "回复需要先登录：到「设置 → 账号」里登录贴吧"
                            } else if (!accountStore.hasTbs) {
                                note = "还没取到发帖令牌（tbs），到「设置 → 账号」里刷新一下"
                            } else {
                                replyQuoteId = sub.id
                                replyQuoteUid = sub.authorId.takeIf { it > 0L }
                                    ?: sub.author?.id ?: 0L
                                replyToName = sub.author?.displayName
                                    ?.ifBlank { "匿名用户" } ?: "匿名用户"
                                replyError = ""
                                showReply = true
                            }
                        },
                        onOpenImages = { images, index ->
                            viewerImages = images
                            viewerIndex = index
                        },
                        onOpenUser = { portrait, uid -> openUserPage(context, portrait, uid) },
                        onToggleAgree = {
                            if (!accountStore.isLoggedIn) {
                                note = "点赞需要先登录：到「设置 → 账号」里登录贴吧"
                            } else if (!accountStore.hasTbs) {
                                note = "还没取到发帖令牌（tbs），到「设置 → 账号」里刷新一下"
                            } else {
                                val target = !agreedPosts.contains(post.id)
                                scope.launch {
                                    val result = runCatching {
                                        repository.setPostAgree(
                                            threadId = tid,
                                            postId = post.id,
                                            agree = target,
                                            // 楼层用 obj_type=1，楼中楼用 3。
                                            objType = TiebaRepository.OBJ_TYPE_POST,
                                        )
                                    }.getOrNull()
                                    when {
                                        result == null -> note = "网络请求失败，稍后再试"
                                        result.success -> {
                                            agreedPosts = if (target) {
                                                agreedPosts + post.id
                                            } else {
                                                agreedPosts - post.id
                                            }
                                            note = if (target) "已点赞" else "已取消赞"
                                        }
                                        else -> note = result.displayMessage
                                    }
                                }
                            }
                        },
                    )
                }

                item(key = "toolbar") {
                    PostToolbar(
                        onlyThreadAuthor = onlyThreadAuthor,
                        descending = descending,
                        currentPage = currentPage,
                        totalPage = totalPage,
                        onToggleAuthor = {
                            onlyThreadAuthor = !onlyThreadAuthor
                            scope.launch { reload() }
                        },
                        onToggleSort = {
                            descending = !descending
                            scope.launch { reload() }
                        },
                        onJumpPage = { showPageJump = true },
                    )
                }

                item(key = "more") {
                    LoadMoreRow(
                        hasMore = page?.hasMore == true,
                        isLoading = isLoadingMore,
                        visible = posts.isNotEmpty(),
                        onLoadMore = {
                            scope.launch {
                                load(targetPage = currentPage + 1, replace = false)
                            }
                        },
                    )
                }
            }
        }
    }

    if (showPageJump && totalPage > 0) {
        PageJumpDialog(
            currentPage = currentPage,
            totalPage = totalPage,
            onSelect = { target -> scope.launch { load(targetPage = target, replace = true) } },
            onDismiss = { showPageJump = false },
        )
    }

    val index = viewerIndex
    if (index != null) {
        ImageViewerDialog(
            images = viewerImages,
            initialIndex = index,
            onDismiss = { viewerIndex = null },
        )
    }

    if (showReply) {
        ReplyDialog(
            title = if (replyToName.isNotEmpty()) "回复 $replyToName" else shownTitle,
            forumName = forumName.ifBlank { forum?.name.orEmpty() },
            sending = replySending,
            errorMessage = replyError,
            onSend = { content ->
                replySending = true
                replyError = ""
                scope.launch {
                    val result = runCatching {
                        repository.replyPost(
                            tid = tid,
                            fid = thread?.fid ?: forum?.id ?: 0L,
                            kw = forumName.ifBlank { forum?.name.orEmpty() },
                            content = content,
                            quoteId = replyQuoteId,
                            replyUid = replyQuoteUid,
                        )
                    }.getOrElse { e ->
                        com.tieba.miuix.protocol.ReplyResult(
                            errorMessage = e.message ?: "网络请求失败",
                        )
                    }
                    replySending = false
                    if (result.isSuccess) {
                        showReply = false
                        note = "回复成功"
                        // 回到第一页重新拉，顺带把页码、总楼层数刷新掉。
                        runCatching { reload() }
                        floorReplies = emptyMap()
                        floorPages = emptyMap()
                    } else {
                        replyError = result.displayMessage()
                    }
                }
            },
            onDismiss = { if (!replySending) showReply = false },
        )
    }
}

/** 列表底部的楼层操作条：只看楼主、倒序、跳页。 */
@Composable
private fun PostToolbar(
    onlyThreadAuthor: Boolean,
    descending: Boolean,
    currentPage: Int,
    totalPage: Int,
    onToggleAuthor: () -> Unit,
    onToggleSort: () -> Unit,
    onJumpPage: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 12.dp),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarButton(
                label = "只看楼主",
                active = onlyThreadAuthor,
                onClick = onToggleAuthor,
                modifier = Modifier.weight(1f),
            )
            ToolbarButton(
                label = if (descending) "倒序" else "正序",
                active = descending,
                onClick = onToggleSort,
                modifier = Modifier.weight(1f),
            )
            ToolbarButton(
                label = if (totalPage > 0) "第 " + currentPage + "/" + totalPage + " 页" else "跳页",
                active = false,
                onClick = onJumpPage,
                modifier = Modifier.weight(1.2f),
            )
        }
    }
}

@Composable
private fun ToolbarButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = if (active) {
            ButtonDefaults.buttonColors(
                color = MiuixTheme.colorScheme.primary,
                contentColor = MiuixTheme.colorScheme.onPrimary,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.button,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PostCard(
    post: Post,
    agreed: Boolean,
    replies: List<SubPostList>,
    repliesExpanded: Boolean,
    repliesLoading: Boolean,
    replyHasMore: Boolean,
    onToggleReplies: () -> Unit,
    onLoadMoreReplies: () -> Unit,
    onReplySubPost: (SubPostList) -> Unit,
    onOpenImages: (List<ImageItem>, Int) -> Unit,
    onOpenUser: (portrait: String, uid: Long) -> Unit,
    onToggleAgree: () -> Unit,
) {
    val images = post.content.toImages()
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(16.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val authorName = post.authorName.ifBlank { "匿名用户" }
            val portrait = post.author?.portrait.orEmpty()
            val authorUid = post.authorId.takeIf { it > 0L } ?: post.author?.id ?: 0L
            Text(
                text = authorName,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clickable(enabled = portrait.isNotEmpty()) {
                        onOpenUser(portrait, authorUid)
                    },
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

        val text = post.plainText
        if (text.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = text,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }

        if (images.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            PostImageGrid(
                images = images,
                onOpenImage = { index -> onOpenImages(images, index) },
            )
        }

        // 楼中楼按需加载（`/c/f/pb/floor`）。`post.subPosts` 只有在请求带
        // `with_floor` 时才有内容，所以这里以按需拉取的 `replies` 为准。
        val replyCount = maxOf(post.subPostNumber, replies.size)
        if (replyCount > 0) {
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 10.dp,
                insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainerHigh),
            ) {
                if (repliesExpanded) {
                    replies.forEach { sub ->
                        SubPostItem(sub = sub, onReply = onReplySubPost)
                    }
                    if (repliesLoading) {
                        Text(
                            text = "正在加载…",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    } else if (replyHasMore) {
                        Text(
                            text = "查看更多回复",
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onLoadMoreReplies() }
                                .padding(vertical = 6.dp),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = "收起",
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onToggleReplies() }
                            .padding(vertical = 6.dp),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                } else {
                    Text(
                        text = "查看 $replyCount 条回复",
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onToggleReplies() }
                            .padding(vertical = 2.dp),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (post.agreeNum > 0 || post.id != 0L) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (post.agreeNum > 0) "赞同 " + post.agreeNum else "",
                    modifier = Modifier.weight(1f),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                if (post.id != 0L) {
                    Text(
                        text = if (agreed) "已赞" else "点赞",
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onToggleAgree() }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MiuixTheme.textStyles.footnote2,
                        color = if (agreed) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SubPostItem(sub: SubPostList, onReply: (SubPostList) -> Unit) {
    val context = LocalContext.current
    val portrait = sub.author?.portrait.orEmpty()
    val uid = sub.authorId.takeIf { it > 0L } ?: sub.author?.id ?: 0L
    val authorName = sub.author?.displayName?.ifBlank { "匿名用户" } ?: "匿名用户"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = authorName,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clickable(enabled = portrait.isNotEmpty()) {
                        openUserPage(context, portrait, uid)
                    },
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (sub.id != 0L) {
                Text(
                    text = "回复",
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onReply(sub) }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        val text = sub.content.toPlainText()
        if (text.isNotBlank()) {
            Text(
                text = text,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceContainer,
            )
        }
    }
}
