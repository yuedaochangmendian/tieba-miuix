// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tieba.miuix.data.TiebaRepository
import com.tieba.miuix.protocol.ForumSearchItem
import com.tieba.miuix.protocol.ThreadSearchItem
import com.tieba.miuix.protocol.UserSearchItem
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

private const val TAB_FORUM = 0
private const val TAB_THREAD = 1
private const val TAB_USER = 2

/**
 * 搜索页：吧 / 帖 / 用户三个 Tab。
 *
 * 对应 TiebaLite 的三条搜索通道：
 * - 吧：`GET /mo/q/search/forum`
 * - 帖：`GET /mo/q/search/thread`（`fname` 非空时即吧内搜索）
 * - 用户：`GET /mo/q/search/user`
 */
@Composable
fun SearchPage(
    bottomPadding: Dp,
    onOpenThread: (tid: Long, forumName: String) -> Unit,
    onOpenForum: (String) -> Unit,
) {
    val repository = remember { TiebaRepository() }
    val scope = rememberCoroutineScope()
    val searchHistory = LocalSearchHistoryStore.current
    val scrollBehavior = MiuixScrollBehavior()

    var keyword by remember { mutableStateOf("") }
    var forumFilter by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(TAB_THREAD) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }

    var forumExact by remember { mutableStateOf<ForumSearchItem?>(null) }
    var forumFuzzy by remember { mutableStateOf<List<ForumSearchItem>>(emptyList()) }
    var threadItems by remember { mutableStateOf<List<ThreadSearchItem>>(emptyList()) }
    var userExact by remember { mutableStateOf<UserSearchItem?>(null) }
    var userFuzzy by remember { mutableStateOf<List<UserSearchItem>>(emptyList()) }

    fun runSearch(targetPage: Int) {
        val word = keyword.trim()
        if (word.isEmpty()) return
        scope.launch {
            loading = true
            error = ""
            searched = true
            runCatching {
                when (selectedTab) {
                    TAB_FORUM -> {
                        val result = repository.searchForum(word)
                        forumExact = result.exact
                        forumFuzzy = result.fuzzy
                        hasMore = false
                    }

                    TAB_USER -> {
                        val result = repository.searchUser(word)
                        userExact = result.exact
                        userFuzzy = result.fuzzy
                        hasMore = false
                    }

                    else -> {
                        val result = repository.searchThread(
                            keyword = word,
                            pn = targetPage,
                            forumName = forumFilter,
                        )
                        threadItems = if (targetPage <= 1) result.items else threadItems + result.items
                        hasMore = result.hasMore
                    }
                }
                page = targetPage
            }.onFailure { throwable ->
                error = throwable.message ?: throwable.javaClass.simpleName
            }
            if (error.isEmpty()) searchHistory.add(word)
            loading = false
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(title = "搜索", scrollBehavior = scrollBehavior)
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            SearchBar(
                keyword = keyword,
                onKeywordChange = { keyword = it },
                forumFilter = forumFilter,
                onClearForumFilter = {
                    forumFilter = null
                    if (keyword.isNotBlank()) runSearch(1)
                },
                onSubmit = {
                    // 换 Tab 后重新搜一次，避免看到上一个 Tab 的旧结果。
                    page = 1
                    runSearch(1)
                },
            )

            TabRow(
                tabs = listOf("吧", "帖子", "用户"),
                selectedTabIndex = selectedTab,
                onTabSelected = { index ->
                    selectedTab = index
                    page = 1
                    if (keyword.isNotBlank()) runSearch(1)
                },
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            if (error.isNotEmpty()) {
                MessageCard(
                    text = "搜索失败：" + error,
                    isError = true,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            when (selectedTab) {
                TAB_FORUM -> ForumResultList(
                    exact = forumExact,
                    fuzzy = forumFuzzy,
                    searched = searched,
                    loading = loading,
                    history = searchHistory.keywords,
                    bottomPadding = bottomPadding,
                    onOpen = onOpenForum,
                    onHistoryClick = {
                        keyword = it
                        page = 1
                        runSearch(1)
                    },
                )

                TAB_USER -> UserResultList(
                    exact = userExact,
                    fuzzy = userFuzzy,
                    searched = searched,
                    loading = loading,
                    bottomPadding = bottomPadding,
                )

                else -> ThreadResultList(
                    items = threadItems,
                    searched = searched,
                    loading = loading,
                    hasMore = hasMore,
                    history = searchHistory.keywords,
                    bottomPadding = bottomPadding,
                    onOpen = onOpenThread,
                    onLoadMore = { runSearch(page + 1) },
                    onHistoryClick = {
                        keyword = it
                        page = 1
                        runSearch(1)
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    forumFilter: String?,
    onClearForumFilter: () -> Unit,
    onSubmit: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        TextField(
            value = keyword,
            onValueChange = onKeywordChange,
            modifier = Modifier.fillMaxWidth(),
            label = if (forumFilter.isNullOrEmpty()) "搜索贴吧 / 帖子 / 用户" else "在「" + forumFilter + "」内搜索",
            useLabelAsPlaceholder = true,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
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
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!forumFilter.isNullOrEmpty()) {
                Card(
                    modifier = Modifier.padding(end = 8.dp),
                    cornerRadius = 8.dp,
                    insideMargin = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.primaryContainer,
                        contentColor = MiuixTheme.colorScheme.onPrimaryContainer,
                    ),
                    pressFeedbackType = PressFeedbackType.Sink,
                    showIndication = true,
                    onClick = onClearForumFilter,
                ) {
                    Text(text = "吧内：" + forumFilter + " ✕", style = MiuixTheme.textStyles.footnote2)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = onSubmit, enabled = keyword.isNotBlank()) {
                Text(text = "搜索", style = MiuixTheme.textStyles.button)
            }
        }
    }
}

@Composable
private fun ForumResultList(
    exact: ForumSearchItem?,
    fuzzy: List<ForumSearchItem>,
    searched: Boolean,
    loading: Boolean,
    history: List<String>,
    bottomPadding: Dp,
    onOpen: (String) -> Unit,
    onHistoryClick: (String) -> Unit,
) {
    if (!searched) {
        SearchHistoryHint(history = history, bottomPadding = bottomPadding, onClick = onHistoryClick)
        return
    }
    val items = buildList {
        exact?.let { add(it) }
        addAll(fuzzy)
    }
    if (items.isEmpty()) {
        CenterHint(text = if (loading) "搜索中…" else "没有找到相关贴吧")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.name }) { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 12.dp,
                insideMargin = PaddingValues(14.dp),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                onClick = { onOpen(item.name) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SafeImage(
                        url = item.avatar,
                        modifier = Modifier.size(44.dp),
                        cornerRadius = 22.dp,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.displayName,
                                style = MiuixTheme.textStyles.body1,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (item.isExact) {
                                Spacer(modifier = Modifier.width(6.dp))
                                ExactBadge()
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = buildString {
                                append(formatCount(item.concernNum)).append(" 关注 · ")
                                append(formatCount(item.postNum)).append(" 帖子")
                                val desc = item.intro.ifEmpty { item.slogan }
                                if (desc.isNotEmpty()) append(" · ").append(desc)
                            },
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
}

@Composable
private fun UserResultList(
    exact: UserSearchItem?,
    fuzzy: List<UserSearchItem>,
    searched: Boolean,
    loading: Boolean,
    bottomPadding: Dp,
) {
    val context = LocalContext.current
    val showBothName = currentSettings.showBothNameAndNickname
    if (!searched) {
        CenterHint(text = "输入关键词后可以搜吧、搜帖、搜用户")
        return
    }
    val items = buildList {
        exact?.let { add(it) }
        addAll(fuzzy)
    }
    if (items.isEmpty()) {
        CenterHint(text = if (loading) "搜索中…" else "没有找到相关用户")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.id.toString() + it.name }) { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 12.dp,
                insideMargin = PaddingValues(14.dp),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                pressFeedbackType = PressFeedbackType.Sink,
                showIndication = true,
                onClick = { openUserPage(context, item.portrait, item.id) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SafeImage(
                        url = item.portraitUrl,
                        modifier = Modifier.size(44.dp),
                        cornerRadius = 22.dp,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.displayNameWith(showBothName),
                            style = MiuixTheme.textStyles.body1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = buildString {
                                if (item.name.isNotEmpty() && item.name != item.displayName) {
                                    append("@").append(item.name).append(" · ")
                                }
                                append(formatCount(item.fansNum)).append(" 粉丝")
                                if (item.intro.isNotEmpty()) append(" · ").append(item.intro)
                            },
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadResultList(
    items: List<ThreadSearchItem>,
    searched: Boolean,
    loading: Boolean,
    hasMore: Boolean,
    history: List<String>,
    bottomPadding: Dp,
    onOpen: (tid: Long, forumName: String) -> Unit,
    onLoadMore: () -> Unit,
    onHistoryClick: (String) -> Unit,
) {
    val context = LocalContext.current
    var sheetItem by remember { mutableStateOf<ThreadSearchItem?>(null) }
    var note by remember { mutableStateOf("") }
    LaunchedEffect(note) {
        if (note.isBlank()) return@LaunchedEffect
        kotlinx.coroutines.delay(2200)
        note = ""
    }
    if (!searched) {
        SearchHistoryHint(history = history, bottomPadding = bottomPadding, onClick = onHistoryClick)
        return
    }
    if (items.isEmpty()) {
        CenterHint(text = if (loading) "搜索中…" else "没有找到相关帖子")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.tid.toString() + "_" + it.pid }) { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 12.dp,
                insideMargin = PaddingValues(14.dp),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                onClick = { onOpen(item.tid, item.forumName) },
                onLongPress = { sheetItem = item },
            ) {
                Column {
                    Text(
                        text = item.title.ifEmpty { "（无标题）" },
                        style = MiuixTheme.textStyles.body1,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.content.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.content,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (item.images.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item.images.take(3).forEach { url ->
                                SafeImage(
                                    url = url,
                                    modifier = Modifier.size(72.dp),
                                    cornerRadius = 8.dp,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (item.authorName.isNotEmpty()) {
                        Text(
                            text = item.authorName,
                            modifier = Modifier.clickable(enabled = item.authorPortrait.isNotEmpty()) {
                                openUserPage(context, item.authorPortrait, item.authorUid)
                            },
                            style = MiuixTheme.textStyles.footnote2,
                            color = if (item.authorPortrait.isNotEmpty()) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.onSurfaceVariantSummary
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = buildString {
                            if (item.forumName.isNotEmpty()) append(item.forumName).append(" · ")
                            append(formatCount(item.replyNum)).append(" 回复")
                            if (item.agreeNum > 0) append(" · ").append(formatCount(item.agreeNum)).append(" 赞")
                        },
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        item(key = "load-more") {
            LoadMoreRow(
                hasMore = hasMore,
                isLoading = loading,
                visible = true,
                onLoadMore = onLoadMore,
            )
        }
    }

    if (note.isNotBlank()) {
        MessageCard(
            text = note,
            isError = false,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }

    val sheet = sheetItem
    if (sheet != null) {
        SearchThreadActionSheet(
            item = sheet,
            onDismiss = { sheetItem = null },
            onNote = { note = it },
        )
    }
}

/** 还没搜过时显示的搜索历史。 */
@Composable
private fun SearchHistoryHint(
    history: List<String>,
    bottomPadding: Dp,
    onClick: (String) -> Unit,
) {
    if (history.isEmpty()) {
        CenterHint(text = "输入关键词后可以搜吧、搜帖、搜用户")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "history-title") { SectionTitle("搜索历史") }
        items(history, key = { it }) { word ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 10.dp,
                insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                onClick = { onClick(word) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = MiuixIcons.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = word,
                        style = MiuixTheme.textStyles.body2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 「精确匹配」小标签。 */
@Composable
private fun ExactBadge() {
    Box(
        modifier = Modifier
            .padding(start = 6.dp),
    ) {
        Card(
            cornerRadius = 6.dp,
            insideMargin = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.primaryContainer,
                contentColor = MiuixTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Text(
                text = "精确",
                style = MiuixTheme.textStyles.footnote2,
                textAlign = TextAlign.Center,
            )
        }
    }
}
