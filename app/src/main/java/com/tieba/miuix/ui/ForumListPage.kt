// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0

package com.tieba.miuix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tieba.miuix.data.ThreadListMode
import com.tieba.miuix.data.TiebaRepository
import com.tieba.miuix.protocol.ForumHome
import com.tieba.miuix.protocol.ForumSearchItem
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
    onOpenUser: (portrait: String) -> Unit = {},
    bottomPadding: Dp = 16.dp,
    /**
     * 由外层页面（首页）提供顶栏时置 true：
     * 此时不再画自己的 `SmallTopAppBar`，内容整体下移一行给外层顶栏让位。
     */
    embedded: Boolean = false,
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
    var forumHome by remember { mutableStateOf<ForumHome?>(null) }
    var actionThread by remember { mutableStateOf<ThreadInfo?>(null) }
    var note by remember { mutableStateOf("") }
    var forumSuggestions by remember { mutableStateOf<List<ForumSearchItem>>(emptyList()) }
    val searchHistoryStore = LocalSearchHistoryStore.current

    // 输入吧名时给候选列表：打错一个字就进不去吧，这里直接让用户从候选里挑。
    LaunchedEffect(keyword, forumName) {
        val query = keyword.trim()
        if (query.isEmpty() || query == forumName) {
            forumSuggestions = emptyList()
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(350)
        val result = runCatching { repository.searchForum(query) }.getOrNull()
        forumSuggestions = if (result == null) {
            emptyList()
        } else {
            buildList {
                result.exact?.let { add(it) }
                addAll(result.fuzzy.filter { fuzzy -> fuzzy.id != result.exact?.id })
            }.take(8)
        }
    }

    // 复制成功之类的提示展示几秒后自动消失，免得一直占着列表顶部。
    LaunchedEffect(note) {
        if (note.isBlank()) return@LaunchedEffect
        kotlinx.coroutines.delay(2200)
        note = ""
    }

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

    // 逛过的吧进浏览历史（设置里可以关掉）。只在真的取到帖子后记一次，
    // 免得把打错的吧名也写进历史。
    val historyStore = LocalHistoryStore.current
    val settings = currentSettings
    LaunchedEffect(forumName, threads, settings.recordHistory) {
        if (threads.isEmpty() || forumName.isBlank() || !settings.recordHistory) return@LaunchedEffect
        historyStore.addForum(forumName)
    }

    // 首页的「关注的吧 / 热门吧」只在真正进入某个吧之后才没必要再加载，
    // 所以这里只在首次进页面时抓一次；失败时静默降级（那一块直接不显示）。
    LaunchedEffect(Unit) {
        runCatching { repository.getForumHome() }.getOrNull()?.let { forumHome = it }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (!embedded) {
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
            }
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
                            if (trimmed.isNotEmpty()) searchHistoryStore.add(trimmed)
                        },
                        suggestions = forumSuggestions,
                        onPickSuggestion = { picked ->
                            keyword = picked.name
                            onKeywordChange(picked.name)
                            forumName = picked.name
                            forumSuggestions = emptyList()
                            searchHistoryStore.add(picked.name)
                        },
                        history = if (forumName.isBlank()) {
                            searchHistoryStore.keywords.take(8)
                        } else {
                            emptyList()
                        },
                        onPickHistory = { picked ->
                            keyword = picked
                            onKeywordChange(picked)
                            forumName = picked
                            searchHistoryStore.add(picked)
                        },
                    )
                }

                val message = errorMessage
                if (message != null) {
                    item(key = "error") {
                        MessageCard(text = message, isError = true)
                    }
                }

                if (note.isNotBlank()) {
                    item(key = "note") {
                        MessageCard(text = note, isError = false)
                    }
                }

                // 还没进入任何吧时，顶部给一条「关注的吧 / 热门吧 / 最近逛过」快捷入口。
                if (forumName.isBlank()) {
                    val home = forumHome
                    if (home != null && !home.isEmpty) {
                        item(key = "forum-home") {
                            ForumHomeCard(
                                home = home,
                                onPick = { picked ->
                                    keyword = picked
                                    onKeywordChange(picked)
                                    forumName = picked
                                },
                            )
                        }
                    }
                    val recent = if (settings.homeShowRecentForum) {
                        historyStore.forums.take(10)
                    } else {
                        emptyList()
                    }
                    if (recent.isNotEmpty()) {
                        item(key = "recent-forum") {
                            RecentForumCard(
                                names = recent.map { it.name },
                                onPick = { picked ->
                                    keyword = picked
                                    onKeywordChange(picked)
                                    forumName = picked
                                },
                            )
                        }
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
                        if (settings.threadListMode == ThreadListMode.SINGLE) {
                            ThreadRow(
                                thread = thread,
                                onClick = { onOpenThread(thread.id, thread.title, forumName) },
                                onLongClick = { actionThread = thread },
                            )
                        } else {
                            ThreadCard(
                                thread = thread,
                                onClick = { onOpenThread(thread.id, thread.title, forumName) },
                                onLongClick = { actionThread = thread },
                            )
                        }
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

    val sheetThread = actionThread
    if (sheetThread != null) {
        ThreadActionSheet(
            thread = sheetThread,
            onDismiss = { actionThread = null },
            onOpenThread = {
                actionThread = null
                onOpenThread(sheetThread.id, sheetThread.title, forumName)
            },
            onOpenForum = { name ->
                if (name.isNotBlank()) {
                    keyword = name
                    onKeywordChange(name)
                    forumName = name
                }
            },
            onOpenUser = { portrait ->
                actionThread = null
                onOpenUser(portrait)
            },
            onNote = { note = it },
        )
    }}

@Composable
private fun SearchCard(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    sortType: Int,
    onSortTypeChange: (Int) -> Unit,
    onSubmit: () -> Unit,
    suggestions: List<ForumSearchItem> = emptyList(),
    onPickSuggestion: (ForumSearchItem) -> Unit = {},
    history: List<String> = emptyList(),
    onPickHistory: (String) -> Unit = {},
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
        if (suggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            suggestions.forEach { suggestion ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    cornerRadius = 10.dp,
                    insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.surfaceContainerHigh,
                    ),
                    pressFeedbackType = PressFeedbackType.Sink,
                    showIndication = true,
                    onClick = { onPickSuggestion(suggestion) },
                ) {
                    Text(
                        text = if (suggestion.nameShow.isBlank()) {
                            suggestion.name
                        } else {
                            suggestion.nameShow
                        },
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceContainer,
                    )
                    Text(
                        text = suggestion.name + "吧 · " + formatCount(suggestion.postNum) + " 帖",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
        if (history.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(items = history, key = { it }) { item ->
                    Card(
                        cornerRadius = 12.dp,
                        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.surfaceContainerHigh,
                        ),
                        pressFeedbackType = PressFeedbackType.Sink,
                        showIndication = true,
                        onClick = { onPickHistory(item) },
                    ) {
                        Text(
                            text = item,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                        )
                    }
                }
            }
        }
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
private fun ThreadRow(
    thread: ThreadInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val showBothName = currentSettings.showBothNameAndNickname
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = onClick,
        onLongPress = onLongClick,
    ) {
        Text(
            text = thread.title.ifBlank { "（无标题）" },
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
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
                text = thread.authorName(showBothName),
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

@Composable
private fun ThreadCard(
    thread: ThreadInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val showBothName = currentSettings.showBothNameAndNickname
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(16.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = onClick,
        onLongPress = onLongClick,
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
                text = thread.authorName(showBothName),
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

/**
 * 首页的快捷入口卡：拿 `/mg/o/getForumHome` 的数据横排展示。
 *
 * 登录后优先显示关注的吧，未登录只有热门吧（服务端不下发 `concern_forum`）。
 */
@Composable
private fun ForumHomeCard(
    home: ForumHome,
    onPick: (String) -> Unit,
) {
    val forums = home.display
    if (forums.isEmpty()) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Text(
            text = if (home.concerned.isNotEmpty()) "关注的吧" else "热门吧",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow {
            items(items = forums, key = { it.id.toString() + "-" + it.name }) { item ->
                Card(
                    modifier = Modifier.padding(end = 8.dp),
                    cornerRadius = 10.dp,
                    insideMargin = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.surfaceContainerHigh,
                    ),
                    pressFeedbackType = PressFeedbackType.Sink,
                    showIndication = true,
                    onClick = { onPick(item.name) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.avatarUrl.isNotEmpty()) {
                            SafeImage(
                                url = item.avatarUrl,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(14.dp)),
                                contentDescription = item.name,
                                cornerRadius = 14.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = item.name,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** 首页的「最近逛过的吧」：纯本地历史，横排文字卡。 */
@Composable
private fun RecentForumCard(
    names: List<String>,
    onPick: (String) -> Unit,
) {
    if (names.isEmpty()) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Text(
            text = "最近逛过",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow {
            items(items = names, key = { it }) { name ->
                Card(
                    modifier = Modifier.padding(end = 8.dp),
                    cornerRadius = 10.dp,
                    insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.surfaceContainerHigh,
                    ),
                    pressFeedbackType = PressFeedbackType.Sink,
                    showIndication = true,
                    onClick = { onPick(name) },
                ) {
                    Text(
                        text = name,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
