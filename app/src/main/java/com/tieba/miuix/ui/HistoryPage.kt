// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tieba.miuix.data.ForumHistory
import com.tieba.miuix.data.ThreadHistory
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAB_THREADS = 0
private const val TAB_FORUMS = 1

/**
 * 浏览历史页。
 *
 * 分「帖子」「吧」两个 Tab，与 TiebaLite 的浏览记录一致（它还存在 Room 里并按
 * 今天 / 更早分组，这里分组粒度更细：今天 / 昨天 / N 天前 / 日期）。
 */
@Composable
fun HistoryPage(
    bottomPadding: Dp,
    onOpenThread: (ThreadHistory) -> Unit,
    onOpenForum: (String) -> Unit,
) {
    val historyStore = LocalHistoryStore.current
    val scrollBehavior = MiuixScrollBehavior()
    var selectedTab by remember { mutableIntStateOf(TAB_THREADS) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "浏览历史",
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(
                        onClick = {
                            if (selectedTab == TAB_THREADS) {
                                historyStore.clearThreads()
                            } else {
                                historyStore.clearForums()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Delete,
                            contentDescription = "清空当前列表",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            TabRow(
                tabs = listOf("帖子", "吧"),
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )

            when (selectedTab) {
                TAB_THREADS -> ThreadHistoryList(
                    records = historyStore.threads,
                    bottomPadding = innerPadding.calculateBottomPadding() + bottomPadding,
                    onOpen = onOpenThread,
                    onRemove = { historyStore.removeThread(it) },
                )

                else -> ForumHistoryList(
                    records = historyStore.forums,
                    bottomPadding = innerPadding.calculateBottomPadding() + bottomPadding,
                    onOpen = onOpenForum,
                )
            }
        }
    }
}

@Composable
private fun ThreadHistoryList(
    records: List<ThreadHistory>,
    bottomPadding: Dp,
    onOpen: (ThreadHistory) -> Unit,
    onRemove: (Long) -> Unit,
) {
    if (records.isEmpty()) {
        CenterHint(text = "还没有浏览记录\n去首页逛逛吧")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(records, key = { it.tid }) { record ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 12.dp,
                insideMargin = PaddingValues(14.dp),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                onClick = { onOpen(record) },
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = record.title.ifEmpty { "（无标题）" },
                            style = MiuixTheme.textStyles.body1,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = buildString {
                                if (record.forumName.isNotEmpty()) append(record.forumName).append(" · ")
                                append(record.authorName.ifEmpty { "匿名" })
                                append(" · ")
                                append(formatCount(record.replyNum))
                                append(" 回复")
                            },
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatRelativeTime(record.time),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ForumHistoryList(
    records: List<ForumHistory>,
    bottomPadding: Dp,
    onOpen: (String) -> Unit,
) {
    if (records.isEmpty()) {
        CenterHint(text = "还没有逛过任何吧")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(records, key = { it.name }) { record ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 12.dp,
                insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                onClick = { onOpen(record.name) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = MiuixIcons.Recent,
                        contentDescription = null,
                        modifier = Modifier.width(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = record.name,
                        modifier = Modifier.weight(1f),
                        style = MiuixTheme.textStyles.body1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatRelativeTime(record.time),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}
