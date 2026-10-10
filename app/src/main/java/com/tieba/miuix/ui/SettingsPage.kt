// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 设置页。 */
@Composable
fun SettingsPage(bottomPadding: androidx.compose.ui.unit.Dp) {
    val settingsStore = LocalSettingsStore.current
    val settings = settingsStore.settings
    val historyStore = LocalHistoryStore.current
    val searchHistoryStore = LocalSearchHistoryStore.current
    val accountStore = LocalAccountStore.current
    val repository = LocalRepository.current
    val loginNote = LocalLoginNote.current
    val requestLogin = LocalRequestLogin.current
    val scrollBehavior = MiuixScrollBehavior()

    // 「清空」是破坏性操作，清完给一句提示，让用户知道确实生效了。
    var clearedNote by remember { mutableStateOf("") }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var isFetchingTbs by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
            item(key = "account-title") { SectionTitle("账号") }
            item(key = "account") {
                SettingsGroup {
                    if (accountStore.isLoggedIn) {
                        ActionRow(
                            title = accountStore.displayName.ifEmpty { "已登录" },
                            summary = if (accountStore.hasTbs) {
                                "已登录 · 可以发帖与签到"
                            } else {
                                "已登录 · 还未取到发帖令牌（tbs）"
                            },
                            onClick = {},
                        )
                        ActionRow(
                            title = "刷新发帖令牌",
                            summary = if (isFetchingTbs) "正在获取…" else "发帖、回复、签到需要它",
                            onClick = {
                                if (isFetchingTbs) return@ActionRow
                                isFetchingTbs = true
                                clearedNote = ""
                                scope.launch {
                                    val value = runCatching { repository.fetchTbs() }.getOrDefault("")
                                    isFetchingTbs = false
                                    if (value.isNotEmpty()) {
                                        accountStore.saveTbs(value)
                                        clearedNote = "发帖令牌已更新"
                                    } else {
                                        clearedNote = "没取到发帖令牌，登录可能已过期"
                                    }
                                }
                            },
                        )
                        ActionRow(
                            title = "退出登录",
                            summary = "清除本机保存的登录凭证",
                            onClick = { showLogoutDialog = true },
                        )
                    } else {
                        ActionRow(
                            title = "登录贴吧",
                            summary = "登录后可关注贴吧、收发消息与签到",
                            onClick = { requestLogin() },
                        )
                        ActionRow(
                            title = "当前状态",
                            summary = "未登录 · 只能匿名浏览",
                            onClick = {},
                        )
                    }
                }
            }

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
                        title = "帖子列表",
                        options = THREAD_LIST_MODE_OPTIONS,
                        selected = settings.threadListMode,
                        onSelected = { settingsStore.setThreadListMode(it) },
                    )
                    ChoiceRow(
                        title = "悬浮按钮",
                        options = FAB_OPTIONS,
                        selected = settings.forumFabAction,
                        onSelected = { settingsStore.setForumFabAction(it) },
                    )
                    SwitchRow(
                        title = "同时显示用户名与昵称",
                        summary = "关闭后只显示昵称；页面里同时带用户名与昵称的才生效",
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
            if (loginNote.isNotEmpty()) {
                item(key = "login-note") {
                    MessageCard(text = loginNote, isError = false)
                }
            }
        }
    }

    OverlayDialog(
        show = showLogoutDialog,
        title = "退出登录",
        summary = "将清除本机保存的 BDUSS 等凭证，浏览历史不受影响。",
        onDismissRequest = { showLogoutDialog = false },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextButton(
                text = "取消",
                onClick = { showLogoutDialog = false },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = "退出",
                onClick = {
                    accountStore.clear()
                    showLogoutDialog = false
                    clearedNote = "已退出登录"
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColors(
                    color = MiuixTheme.colorScheme.error,
                ),
            )
        }
    }
}
