// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.tieba.miuix.protocol.ConcernedForum
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 「我的」页。
 *
 * 结构（自上而下）：账号卡片 → 关注的吧 → 入口（收藏 / 浏览历史 / 搜索历史 / 设置）。
 *
 * 关注的吧用 `/c/f/forum/getforumlist`（`force_login` 通道），未登录时只保留账号卡片与
 * 本地入口，不给错误提示 —— 匿名浏览本来就是这个 App 的默认状态。
 * 注意：关注的吧需要登录，但**逛过的吧**是本地 [com.tieba.miuix.data.HistoryStore] 记的，
 * 所以未登录时下面还有一块本地历史可选。
 */
@Composable
fun ProfilePage(
    bottomPadding: Dp,
    onOpenForum: (name: String) -> Unit,
    onOpenThread: (tid: Long, title: String, forumName: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSearchHistory: () -> Unit,
    onOpenFavorites: () -> Unit,
    /** 打开当前账号的用户页（「我的帖子与回复」）。 */
    onOpenSelf: (uid: Long, portrait: String) -> Unit = { _, _ -> },
    /** 打开「我关注的人」。 */
    onOpenFollowList: () -> Unit = {},
) {
    val repository = LocalRepository.current
    val accountStore = LocalAccountStore.current
    val historyStore = LocalHistoryStore.current
    val settings = currentSettings
    val requestLogin = LocalRequestLogin.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()

    var forums by remember { mutableStateOf<List<ConcernedForum>>(emptyList()) }
    var isLoadingForums by remember { mutableStateOf(false) }
    var forumsError by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var isSigning by remember { mutableStateOf(false) }
    var isResolvingSelf by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }

    suspend fun loadForums() {
        if (!accountStore.isLoggedIn) {
            forums = emptyList()
            return
        }
        isLoadingForums = true
        forumsError = ""
        val result = runCatching { repository.getConcernedForums() }.getOrNull()
        when {
            result == null -> forumsError = "关注的吧加载失败，可以下拉重试"
            result.errorCode != 0 -> forumsError = result.errorMessage.ifBlank { "关注的吧加载失败" }
            else -> forums = result.items
        }
        isLoadingForums = false
    }

    LaunchedEffect(accountStore.isLoggedIn, reloadKey) {
        loadForums()
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
                    accountStore.displayName.ifBlank { "已登录" }
                } else {
                    "未登录 · 只能匿名浏览"
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { reloadKey += 1 }) {
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "account") {
                AccountCard(
                    name = accountStore.displayName,
                    isLoggedIn = accountStore.isLoggedIn,
                    hasTbs = accountStore.hasTbs,
                    onLogin = { requestLogin() },
                )
            }

            if (note.isNotBlank()) {
                item(key = "note") { MessageCard(text = note, isError = false) }
            }

            if (accountStore.isLoggedIn && forumsError.isNotBlank()) {
                item(key = "forum-error") { MessageCard(text = forumsError, isError = true) }
            }

            item(key = "forums-title") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle(text = "关注的吧")
                    Spacer(modifier = Modifier.weight(1f))
                    if (accountStore.isLoggedIn && forums.size > 1) {
                        TextButton(
                            text = if (isSigning) "签到中…" else "一键签到",
                            onClick = {
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
                            enabled = !isSigning && accountStore.hasTbs,
                        )
                    }
                }
            }

            if (!accountStore.isLoggedIn) {
                item(key = "forum-login") {
                    MessageCard(
                        text = "登录后可以在这里看到关注的吧，并一键签到。",
                        isError = false,
                    )
                }
                item(key = "forum-login-button") {
                    TextButton(
                        text = "登录贴吧",
                        onClick = { requestLogin() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else if (forums.isEmpty()) {
                item(key = "forum-empty") {
                    MessageCard(
                        text = if (isLoadingForums) "正在加载关注的吧…" else "还没有关注的吧。",
                        isError = false,
                    )
                }
            } else {
                items(
                    count = (forums.size + 2) / 3,
                    key = { row -> "forum-row-" + row },
                ) { row ->
                    val slice = forums.drop(row * 3).take(3)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        slice.forEach { forum ->
                            ForumChip(
                                forum = forum,
                                onClick = { onOpenForum(forum.name) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - slice.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // 最近逛过的吧：本地记录，未登录也有（设置里可关）。
            if (settings.homeShowRecentForum && historyStore.forums.isNotEmpty()) {
                item(key = "recent-title") { SectionTitle(text = "最近逛过的吧") }
                item(key = "recent") {
                    Column {
                        historyStore.forums.take(10).forEach { record ->
                            ActionRow(
                                title = record.name + "吧",
                                summary = formatRelativeTime(record.time),
                                onClick = { onOpenForum(record.name) },
                            )
                        }
                    }
                }
            }

            item(key = "entries-title") { SectionTitle(text = "我的内容") }

            item(key = "entry-favorites") {
                ActionRow(
                    title = "收藏的帖子",
                    summary = "登录后可见",
                    onClick = { onOpenFavorites() },
                )
            }

            item(key = "entry-follow-list") {
                ActionRow(
                    title = "我关注的人",
                    summary = if (accountStore.isLoggedIn) {
                        "看看自己关注了哪些人"
                    } else {
                        "登录后可见"
                    },
                    onClick = {
                        if (accountStore.isLoggedIn) onOpenFollowList() else requestLogin()
                    },
                )
            }

            item(key = "entry-my-posts") {
                ActionRow(
                    title = "我的帖子与回复",
                    summary = when {
                        !accountStore.isLoggedIn -> "登录后可见"
                        isResolvingSelf -> "正在确认账号身份…"
                        else -> "查看自己的主题与回复"
                    },
                    onClick = {
                        if (!accountStore.isLoggedIn) {
                            requestLogin()
                        } else if (!isResolvingSelf) {
                            scope.launch {
                                isResolvingSelf = true
                                // 「我的帖子」需要 uid，而登录流程只拿到 BDUSS/STOKEN；
                                // 用 userpost 的空 uid 查询把身份读出来（见 TiebaRepository.selfUid）。
                                val resolved = runCatching { repository.selfUid() }.getOrNull() ?: 0L
                                val portrait = runCatching {
                                    repository.getSelfPosts()
                                }.getOrNull()?.firstPortrait.orEmpty()
                                isResolvingSelf = false
                                if (resolved > 0L) {
                                    onOpenSelf(resolved, portrait)
                                } else {
                                    note = "没能确认账号 ID，可能是登录已过期，试试重新登录"
                                }
                            }
                        }
                    },
                )
            }

            item(key = "entry-history") {
                ActionRow(
                    title = "浏览历史",
                    summary = historyStore.threads.size.toString() + " 条",
                    onClick = onOpenHistory,
                )
            }

            item(key = "entry-search-history") {
                ActionRow(
                    title = "搜索历史",
                    summary = "管理搜过的关键词",
                    onClick = onOpenSearchHistory,
                )
            }

            item(key = "entry-settings") {
                ActionRow(
                    title = "设置",
                    summary = "外观、浏览、历史",
                    onClick = onOpenSettings,
                )
            }
        }
    }
}

/** 账号卡片：登录状态 + 昵称 + 登录/未登录提示。 */
@Composable
private fun AccountCard(
    name: String,
    isLoggedIn: Boolean,
    hasTbs: Boolean,
    onLogin: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(16.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(MiuixTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isLoggedIn && name.isNotEmpty()) name.take(1) else "贴",
                    style = MiuixTheme.textStyles.title4,
                    color = MiuixTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isLoggedIn) name.ifBlank { "已登录" } else "未登录",
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when {
                        !isLoggedIn -> "匿名浏览中，登录后可关注、签到、发帖"
                        hasTbs -> "可以关注、签到、发帖"
                        else -> "还没取到发帖令牌，可在设置里刷新"
                    },
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!isLoggedIn) {
                Button(onClick = onLogin) {
                    Text(text = "登录", style = MiuixTheme.textStyles.button)
                }
            }
        }
    }
}

/** 关注的吧：一个小方块，点进吧详情。 */
@Composable
private fun ForumChip(
    forum: ConcernedForum,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = onClick,
    ) {
        Text(
            text = forum.name,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = buildString {
                if (forum.level > 0) append("Lv.").append(forum.level)
                if (forum.isSigned) {
                    if (isNotEmpty()) append(" · ")
                    append("已签")
                }
            }.ifEmpty { "未签到" },
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
