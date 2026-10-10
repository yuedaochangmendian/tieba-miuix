// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar

/** 设置页。 */
@Composable
fun SettingsPage(bottomPadding: androidx.compose.ui.unit.Dp) {
    val settingsStore = LocalSettingsStore.current
    val settings = settingsStore.settings
    val historyStore = LocalHistoryStore.current
    val searchHistoryStore = LocalSearchHistoryStore.current
    val scrollBehavior = MiuixScrollBehavior()

    // 「清空」是破坏性操作，清完给一句提示，让用户知道确实生效了。
    var clearedNote by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(title = "设置", scrollBehavior = scrollBehavior)
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 4.dp,
                bottom = innerPadding.calculateBottomPadding() + bottomPadding + 16.dp,
            ),
        ) {
            item(key = "appearance-title") { SectionTitle("外观") }
            item(key = "appearance") {
                SettingsGroup {
                    ChoiceRow(
                        title = "深色模式",
                        options = DARK_MODE_OPTIONS,
                        selected = settings.darkMode,
                        onSelected = { settingsStore.setDarkMode(it) },
                    )
                    ChoiceRow(
                        title = "字号",
                        options = FONT_SIZE_OPTIONS.mapIndexed { index, label -> label to index },
                        selected = settings.fontSizeLevel,
                        onSelected = { settingsStore.setFontSizeLevel(it) },
                    )
                    SwitchRow(
                        title = "深色模式下降低图片亮度",
                        summary = "夜间浏览时图片不会刺眼",
                        checked = settings.imageDarkenInDarkTheme,
                        onCheckedChange = { settingsStore.setImageDarkenInDarkTheme(it) },
                    )
                }
            }

            item(key = "browse-title") { SectionTitle("浏览") }
            item(key = "browse") {
                SettingsGroup {
                    ChoiceRow(
                        title = "图片加载",
                        options = IMAGE_LOAD_OPTIONS,
                        selected = settings.imageLoadMode,
                        onSelected = { settingsStore.setImageLoadMode(it) },
                    )
                    ChoiceRow(
                        title = "默认排序",
                        options = THREAD_SORT_OPTIONS,
                        selected = settings.defaultThreadSort,
                        onSelected = { settingsStore.setDefaultThreadSort(it) },
                    )
                    ChoiceRow(
                        title = "悬浮按钮",
                        options = FAB_OPTIONS,
                        selected = settings.forumFabAction,
                        onSelected = { settingsStore.setForumFabAction(it) },
                    )
                    SwitchRow(
                        title = "同时显示用户名与昵称",
                        summary = "关闭后只显示昵称",
                        checked = settings.showBothNameAndNickname,
                        onCheckedChange = { settingsStore.setShowBothNameAndNickname(it) },
                    )
                }
            }

            item(key = "history-title") { SectionTitle("历史") }
            item(key = "history") {
                SettingsGroup {
                    SwitchRow(
                        title = "记录浏览历史",
                        summary = "关闭后不再写入新的浏览记录",
                        checked = settings.recordHistory,
                        onCheckedChange = { settingsStore.setRecordHistory(it) },
                    )
                    SwitchRow(
                        title = "首页显示最近逛过的吧",
                        summary = "在首页顶部显示最近进入过的吧",
                        checked = settings.homeShowRecentForum,
                        onCheckedChange = { settingsStore.setHomeShowRecentForum(it) },
                    )
                    ActionRow(
                        title = "清空浏览历史",
                        summary = historyStore.threads.size.toString() + " 条帖子 · " +
                            historyStore.forums.size.toString() + " 个吧",
                        onClick = {
                            historyStore.clearAll()
                            clearedNote = "浏览历史已清空"
                        },
                    )
                    ActionRow(
                        title = "清空搜索历史",
                        summary = searchHistoryStore.keywords.size.toString() + " 条",
                        onClick = {
                            searchHistoryStore.clear()
                            clearedNote = "搜索历史已清空"
                        },
                    )
                }
            }

            item(key = "about-title") { SectionTitle("关于") }
            item(key = "about") {
                SettingsGroup {
                    ActionRow(
                        title = "贴吧 Miuix",
                        summary = "基于 miuix 设计语言的第三方贴吧客户端",
                        onClick = {},
                    )
                    ActionRow(
                        title = "协议参考",
                        summary = "aiotieba / TiebaLite",
                        onClick = {},
                    )
                }
            }

            if (clearedNote.isNotEmpty()) {
                item(key = "note") {
                    MessageCard(text = clearedNote, isError = false)
                }
            }
        }
    }
}
