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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
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
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.tieba.miuix.data.BlockRule

/**
 * 屏蔽管理页（移植自 TiebaLite 的屏蔽词设置）。
 *
 * 两个 Tab：黑名单（命中即屏蔽）与白名单（命中即放行，优先级更高）。
 * 每条规则可以按关键词（支持正则）或按用户（uid / 用户名）匹配。
 */
@Composable
fun BlockPage(bottomPadding: Dp, onBack: () -> Unit = {}) {
    val blocks = blockStore
    val scrollBehavior = MiuixScrollBehavior()

    var tab by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    var useRegex by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    val category = if (tab == 0) {
        BlockRule.CATEGORY_BLACK_LIST
    } else {
        BlockRule.CATEGORY_WHITE_LIST
    }
    val current = if (tab == 0) blocks.blackList else blocks.whiteList

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "屏蔽管理",
                subtitle = if (current.isEmpty()) "还没有规则" else current.size.toString() + " 条规则",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
                    }
                },
                bottomContent = {
                    TabRow(
                        tabs = listOf("黑名单", "白名单"),
                        selectedTabIndex = tab,
                        onTabSelected = { tab = it },
                    )
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + bottomPadding + 16.dp,
                start = 12.dp,
                end = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "hint") {
                MessageCard(
                    text = if (tab == 0) {
                        "黑名单命中后，列表里整条隐藏，帖子里折叠成一句提示。"
                    } else {
                        "白名单里的内容永远不屏蔽，优先级高于黑名单。"
                    },
                    isError = false,
                )
            }

            item(key = "input") {
                Card(
                    cornerRadius = 14.dp,
                    insideMargin = PaddingValues(12.dp),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    TextField(
                        value = input,
                        onValueChange = { input = it },
                        label = "关键词，或用 uid/用户名 屏蔽某人",
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = { useRegex = !useRegex },
                            modifier = Modifier.weight(1f),
                            colors = if (useRegex) {
                                ButtonDefaults.buttonColors(
                                    color = MiuixTheme.colorScheme.primary,
                                    contentColor = MiuixTheme.colorScheme.onPrimary,
                                )
                            } else {
                                ButtonDefaults.buttonColors()
                            },
                        ) {
                            Text(text = if (useRegex) "正则：开" else "正则：关")
                        }
                        Button(
                            onClick = {
                                val text = input.trim()
                                if (text.isEmpty()) {
                                    note = "先输入关键词"
                                } else {
                                    blocks.addKeyword(
                                        keyword = text,
                                        category = category,
                                        isRegex = useRegex,
                                    )
                                    input = ""
                                    note = "已添加：" + text
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                color = MiuixTheme.colorScheme.primary,
                                contentColor = MiuixTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text(text = "添加")
                        }
                    }
                }
            }

            if (note.isNotBlank()) {
                item(key = "note") {
                    MessageCard(text = note, isError = false)
                }
            }

            if (current.isEmpty()) {
                item(key = "empty") {
                    CenterHint(
                        text = if (tab == 0) {
                            "黑名单为空。长按帖子列表里的帖子也能直接屏蔽作者。"
                        } else {
                            "白名单为空。"
                        },
                    )
                }
            }

            items(items = current, key = { it.id }) { rule ->
                Card(
                    cornerRadius = 14.dp,
                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = rule.displayText,
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onSurfaceContainer,
                            )
                            Text(
                                text = when (rule.type) {
                                    BlockRule.TYPE_USER -> "按用户匹配"
                                    else -> if (rule.isRegex) "按正则匹配正文" else "按关键词匹配正文"
                                },
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        IconButton(onClick = { blocks.remove(rule.id) }) {
                            Icon(
                                imageVector = MiuixIcons.Delete,
                                contentDescription = "删除",
                            )
                        }
                    }
                }
            }

            if (current.isNotEmpty()) {
                item(key = "clear") {
                    Button(
                        onClick = {
                            current.forEach { blocks.remove(it.id) }
                            note = if (tab == 0) "黑名单已清空" else "白名单已清空"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            contentColor = MiuixTheme.colorScheme.error,
                        ),
                    ) {
                        Text(
                            text = if (tab == 0) "清空黑名单" else "清空白名单",
                            color = MiuixTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
